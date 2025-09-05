package com.yuwang.leyuegateway.filter;

import com.yuwang.leyuegateway.constant.TracingHeaders;
import com.yuwang.leyuegateway.util.TraceIdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 追踪全局过滤器
 * 实现W3C Trace Context协议，支持多协议兼容
 * 
 * 功能：
 * 1. 从上游提取或生成traceId
 * 2. 统一转换为W3C格式向下游传递
 * 3. 同时保持向下兼容性
 * 4. 记录追踪日志和监控指标
 *
 * @author Claude
 * @since 2025-01-04
 */
@Component
public class TracingGlobalFilter implements GlobalFilter, Ordered {
    
    private static final Logger logger = LoggerFactory.getLogger(TracingGlobalFilter.class);
    
    // 网关span名称
    private static final String GATEWAY_SPAN_NAME = "gateway";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        
        // 提取或生成追踪信息
        TraceIdUtils.TraceInfo traceInfo = TraceIdUtils.extractOrGenerateTraceInfo(request);
        
        // 设置MDC用于当前请求的日志
        MDC.put("traceId", traceInfo.getTraceId());
        MDC.put("spanId", traceInfo.getSpanId());
        MDC.put("traceSource", traceInfo.getSource());
        
        logger.info("网关处理请求开始: method={}, path={}, traceId={}, source={}", 
                method, path, traceInfo.getTraceId(), traceInfo.getSource());
        
        // 构建修改后的请求，添加追踪头
        ServerHttpRequest mutatedRequest = buildRequestWithTracingHeaders(request, traceInfo);
        
        // 记录请求开始时间用于计算延迟
        long startTime = System.currentTimeMillis();
        
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doOnSuccess(aVoid -> {
                    // 请求成功完成
                    long duration = System.currentTimeMillis() - startTime;
                    logger.info("网关处理请求完成: method={}, path={}, traceId={}, duration={}ms, status=success", 
                            method, path, traceInfo.getTraceId(), duration);
                })
                .doOnError(throwable -> {
                    // 请求处理出错
                    long duration = System.currentTimeMillis() - startTime;
                    logger.error("网关处理请求失败: method={}, path={}, traceId={}, duration={}ms, error={}", 
                            method, path, traceInfo.getTraceId(), duration, throwable.getMessage());
                })
                .doFinally(signalType -> {
                    // 清理MDC，防止内存泄漏
                    MDC.clear();
                    
                    // 记录最终的信号类型
                    logger.debug("请求处理完成: traceId={}, signalType={}", traceInfo.getTraceId(), signalType);
                });
    }
    
    /**
     * 构建包含追踪头的请求
     */
    private ServerHttpRequest buildRequestWithTracingHeaders(ServerHttpRequest request, TraceIdUtils.TraceInfo traceInfo) {
        return request.mutate()
                // W3C Trace Context 标准头（主要）
                .header(TracingHeaders.W3C_TRACEPARENT, traceInfo.toW3CTraceparent())
                
                // 向下兼容的自定义头
                .header(TracingHeaders.X_TRACE_ID, traceInfo.getTraceId())
                .header(TracingHeaders.X_SPAN_ID, traceInfo.getSpanId())
                .header(TracingHeaders.TRACE_ID, traceInfo.getTraceId())  // 传统格式兼容
                
                // 网关元数据
                .header(TracingHeaders.X_TRACE_SOURCE, traceInfo.getSource())
                .header(TracingHeaders.X_REQUEST_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                
                // 如果原始请求有tracestate，保持传递（W3C标准要求）
                .headers(httpHeaders -> {
                    String tracestate = request.getHeaders().getFirst(TracingHeaders.W3C_TRACESTATE);
                    if (tracestate != null) {
                        httpHeaders.add(TracingHeaders.W3C_TRACESTATE, tracestate);
                    }
                })
                
                .build();
    }
    
    @Override
    public int getOrder() {
        // 设置为-200，确保在认证过滤器（-100）之前执行
        // 这样追踪信息在所有后续过滤器中都可用
        return -200;
    }
}
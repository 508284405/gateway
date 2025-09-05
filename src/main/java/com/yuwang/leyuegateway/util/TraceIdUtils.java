package com.yuwang.leyuegateway.util;

import com.yuwang.leyuegateway.constant.TracingHeaders;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * TraceId工具类
 * 负责traceId的生成、提取、格式化等操作
 *
 * @author Claude
 * @since 2025-01-04
 */
public final class TraceIdUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(TraceIdUtils.class);
    
    private static final SecureRandom random = new SecureRandom();
    
    // W3C Trace Context 常量
    private static final String W3C_VERSION = "00";
    private static final String W3C_FLAGS_SAMPLED = "01";
    private static final String W3C_FLAGS_NOT_SAMPLED = "00";
    
    /**
     * 追踪信息封装类
     */
    public static class TraceInfo {
        private final String traceId;
        private final String spanId;
        private final String source;
        private final boolean sampled;
        
        public TraceInfo(String traceId, String spanId, String source, boolean sampled) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.source = source;
            this.sampled = sampled;
        }
        
        public String getTraceId() { return traceId; }
        public String getSpanId() { return spanId; }
        public String getSource() { return source; }
        public boolean isSampled() { return sampled; }
        
        /**
         * 生成W3C traceparent格式
         */
        public String toW3CTraceparent() {
            String normalizedTraceId = normalizeTraceId(traceId);
            String normalizedSpanId = normalizeSpanId(spanId);
            String flags = sampled ? W3C_FLAGS_SAMPLED : W3C_FLAGS_NOT_SAMPLED;
            return String.format("%s-%s-%s-%s", W3C_VERSION, normalizedTraceId, normalizedSpanId, flags);
        }
    }
    
    /**
     * 从HTTP请求中提取或生成追踪信息
     * 支持多种协议的自动检测和转换
     * 
     * @param request HTTP请求
     * @return 追踪信息
     */
    public static TraceInfo extractOrGenerateTraceInfo(ServerHttpRequest request) {
        // 1. 尝试从W3C traceparent提取（最高优先级）
        TraceInfo w3cTrace = extractFromW3CTraceparent(request);
        if (w3cTrace != null) {
            logger.debug("从W3C traceparent提取追踪信息: traceId={}", w3cTrace.getTraceId());
            return w3cTrace;
        }
        
        // 2. 尝试从B3协议提取
        TraceInfo b3Trace = extractFromB3Headers(request);
        if (b3Trace != null) {
            logger.debug("从B3协议提取追踪信息: traceId={}", b3Trace.getTraceId());
            return b3Trace;
        }
        
        // 3. 尝试从自定义头提取
        TraceInfo customTrace = extractFromCustomHeaders(request);
        if (customTrace != null) {
            logger.debug("从自定义头提取追踪信息: traceId={}", customTrace.getTraceId());
            return customTrace;
        }
        
        // 4. 生成新的根追踪信息
        TraceInfo newTrace = generateRootTrace();
        logger.debug("生成新的根追踪信息: traceId={}", newTrace.getTraceId());
        return newTrace;
    }
    
    /**
     * 从W3C traceparent头提取追踪信息
     */
    private static TraceInfo extractFromW3CTraceparent(ServerHttpRequest request) {
        String traceparent = request.getHeaders().getFirst(TracingHeaders.W3C_TRACEPARENT);
        if (StringUtils.isBlank(traceparent)) {
            return null;
        }
        
        String[] parts = traceparent.split("-");
        if (parts.length != 4) {
            logger.warn("无效的W3C traceparent格式: {}", traceparent);
            return null;
        }
        
        String version = parts[0];
        String traceId = parts[1];
        String parentSpanId = parts[2];
        String flags = parts[3];
        
        // 验证格式
        if (!"00".equals(version)) {
            logger.warn("不支持的W3C traceparent版本: {}", version);
            return null;
        }
        
        // 验证traceId和spanId长度，并进行标准化
        if (traceId.length() != 32) {
            traceId = normalizeTraceId(traceId);
        }
        if (parentSpanId.length() != 16) {
            parentSpanId = normalizeSpanId(parentSpanId);
        }
        
        // 再次验证
        if (traceId.length() != 32 || parentSpanId.length() != 16) {
            logger.warn("W3C traceparent格式错误: traceId长度={}, spanId长度={}", traceId.length(), parentSpanId.length());
            return null;
        }
        
        boolean sampled = "01".equals(flags);
        String newSpanId = generateSpanId(); // 为当前服务生成新的spanId
        
        return new TraceInfo(traceId, newSpanId, "w3c-traceparent", sampled);
    }
    
    /**
     * 从B3协议头提取追踪信息
     */
    private static TraceInfo extractFromB3Headers(ServerHttpRequest request) {
        // 尝试B3 Single Header
        String b3Single = request.getHeaders().getFirst(TracingHeaders.B3_SINGLE);
        if (StringUtils.isNotBlank(b3Single)) {
            return parseB3Single(b3Single);
        }
        
        // 尝试B3 多头格式
        String traceId = request.getHeaders().getFirst(TracingHeaders.B3_TRACE_ID);
        String spanId = request.getHeaders().getFirst(TracingHeaders.B3_SPAN_ID);
        
        if (StringUtils.isNotBlank(traceId)) {
            String newSpanId = generateSpanId();
            return new TraceInfo(traceId, newSpanId, "b3-headers", true);
        }
        
        return null;
    }
    
    /**
     * 解析B3 Single Header格式
     */
    private static TraceInfo parseB3Single(String b3Single) {
        String[] parts = b3Single.split("-");
        if (parts.length >= 2) {
            String traceId = parts[0];
            String newSpanId = generateSpanId();
            boolean sampled = parts.length > 2 && "1".equals(parts[2]);
            return new TraceInfo(traceId, newSpanId, "b3-single", sampled);
        }
        return null;
    }
    
    /**
     * 从自定义头提取追踪信息
     */
    private static TraceInfo extractFromCustomHeaders(ServerHttpRequest request) {
        // 尝试X-Trace-Id
        String customTraceId = request.getHeaders().getFirst(TracingHeaders.X_TRACE_ID);
        if (StringUtils.isNotBlank(customTraceId)) {
            String spanId = generateSpanId();
            return new TraceInfo(customTraceId, spanId, "custom-x-trace-id", true);
        }
        
        // 尝试传统traceId头
        String legacyTraceId = request.getHeaders().getFirst(TracingHeaders.TRACE_ID);
        if (StringUtils.isNotBlank(legacyTraceId)) {
            String spanId = generateSpanId();
            return new TraceInfo(legacyTraceId, spanId, "legacy-trace-id", true);
        }
        
        return null;
    }
    
    /**
     * 生成根追踪信息（新的追踪开始）
     */
    private static TraceInfo generateRootTrace() {
        String traceId = generateTraceId();
        String spanId = generateSpanId();
        return new TraceInfo(traceId, spanId, "gateway-generated", true);
    }
    
    /**
     * 生成32位十六进制的traceId
     */
    public static String generateTraceId() {
        // 使用时间戳+随机数生成更好的分布
        long timestamp = System.currentTimeMillis();
        long randomPart = random.nextLong();
        
        return String.format("%016x%016x", timestamp, randomPart);
    }
    
    /**
     * 生成16位十六进制的spanId
     */
    public static String generateSpanId() {
        return String.format("%016x", random.nextLong() & Long.MAX_VALUE);
    }
    
    /**
     * 标准化traceId为32位十六进制格式
     */
    public static String normalizeTraceId(String traceId) {
        if (StringUtils.isBlank(traceId)) {
            return generateTraceId();
        }
        
        // 移除非十六进制字符
        String cleanTraceId = traceId.replaceAll("[^0-9a-fA-F]", "").toLowerCase();
        
        if (cleanTraceId.length() >= 32) {
            return cleanTraceId.substring(0, 32);
        } else if (cleanTraceId.length() >= 16) {
            // 使用零填充
            return String.format("%32s", cleanTraceId).replace(' ', '0');
        } else {
            // 太短，使用hash + 原值
            String hash = Integer.toHexString(traceId.hashCode());
            String combined = cleanTraceId + hash;
            return String.format("%32s", combined.substring(0, Math.min(combined.length(), 32))).replace(' ', '0');
        }
    }
    
    /**
     * 标准化spanId为16位十六进制格式
     */
    public static String normalizeSpanId(String spanId) {
        if (StringUtils.isBlank(spanId)) {
            return generateSpanId();
        }
        
        // 移除非十六进制字符
        String cleanSpanId = spanId.replaceAll("[^0-9a-fA-F]", "").toLowerCase();
        
        if (cleanSpanId.length() >= 16) {
            return cleanSpanId.substring(0, 16);
        } else {
            return String.format("%16s", cleanSpanId).replace(' ', '0');
        }
    }
    
    /**
     * 验证traceId格式是否符合W3C标准
     */
    public static boolean isValidW3CTraceId(String traceId) {
        return StringUtils.isNotBlank(traceId) 
            && traceId.length() == 32 
            && traceId.matches("^[0-9a-f]+$")
            && !"00000000000000000000000000000000".equals(traceId);
    }
    
    /**
     * 验证spanId格式是否符合W3C标准
     */
    public static boolean isValidW3CSpanId(String spanId) {
        return StringUtils.isNotBlank(spanId) 
            && spanId.length() == 16 
            && spanId.matches("^[0-9a-f]+$")
            && !"0000000000000000".equals(spanId);
    }
    
    // 私有构造函数防止实例化
    private TraceIdUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
package com.yuwang.leyuegateway.constant;

/**
 * 追踪相关的HTTP头常量
 *
 * @author Claude
 * @since 2025-01-04
 */
public final class TracingHeaders {
    
    // W3C Trace Context 标准头
    /**
     * W3C Trace Context标准的traceparent头
     * 格式：{version}-{trace-id}-{parent-id}-{trace-flags}
     * 例如：00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
     */
    public static final String W3C_TRACEPARENT = "traceparent";
    
    /**
     * W3C Trace Context标准的tracestate头
     * 用于传递供应商特定的追踪信息
     */
    public static final String W3C_TRACESTATE = "tracestate";
    
    // 自定义追踪头（向下兼容）
    /**
     * 自定义traceId头，用于向下兼容
     */
    public static final String X_TRACE_ID = "X-Trace-Id";
    
    /**
     * 自定义spanId头，用于向下兼容
     */
    public static final String X_SPAN_ID = "X-Span-Id";
    
    /**
     * 传统traceId头（最低优先级）
     */
    public static final String TRACE_ID = "traceId";
    
    // 其他常见追踪协议头（支持多协议兼容）
    /**
     * B3 Single Header格式
     * 格式：{traceId}-{spanId}-{sampled}-{parentSpanId}
     */
    public static final String B3_SINGLE = "b3";
    
    /**
     * B3 多头格式 - traceId
     */
    public static final String B3_TRACE_ID = "X-B3-TraceId";
    
    /**
     * B3 多头格式 - spanId
     */
    public static final String B3_SPAN_ID = "X-B3-SpanId";
    
    /**
     * SkyWalking追踪头
     */
    public static final String SW8 = "sw8";
    
    // 网关内部使用的元数据头
    /**
     * 网关添加的traceId来源标识
     */
    public static final String X_TRACE_SOURCE = "X-Trace-Source";
    
    /**
     * 网关添加的请求时间戳
     */
    public static final String X_REQUEST_TIMESTAMP = "X-Request-Timestamp";
    
    // 私有构造函数防止实例化
    private TracingHeaders() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
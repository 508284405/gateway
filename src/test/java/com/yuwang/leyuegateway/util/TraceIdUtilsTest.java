package com.yuwang.leyuegateway.util;

import com.yuwang.leyuegateway.constant.TracingHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TraceIdUtils 测试类
 *
 * @author Claude
 * @since 2025-01-04
 */
class TraceIdUtilsTest {

    @Test
    void testGenerateTraceId() {
        String traceId = TraceIdUtils.generateTraceId();
        
        assertNotNull(traceId);
        assertEquals(32, traceId.length());
        assertTrue(traceId.matches("^[0-9a-f]+$"));
        
        // 生成两次应该不同
        String traceId2 = TraceIdUtils.generateTraceId();
        assertNotEquals(traceId, traceId2);
    }

    @Test
    void testGenerateSpanId() {
        String spanId = TraceIdUtils.generateSpanId();
        
        assertNotNull(spanId);
        assertEquals(16, spanId.length());
        assertTrue(spanId.matches("^[0-9a-f]+$"));
        
        // 生成两次应该不同
        String spanId2 = TraceIdUtils.generateSpanId();
        assertNotEquals(spanId, spanId2);
    }

    @Test
    void testNormalizeTraceId() {
        // 测试正常的32位traceId
        String normalTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        assertEquals(normalTraceId, TraceIdUtils.normalizeTraceId(normalTraceId));
        
        // 测试大写转小写
        assertEquals(normalTraceId, TraceIdUtils.normalizeTraceId(normalTraceId.toUpperCase()));
        
        // 测试含有非法字符
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", 
                TraceIdUtils.normalizeTraceId("4bf92f35-77b3-4da6-a3ce-929d0e0e4736"));
        
        // 测试短ID补零
        String shortId = "123456789abcdef0";
        String normalized = TraceIdUtils.normalizeTraceId(shortId);
        assertEquals(32, normalized.length());
        assertTrue(normalized.endsWith(shortId));
        
        // 测试空值
        String generated = TraceIdUtils.normalizeTraceId(null);
        assertNotNull(generated);
        assertEquals(32, generated.length());
    }

    @Test
    void testNormalizeSpanId() {
        // 测试正常的16位spanId
        String normalSpanId = "00f067aa0ba902b7";
        assertEquals(normalSpanId, TraceIdUtils.normalizeSpanId(normalSpanId));
        
        // 测试大写转小写
        assertEquals(normalSpanId, TraceIdUtils.normalizeSpanId(normalSpanId.toUpperCase()));
        
        // 测试短ID补零
        String shortId = "12345678";
        String normalized = TraceIdUtils.normalizeSpanId(shortId);
        assertEquals(16, normalized.length());
        assertTrue(normalized.endsWith(shortId));
        assertEquals("0000000012345678", normalized);
        
        // 测试空值
        String generated = TraceIdUtils.normalizeSpanId(null);
        assertNotNull(generated);
        assertEquals(16, generated.length());
    }

    @Test
    void testIsValidW3CTraceId() {
        // 有效的traceId
        assertTrue(TraceIdUtils.isValidW3CTraceId("4bf92f3577b34da6a3ce929d0e0e4736"));
        assertTrue(TraceIdUtils.isValidW3CTraceId("abcdef1234567890abcdef1234567890"));
        
        // 无效的traceId
        assertFalse(TraceIdUtils.isValidW3CTraceId(null));
        assertFalse(TraceIdUtils.isValidW3CTraceId(""));
        assertFalse(TraceIdUtils.isValidW3CTraceId("too-short"));
        assertFalse(TraceIdUtils.isValidW3CTraceId("4bf92f3577b34da6a3ce929d0e0e4736extra")); // 太长
        assertFalse(TraceIdUtils.isValidW3CTraceId("4bf92f3577b34da6a3ce929d0e0e473g")); // 包含非法字符
        assertFalse(TraceIdUtils.isValidW3CTraceId("00000000000000000000000000000000")); // 全零
    }

    @Test
    void testIsValidW3CSpanId() {
        // 有效的spanId
        assertTrue(TraceIdUtils.isValidW3CSpanId("00f067aa0ba902b7"));
        assertTrue(TraceIdUtils.isValidW3CSpanId("abcdef1234567890"));
        
        // 无效的spanId
        assertFalse(TraceIdUtils.isValidW3CSpanId(null));
        assertFalse(TraceIdUtils.isValidW3CSpanId(""));
        assertFalse(TraceIdUtils.isValidW3CSpanId("too-short"));
        assertFalse(TraceIdUtils.isValidW3CSpanId("00f067aa0ba902b7extra")); // 太长
        assertFalse(TraceIdUtils.isValidW3CSpanId("00f067aa0ba902bg")); // 包含非法字符
        assertFalse(TraceIdUtils.isValidW3CSpanId("0000000000000000")); // 全零
    }

    @Test
    void testExtractFromW3CTraceparent() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/test")
                .header(TracingHeaders.W3C_TRACEPARENT, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                .build();

        TraceIdUtils.TraceInfo traceInfo = TraceIdUtils.extractOrGenerateTraceInfo(request);
        
        assertNotNull(traceInfo);
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", traceInfo.getTraceId());
        assertEquals("w3c-traceparent", traceInfo.getSource());
        assertTrue(traceInfo.isSampled());
        
        // 验证生成新的spanId
        assertNotEquals("00f067aa0ba902b7", traceInfo.getSpanId());
        assertEquals(16, traceInfo.getSpanId().length());
        
        // 验证W3C traceparent生成
        String traceparent = traceInfo.toW3CTraceparent();
        assertTrue(traceparent.startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-"));
        assertTrue(traceparent.endsWith("-01"));
    }

    @Test
    void testExtractFromB3Single() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/test")
                .header(TracingHeaders.B3_SINGLE, "4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-1")
                .build();

        TraceIdUtils.TraceInfo traceInfo = TraceIdUtils.extractOrGenerateTraceInfo(request);
        
        assertNotNull(traceInfo);
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", traceInfo.getTraceId());
        assertEquals("b3-single", traceInfo.getSource());
        assertTrue(traceInfo.isSampled());
    }

    @Test
    void testExtractFromB3Headers() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/test")
                .header(TracingHeaders.B3_TRACE_ID, "4bf92f3577b34da6a3ce929d0e0e4736")
                .header(TracingHeaders.B3_SPAN_ID, "00f067aa0ba902b7")
                .build();

        TraceIdUtils.TraceInfo traceInfo = TraceIdUtils.extractOrGenerateTraceInfo(request);
        
        assertNotNull(traceInfo);
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", traceInfo.getTraceId());
        assertEquals("b3-headers", traceInfo.getSource());
        assertTrue(traceInfo.isSampled());
    }

    @Test
    void testExtractFromCustomHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/test")
                .header(TracingHeaders.X_TRACE_ID, "custom-trace-12345")
                .build();

        TraceIdUtils.TraceInfo traceInfo = TraceIdUtils.extractOrGenerateTraceInfo(request);
        
        assertNotNull(traceInfo);
        assertEquals("custom-trace-12345", traceInfo.getTraceId());
        assertEquals("custom-x-trace-id", traceInfo.getSource());
        assertTrue(traceInfo.isSampled());
    }

    @Test
    void testExtractFromLegacyHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/test")
                .header(TracingHeaders.TRACE_ID, "legacy-trace-id")
                .build();

        TraceIdUtils.TraceInfo traceInfo = TraceIdUtils.extractOrGenerateTraceInfo(request);
        
        assertNotNull(traceInfo);
        assertEquals("legacy-trace-id", traceInfo.getTraceId());
        assertEquals("legacy-trace-id", traceInfo.getSource());
        assertTrue(traceInfo.isSampled());
    }

    @Test
    void testGenerateRootTrace() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/test")
                .build(); // 没有任何追踪头

        TraceIdUtils.TraceInfo traceInfo = TraceIdUtils.extractOrGenerateTraceInfo(request);
        
        assertNotNull(traceInfo);
        assertNotNull(traceInfo.getTraceId());
        assertNotNull(traceInfo.getSpanId());
        assertEquals("gateway-generated", traceInfo.getSource());
        assertTrue(traceInfo.isSampled());
        
        // 验证生成的ID格式
        assertEquals(32, TraceIdUtils.normalizeTraceId(traceInfo.getTraceId()).length());
        assertEquals(16, TraceIdUtils.normalizeSpanId(traceInfo.getSpanId()).length());
    }

    @Test
    void testPriorityOrder() {
        // 测试多种头同时存在时的优先级：W3C > B3 > Custom
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/test")
                .header(TracingHeaders.W3C_TRACEPARENT, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                .header(TracingHeaders.B3_TRACE_ID, "b3traceid1111111111111111111111111111")
                .header(TracingHeaders.X_TRACE_ID, "custom-trace-id")
                .build();

        TraceIdUtils.TraceInfo traceInfo = TraceIdUtils.extractOrGenerateTraceInfo(request);
        
        // W3C优先级最高
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", traceInfo.getTraceId());
        assertEquals("w3c-traceparent", traceInfo.getSource());
    }

    @Test
    void testInvalidW3CTraceparent() {
        // 测试无效的W3C traceparent格式
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/test")
                .header(TracingHeaders.W3C_TRACEPARENT, "invalid-format")
                .header(TracingHeaders.B3_TRACE_ID, "b3-fallback-trace-id")
                .build();

        TraceIdUtils.TraceInfo traceInfo = TraceIdUtils.extractOrGenerateTraceInfo(request);
        
        // 应该回退到B3格式
        assertEquals("b3-fallback-trace-id", traceInfo.getTraceId());
        assertEquals("b3-headers", traceInfo.getSource());
    }

    @Test
    void testTraceInfoToW3CTraceparent() {
        TraceIdUtils.TraceInfo traceInfo = new TraceIdUtils.TraceInfo(
            "4bf92f3577b34da6a3ce929d0e0e4736", 
            "00f067aa0ba902b7", 
            "test", 
            true
        );
        
        String traceparent = traceInfo.toW3CTraceparent();
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", traceparent);
        
        // 测试未采样的情况
        TraceIdUtils.TraceInfo notSampledTrace = new TraceIdUtils.TraceInfo(
            "4bf92f3577b34da6a3ce929d0e0e4736", 
            "00f067aa0ba902b7", 
            "test", 
            false
        );
        
        String notSampledTraceparent = notSampledTrace.toW3CTraceparent();
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00", notSampledTraceparent);
    }
}
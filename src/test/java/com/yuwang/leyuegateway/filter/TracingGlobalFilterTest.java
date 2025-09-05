package com.yuwang.leyuegateway.filter;

import com.yuwang.leyuegateway.constant.TracingHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TracingGlobalFilter 测试类
 * 
 * @author Claude
 * @since 2025-01-04
 */
class TracingGlobalFilterTest {

    private TracingGlobalFilter tracingGlobalFilter;
    private AtomicReference<ServerWebExchange> capturedExchange;

    @BeforeEach
    void setUp() {
        tracingGlobalFilter = new TracingGlobalFilter();
        capturedExchange = new AtomicReference<>();
    }

    @Test
    void testFilterOrder() {
        assertEquals(-200, tracingGlobalFilter.getOrder());
    }

    @Test
    void testGenerateNewTraceWhenNoHeaders() {
        // 创建不包含任何追踪头的请求
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/api/test")
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        GatewayFilterChain chain = createMockChain();
        
        StepVerifier.create(tracingGlobalFilter.filter(exchange, chain))
                .verifyComplete();
        
        // 验证生成的追踪头
        ServerWebExchange modifiedExchange = capturedExchange.get();
        assertNotNull(modifiedExchange);
        
        HttpHeaders headers = modifiedExchange.getRequest().getHeaders();
        
        // 验证W3C traceparent头
        String traceparent = headers.getFirst(TracingHeaders.W3C_TRACEPARENT);
        assertNotNull(traceparent);
        assertTrue(traceparent.matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$"));
        
        // 验证兼容头
        assertNotNull(headers.getFirst(TracingHeaders.X_TRACE_ID));
        assertNotNull(headers.getFirst(TracingHeaders.X_SPAN_ID));
        assertNotNull(headers.getFirst(TracingHeaders.TRACE_ID));
        
        // 验证元数据头
        assertEquals("gateway-generated", headers.getFirst(TracingHeaders.X_TRACE_SOURCE));
        assertNotNull(headers.getFirst(TracingHeaders.X_REQUEST_TIMESTAMP));
    }

    @Test
    void testExtractFromW3CTraceparent() {
        String originalTraceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/api/test")
                .header(TracingHeaders.W3C_TRACEPARENT, originalTraceparent)
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = createMockChain();
        
        StepVerifier.create(tracingGlobalFilter.filter(exchange, chain))
                .verifyComplete();
        
        ServerWebExchange modifiedExchange = capturedExchange.get();
        HttpHeaders headers = modifiedExchange.getRequest().getHeaders();
        
        // 验证traceId保持不变，但生成了新的spanId
        String newTraceparent = headers.getFirst(TracingHeaders.W3C_TRACEPARENT);
        assertNotNull(newTraceparent);
        assertTrue(newTraceparent.startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-"));
        assertFalse(newTraceparent.contains("00f067aa0ba902b7")); // spanId应该不同
        
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", headers.getFirst(TracingHeaders.X_TRACE_ID));
        assertEquals("w3c-traceparent", headers.getFirst(TracingHeaders.X_TRACE_SOURCE));
    }

    @Test
    void testExtractFromCustomXTraceId() {
        String customTraceId = "custom-trace-id-12345";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/api/test")
                .header(TracingHeaders.X_TRACE_ID, customTraceId)
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = createMockChain();
        
        StepVerifier.create(tracingGlobalFilter.filter(exchange, chain))
                .verifyComplete();
        
        ServerWebExchange modifiedExchange = capturedExchange.get();
        HttpHeaders headers = modifiedExchange.getRequest().getHeaders();
        
        // 验证自定义traceId被提取和转换
        assertEquals(customTraceId, headers.getFirst(TracingHeaders.X_TRACE_ID));
        assertEquals("custom-x-trace-id", headers.getFirst(TracingHeaders.X_TRACE_SOURCE));
        
        // 验证生成了W3C格式
        String traceparent = headers.getFirst(TracingHeaders.W3C_TRACEPARENT);
        assertNotNull(traceparent);
        assertTrue(traceparent.matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$"));
    }

    @Test
    void testExtractFromB3Headers() {
        String b3TraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String b3SpanId = "00f067aa0ba902b7";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/api/test")
                .header(TracingHeaders.B3_TRACE_ID, b3TraceId)
                .header(TracingHeaders.B3_SPAN_ID, b3SpanId)
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = createMockChain();
        
        StepVerifier.create(tracingGlobalFilter.filter(exchange, chain))
                .verifyComplete();
        
        ServerWebExchange modifiedExchange = capturedExchange.get();
        HttpHeaders headers = modifiedExchange.getRequest().getHeaders();
        
        // 验证B3 traceId被提取
        assertEquals(b3TraceId, headers.getFirst(TracingHeaders.X_TRACE_ID));
        assertEquals("b3-headers", headers.getFirst(TracingHeaders.X_TRACE_SOURCE));
        
        // 验证转换为W3C格式
        String traceparent = headers.getFirst(TracingHeaders.W3C_TRACEPARENT);
        assertNotNull(traceparent);
        assertTrue(traceparent.startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-"));
    }

    @Test
    void testB3SingleHeaderFormat() {
        String b3Single = "4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-1";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/api/test")
                .header(TracingHeaders.B3_SINGLE, b3Single)
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = createMockChain();
        
        StepVerifier.create(tracingGlobalFilter.filter(exchange, chain))
                .verifyComplete();
        
        ServerWebExchange modifiedExchange = capturedExchange.get();
        HttpHeaders headers = modifiedExchange.getRequest().getHeaders();
        
        // 验证B3 Single格式被正确解析
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", headers.getFirst(TracingHeaders.X_TRACE_ID));
        assertEquals("b3-single", headers.getFirst(TracingHeaders.X_TRACE_SOURCE));
    }

    @Test
    void testPriorityOrder() {
        // 测试多种头同时存在时的优先级顺序：W3C > B3 > Custom
        String w3cTraceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        String b3TraceId = "b3traceid1111111111111111111111111111";
        String customTraceId = "custom-trace-id";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/api/test")
                .header(TracingHeaders.W3C_TRACEPARENT, w3cTraceparent)
                .header(TracingHeaders.B3_TRACE_ID, b3TraceId)
                .header(TracingHeaders.X_TRACE_ID, customTraceId)
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = createMockChain();
        
        StepVerifier.create(tracingGlobalFilter.filter(exchange, chain))
                .verifyComplete();
        
        ServerWebExchange modifiedExchange = capturedExchange.get();
        HttpHeaders headers = modifiedExchange.getRequest().getHeaders();
        
        // 验证W3C优先级最高
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", headers.getFirst(TracingHeaders.X_TRACE_ID));
        assertEquals("w3c-traceparent", headers.getFirst(TracingHeaders.X_TRACE_SOURCE));
    }

    @Test
    void testTracestatePreservation() {
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        String tracestate = "congo=ucfJifl5GOE,rojo=00f067aa0ba902b7";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/api/test")
                .header(TracingHeaders.W3C_TRACEPARENT, traceparent)
                .header(TracingHeaders.W3C_TRACESTATE, tracestate)
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = createMockChain();
        
        StepVerifier.create(tracingGlobalFilter.filter(exchange, chain))
                .verifyComplete();
        
        ServerWebExchange modifiedExchange = capturedExchange.get();
        HttpHeaders headers = modifiedExchange.getRequest().getHeaders();
        
        // 验证tracestate被保持传递
        assertEquals(tracestate, headers.getFirst(TracingHeaders.W3C_TRACESTATE));
    }

    private GatewayFilterChain createMockChain() {
        return exchange -> {
            capturedExchange.set(exchange);
            return Mono.empty();
        };
    }
}
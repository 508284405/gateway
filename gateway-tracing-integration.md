# 网关TraceId集成建议

## 网关侧实施方案

### 1. 入口请求处理

```java
// 网关过滤器示例
public class TracingGatewayFilter implements GatewayFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 提取或生成traceId
        String traceId = extractOrGenerateTraceId(request);
        
        // 生成W3C格式的traceparent
        String spanId = generateSpanId();
        String traceparent = String.format("00-%s-%s-01", 
            normalizeTraceId(traceId), spanId);
        
        // 修改请求头，向下游传递
        ServerHttpRequest mutatedRequest = request.mutate()
            .header("traceparent", traceparent)
            .header("X-Trace-Id", traceId)  // 向下兼容
            .build();
        
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
    
    private String extractOrGenerateTraceId(ServerHttpRequest request) {
        // 1. 从上游W3C traceparent提取
        String traceparent = request.getHeaders().getFirst("traceparent");
        if (StringUtils.hasText(traceparent)) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 2 && parts[1].length() == 32) {
                return parts[1];
            }
        }
        
        // 2. 从自定义头提取
        String customTraceId = request.getHeaders().getFirst("X-Trace-Id");
        if (StringUtils.hasText(customTraceId)) {
            return customTraceId;
        }
        
        // 3. 生成新的traceId (根trace)
        return generateNewTraceId();
    }
}
```

### 2. 协议统一策略

#### 对外接口（面向客户端）
- 接受多种格式：B3、SkyWalking、自定义格式
- 兼容性优先，支持老版本客户端

#### 对内服务（面向下游）  
- **统一使用W3C Trace Context**
- 同时保留`X-Trace-Id`用于向下兼容
- 确保traceId格式标准化

### 3. 配置示例

```yaml
# Gateway配置
gateway:
  tracing:
    enabled: true
    # 入口采样策略
    sampling:
      rate: 1.0  # 开发环境100%，生产环境建议0.01
    # 协议转换配置
    protocol:
      input: ["b3", "skywalking", "w3c", "custom"]
      output: "w3c"
    # 头部配置
    headers:
      traceparent: true      # W3C标准头
      x-trace-id: true       # 兼容头
      x-span-id: true        # 兼容头
```

## SmartCS-Web侧验证

### 当前实现✅
1. **多协议兼容** - 支持W3C/自定义/传统格式
2. **自动降级** - 无traceId时自动生成
3. **MDC集成** - 自动设置到日志上下文
4. **下游传播** - HTTP客户端自动传递

### 推荐增强

```java
// 在TokenValidateFilter中添加网关来源验证
private String extractTraceId(HttpServletRequest request) {
    // 记录traceId来源，便于调试
    String source = "generated";
    
    // W3C traceparent (优先级最高)
    String traceparent = request.getHeader("traceparent");
    if (StringUtils.hasText(traceparent)) {
        String[] parts = traceparent.split("-");
        if (parts.length >= 2 && parts[1].length() == 32) {
            source = "gateway-w3c";
            log.debug("从Gateway W3C traceparent提取traceId: {} (来源: {})", parts[1], source);
            return parts[1];
        }
    }
    
    // 网关自定义头
    String gatewayTraceId = request.getHeader("X-Trace-Id");
    if (StringUtils.hasText(gatewayTraceId)) {
        source = "gateway-custom";
        log.debug("从Gateway X-Trace-Id提取traceId: {} (来源: {})", gatewayTraceId, source);
        return gatewayTraceId;
    }
    
    // ... 其他逻辑
}
```

## 端到端验证

### 1. 完整链路测试
```bash
# 客户端 -> Gateway -> SmartCS-Web -> 下游服务
curl -H "traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01" \
     http://gateway/smartcs/api/test
```

### 2. 日志验证
```log
# Gateway日志
[traceId=4bf92f3577b34da6a3ce929d0e0e4736] Gateway接收请求

# SmartCS-Web日志  
[traceId=4bf92f3577b34da6a3ce929d0e0e4736 spanId=00f067aa0ba902b7] TokenFilter提取traceId
[traceId=4bf92f3577b34da6a3ce929d0e0e4736 spanId=00f067aa0ba902b7] 业务逻辑处理

# 下游服务日志
[traceId=4bf92f3577b34da6a3ce929d0e0e4736 spanId=新spanId] 下游服务接收请求
```

## 总结

✅ **当前SmartCS-Web已完全支持从网关接收traceId**  
✅ **支持多种协议格式，向下兼容**  
✅ **自动生成机制，确保链路完整**  

**建议**：
1. 在Leyue Gateway中实施统一的tracing过滤器
2. 采用W3C Trace Context作为标准协议
3. 保留X-Trace-Id用于向下兼容
4. 完善监控和告警机制
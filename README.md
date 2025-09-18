# Leyue Gateway

Leyue Gateway 是基于 Spring Cloud Gateway 的 API 网关服务，提供统一的请求入口、JWT 鉴权、菜单级权限校验，并集成了阿里巴巴 Sentinel 进行流量防护，同时支持全链路追踪能力。该项目面向 Leyue 微服务体系，负责转发流量、增强安全策略以及与下游服务的链路打通。

## 核心特性
- **网关路由与服务发现**：依赖 Spring Cloud Gateway 与 Nacos，实现注册中心自动路由发现。`spring.cloud.gateway.discovery.locator.enabled=true` 可动态感知新服务。  
- **身份与权限控制**：集成 JWT 校验、角色/菜单权限透传，下游服务可通过标准请求头获取用户上下文信息。  
- **Sentinel 流控防护**：引入 `spring-cloud-starter-alibaba-sentinel` 与 `sentinel-spring-cloud-gateway-adapter`，提供网关维度的限流、熔断与自定义阻断响应。  
- **观测性支持**：内置追踪配置，可兼容 W3C、B3、自定义等多种 Trace 协议，并将统一的 trace 信息输出到日志与下游服务。  
- **运维可观测性**：启用了 Spring Boot Actuator 与 `management.endpoints.web.exposure.include` 网关端点，方便监控健康状态与路由信息。

## 环境要求
- JDK 17
- Maven 3.9+（或兼容版本）
- Nacos Server（服务注册与配置中心）
- Sentinel Dashboard（流控规则配置，可通过 `SENTINEL_DASHBOARD` 环境变量指定地址）

## 快速开始
1. **拉取代码并安装依赖**
   ```bash
   git clone <repository-url>
   cd gateway
   mvn dependency:go-offline
   ```
2. **准备基础设施**
   - 启动 Nacos Server，并在 `bootstrap.yml` 中配置连接信息（如使用）。
   - 启动 Sentinel Dashboard（默认地址 `localhost:8719`）。若地址不同，可通过环境变量覆盖：
     ```bash
     export SENTINEL_DASHBOARD=sentinel.example.com:8719
     export SENTINEL_TRANSPORT_PORT=8720
     ```
3. **本地启动网关**
   ```bash
   mvn spring-boot:run
   ```
   应用默认监听 `8080` 端口，可通过 `http://localhost:8080/actuator/health` 检查健康状态。

4. **打包发布**
   ```bash
   mvn clean package -DskipTests
   java -jar target/leyue-gateway-0.0.1-SNAPSHOT.jar
   ```

## Sentinel 集成说明
- **依赖与自动装配**：`pom.xml` 中引入了 Sentinel Starter 以及 Gateway Adapter，框架会自动注册限流过滤器。  
- **全局过滤器**：`SentinelGatewayConfiguration` 手动声明了 `SentinelGatewayFilter`，并注册自定义 `SentinelGatewayBlockExceptionHandler`，保证限流结果返回统一的 JSON 结构：
  ```json
  {
    "code": 429,
    "message": "请求过于频繁，请稍后再试",
    "routeId": "<触发限流的路由ID>",
    "requestUri": "/api/example"
  }
  ```
- **初始化阻断处理器**：在 `@PostConstruct` 钩子中通过 `GatewayCallbackManager` 注册了自定义 BlockHandler，可根据实际需要扩展返回字段或国际化信息。  
- **配置项**：`application.yml` 内的 `spring.cloud.sentinel.transport.dashboard` 与 `port` 指定了上报地址及本地通信端口，均支持环境变量覆盖。

## 主要配置项
- `spring.cloud.gateway.discovery.locator`：控制是否根据注册中心自动创建路由及服务名大小写。  
- `spring.cloud.sentinel.eager`：开启后提前加载 Sentinel 规则，避免首次请求时初始化带来的延迟。  
- `gateway.tracing.*`：定义采样率、协议映射、日志输出等追踪相关选项，默认开发环境全量采样。  
- `management.endpoints.web.exposure.include`：暴露健康、信息、指标与网关端点，便于运维监控。  
- `logging.pattern.console`：自定义日志格式，输出 traceId/spanId 便于排查链路问题。

## 常用开发命令
```bash
mvn spring-boot:run             # 本地运行
mvn clean package -DskipTests   # 构建可执行 JAR
mvn -q test                     # 执行单元测试
```
> 受限于离线环境，若 Maven 无法从中央仓库拉取依赖，请预先下载或使用私有仓库。

## 目录结构
```
.
├── pom.xml                     # Maven 构建文件
├── Dockerfile                  # 容器化构建配置
├── src/main/java/com/yuwang/leyuegateway
│   └── config/SentinelGatewayConfiguration.java  # Sentinel 过滤器与阻断处理器配置
├── src/main/resources/application.yml            # 应用配置
├── gateway-tracing-integration.md                # 链路追踪方案说明
└── README.md
```

## 参考文档
- [Spring Cloud Gateway 官方文档](https://spring.io/projects/spring-cloud-gateway)
- [Sentinel Spring Cloud Gateway 适配器文档](https://github.com/alibaba/Sentinel/wiki/Spring-Cloud-Gateway)

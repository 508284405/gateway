# ---------- 编译阶段（可在 CI 里跳过） ----------
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests -Pprod

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre-jammy
LABEL maintainer="devops@leyue.com"
ENV TZ=Asia/Shanghai \
    LANG=zh_CN.UTF-8 \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport \
                       -XX:MaxRAMPercentage=75 \
                       -Djava.security.egd=file:/dev/./urandom"

# 拷贝 jar
COPY --from=build /workspace/target/leyue-gateway-*.jar /app/gateway.jar

# 默认端口（与 application.yml 保持一致）
EXPOSE 8080

# 健康检查（Spring Boot Actuator）
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health \
  || exit 1

ENTRYPOINT ["java","-jar","/app/gateway.jar"]
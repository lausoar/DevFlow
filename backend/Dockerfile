# syntax=docker/dockerfile:1
# ============================================
# DevFlow 后端镜像（Spring Boot 3.4 / JDK 17）
# ============================================
# 多阶段构建：构建层带完整 Maven + JDK，运行层只保留 JRE 与 jar。
# 运行：docker run --env-file .env -p 8080:8080 devflow/backend:1.0.0

# ---------- 构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷 pom，依赖层可被缓存（源码变动不必重下依赖）
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行 ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 非 root 运行
RUN addgroup -S devflow && adduser -S devflow -G devflow \
    && mkdir -p /app/logs && chown -R devflow:devflow /app
USER devflow

COPY --from=build --chown=devflow:devflow /build/target/*.jar /app/app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Dfile.encoding=UTF-8" \
    SPRING_PROFILES_ACTIVE=prod \
    MANAGEMENT_PORT=9090

EXPOSE 8080 9090

# Actuator 监听独立管理端口，且不在 /api/v1 业务 context-path 下。
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${MANAGEMENT_PORT:-9090}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

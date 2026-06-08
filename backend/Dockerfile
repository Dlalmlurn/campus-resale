# 依赖缓存层：生产构建和开发镜像复用 Maven 本地仓库，加速重复构建。
FROM docker.1ms.run/library/maven:3.9-eclipse-temurin-21-alpine AS deps
WORKDIR /workspace
COPY pom.xml .
COPY settings.xml .
RUN mvn -B -s settings.xml dependency:go-offline -q

# 开发阶段：compose.override.yaml 挂载源码后直接运行 Spring Boot，避免业务代码改动后重建镜像。
FROM docker.1ms.run/library/maven:3.9-eclipse-temurin-21-alpine AS dev
WORKDIR /workspace
COPY --from=deps /root/.m2 /root/.m2
COPY pom.xml .
COPY settings.xml .
EXPOSE 8080
CMD ["mvn", "-B", "-s", "settings.xml", "spring-boot:run"]

# 构建阶段：生成生产运行所需的可执行 jar。
FROM docker.1ms.run/library/maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY --from=deps /root/.m2 /root/.m2
COPY pom.xml .
COPY settings.xml .
COPY src ./src
RUN mvn -B -s settings.xml package -DskipTests -q

# 生产运行阶段：compose.yaml 默认构建最后阶段，保持原有发布行为。
FROM docker.1ms.run/library/eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# 设置时区为 Asia/Shanghai
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata

COPY --from=build /workspace/target/campus-resale-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

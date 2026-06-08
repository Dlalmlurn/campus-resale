# 阶段一：Maven 构建（缓存依赖层，加速重复构建）
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
# 先单独复制 pom.xml 缓存依赖，只有 pom.xml 变动时才重新下载
COPY pom.xml .
COPY settings.xml .
RUN mvn -B -s settings.xml dependency:go-offline -q
COPY src ./src
RUN mvn -B -s settings.xml package -DskipTests -q

# 阶段二：最小 JRE 运行镜像
FROM eclipse-temurin:21-jre-alpine
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

# Phoenix RTC 服务端 Dockerfile

FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制 pom.xml 并下载依赖（利用 Docker 缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 编译打包
RUN mvn clean package -DskipTests

# 运行时镜像
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制 jar 文件
COPY --from=builder /app/target/phoenix-rtc-server-1.0.0.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]

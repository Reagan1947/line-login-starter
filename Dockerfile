# 构建阶段：使用带 Maven 的 Java 17 镜像（eclipse-temurin 仅含 JDK 不含 mvn）
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 先复制依赖定义，利用 Docker 层缓存
COPY pom.xml .
# 下载依赖（不执行构建）
RUN mvn dependency:go-offline -B

# 复制源码并打包
COPY src ./src
RUN mvn package -DskipTests -B

# 运行阶段：使用精简 JRE 运行
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 从构建阶段复制打好的 jar（finalName 为 line-login-starter）
COPY --from=builder /app/target/line-login-starter.jar app.jar

# 应用配置端口 9000（见 application.yml）
EXPOSE 9000

# 使用 exec 形式以便正确接收信号
ENTRYPOINT ["java", "-jar", "app.jar"]

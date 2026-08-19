# Java 后端生产镜像
FROM maven:3.9-eclipse-temurin-8 AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:8-jre

WORKDIR /app
COPY --from=builder /build/target/earnings-calendar.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
VOLUME ["/app/data"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

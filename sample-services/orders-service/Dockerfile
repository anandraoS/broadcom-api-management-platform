# Build context: repository root.  docker build -f sample-services/orders-service/Dockerfile -t api-platform/orders-service:1.0.0 .
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY control-plane/pom.xml control-plane/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY sample-services/orders-service/pom.xml sample-services/orders-service/pom.xml
RUN mvn -B -q -pl sample-services/orders-service -am dependency:go-offline
COPY sample-services/orders-service sample-services/orders-service
RUN mvn -B -q -pl sample-services/orders-service -DskipTests package \
 && java -Djarmode=layertools -jar sample-services/orders-service/target/orders-service-1.0.0.jar extract --destination /layers

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -G app -u 10001 -H -s /sbin/nologin app
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER app
EXPOSE 9001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -qO- http://localhost:9001/actuator/health/liveness >/dev/null || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

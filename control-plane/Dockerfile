# Build context: repository root (needs the parent pom).  docker build -f control-plane/Dockerfile -t api-platform/control-plane:1.0.0 .
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY control-plane/pom.xml control-plane/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY sample-services/orders-service/pom.xml sample-services/orders-service/pom.xml
RUN mvn -B -q -pl control-plane -am dependency:go-offline
COPY control-plane control-plane
RUN mvn -B -q -pl control-plane -DskipTests -DskipITs package \
 && java -Djarmode=layertools -jar control-plane/target/control-plane-1.0.0.jar extract --destination /layers

FROM eclipse-temurin:21-jre-alpine
# non-root, no shell login, read-only root filesystem compatible (writes only to /tmp)
RUN addgroup -S -g 10001 app && adduser -S -G app -u 10001 -H -s /sbin/nologin app
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness >/dev/null || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

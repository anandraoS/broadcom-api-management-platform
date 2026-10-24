# Build context: repository root.  docker build -f gateway/Dockerfile -t api-platform/gateway:1.0.0 .
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY control-plane/pom.xml control-plane/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY sample-services/orders-service/pom.xml sample-services/orders-service/pom.xml
RUN mvn -B -q -pl gateway -am dependency:go-offline
COPY gateway gateway
RUN mvn -B -q -pl gateway -DskipTests -DskipITs package \
 && java -Djarmode=layertools -jar gateway/target/gateway-1.0.0.jar extract --destination /layers

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -G app -u 10001 -H -s /sbin/nologin app \
 && mkdir -p /var/lib/gateway && chown app:app /var/lib/gateway
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER app
EXPOSE 8081
# snapshot cache lives on a writable volume (emptyDir in Kubernetes) so the root filesystem can stay read-only
ENV SNAPSHOT_CACHE_FILE=/var/lib/gateway/snapshot.json
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=5 \
  CMD wget -qO- http://localhost:8081/actuator/health/liveness >/dev/null || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

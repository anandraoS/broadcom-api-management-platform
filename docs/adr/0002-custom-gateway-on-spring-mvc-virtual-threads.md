# ADR-0002: Custom gateway on Spring MVC with virtual threads (not Spring Cloud Gateway)

* Status: accepted - Date: 2026-10-17

## Context
The gateway must be Java-based. Spring Cloud Gateway (reactive, Netty) is the obvious option; Spring Boot is 3.3.5 (compatible Spring Cloud train 2023.0.x).
Our needs: registry-driven immutable route table, bespoke policy enforcement order, strict bounded buffering, custom limiter and breaker, full control of error contract, easy unit testing with fake clocks.

## Decision
Implement the gateway as a servlet filter pipeline on Spring MVC + Tomcat with **virtual threads** (`spring.threads.virtual.enabled`) and the JDK `HttpClient` for upstream calls.

## Alternatives
1. **Spring Cloud Gateway:** mature filters, WebFlux streaming, built-in Resilience4j/Redis rate limiter. Cost: reactive programming model for every contributor, route definitions are mostly static (dynamic refresh requires extra work), the policy pipeline would be bespoke filters anyway, and reactive debugging/test complexity is higher.
2. **Envoy/Kong/NGINX:** excellent data planes but not Java and outside the "Java-based services" constraint.
3. **Servlet + platform threads:** simple but thread-per-request caps concurrency; virtual threads remove that limit for blocking I/O.

## Consequences
+ Straight-line blocking code (readable, debuggable); virtual threads give high concurrency; every component is unit-testable; no framework magic between request and policy.
- We own correctness of proxy edge cases (hop-by-hop headers, bodies are **buffered**, no streaming/SSE/WebSocket, HTTP/1.1 upstream only); we reimplemented a breaker and limiter (small, tested) instead of using vetted libraries; synchronized sections that block I/O can pin carrier threads (none on the hot path today).

## Review triggers
Need for streaming bodies, WebSockets/gRPC, HTTP/2 upstream, or > ~20k req/s per instance with strict tail latency; a maintained library covers our needs with less code (then adopt Spring Cloud Gateway or Resilience4j).

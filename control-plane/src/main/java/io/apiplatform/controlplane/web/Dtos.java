package io.apiplatform.controlplane.web;

import io.apiplatform.controlplane.domain.Lifecycle;
import io.apiplatform.controlplane.domain.PolicyStatus;
import io.apiplatform.controlplane.policy.PolicyConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

/** Request/response DTOs. Entities are never exposed directly (avoids mass assignment and lazy-loading leaks). */
public final class Dtos {
    private Dtos() {}

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageView<T> of(Page<T> p) {
            return new PageView<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    public record CreateApiRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,78}[a-z0-9]", message = "lowercase letters, digits and dashes (3-80 chars)") String slug,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Pattern(regexp = "[a-z0-9-]{2,80}") String ownerTeam,
            @Size(max = 1000) String description) {}

    public record UpdateApiRequest(@NotBlank @Size(max = 160) String name, @Size(max = 1000) String description,
                                   @NotNull Long version) {}

    public record LifecycleRequest(@NotNull Lifecycle target) {}

    public record CreateVersionRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,20}") String label,
            @NotBlank @Pattern(regexp = "/[a-z0-9][a-z0-9/_-]{0,100}", message = "must start with / and use lowercase path characters") String basePath,
            @Size(max = 200_000) String openapiSpec) {}

    public record TargetRequest(@NotBlank @Size(max = 500) String url, @Min(1) @Max(100) int weight,
                                @Pattern(regexp = "/[A-Za-z0-9/_.-]{0,150}") String healthPath) {}

    public record TargetView(Long id, String url, int weight, String healthPath) {}

    public record VersionView(Long id, String label, String basePath, Lifecycle status, boolean hasSpec,
                              List<TargetView> targets) {}

    public record ApiView(Long id, String slug, String name, String ownerTeam, Lifecycle lifecycle, String description,
                          long version, Instant createdAt, Instant updatedAt, List<VersionView> versions) {}

    public record PolicySubmitRequest(@NotNull @Valid PolicyConfig config, @Size(max = 500) String comment) {}

    public record DecisionRequest(@Size(max = 500) String comment) {}

    public record RevertRequest(@Min(1) int toVersionNo) {}

    public record PolicyView(Long id, Long apiId, int versionNo, PolicyStatus status, PolicyConfig config,
                             String submittedBy, String approvedBy, String comment, Instant createdAt, Instant decidedAt) {}

    public record ValidationResult(boolean valid, List<String> errors) {}

    public record ConsumerRequest(@NotBlank @Size(max = 120) String name,
                                  @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{2,78}") String clientId,
                                  @NotBlank @Email @Size(max = 160) String ownerEmail) {}

    public record ConsumerView(Long id, String name, String clientId, String ownerEmail, boolean active, Instant createdAt) {}

    public record SubscribeRequest(@NotNull Long apiId) {}

    public record SubscriptionView(Long id, Long consumerId, Long apiId, String apiSlug, String status) {}

    public record IssueKeyRequest(@Min(1) @Max(365) int validDays) {}

    /** The plaintext key is shown exactly once; only its hash is stored. */
    public record KeyIssued(Long id, String keyPrefix, String apiKey, Instant expiresAt) {}

    public record KeyView(Long id, String keyPrefix, Instant expiresAt, boolean revoked, Instant createdAt) {}

    public record AuditView(Long id, Instant ts, String actor, String action, String entityType, String entityId,
                            String detail, String correlationId) {}

    public record EventView(Long id, Instant ts, String type, String severity, String routeSlug, String message,
                            String correlationId) {}

    public record TrafficRouteSummary(String route, long requests, long errors4xx, long errors5xx, double errorRate,
                                      double avgLatencyMs, long maxLatencyMs, long rateLimited, long authFailures, long timeouts) {}

    public record TrafficSummary(int windowMinutes, long totalRequests, double overallErrorRate,
                                 List<TrafficRouteSummary> routes) {}

    public record Finding(String rule, String severity, String route, String summary,
                          java.util.Map<String, Object> evidence, List<String> suggestedSteps) {}

    public record IncidentReport(Instant generatedAt, int windowMinutes, List<Finding> findings,
                                 List<AuditView> recentConfigChanges, List<EventView> recentEvents, String note) {}

    // gateway -> control plane telemetry ingestion
    public record BucketIn(@NotBlank @Size(max = 80) String route, @NotNull Instant bucketStart, @Min(0) long requests,
                           @Min(0) long errors4xx, @Min(0) long errors5xx, @Min(0) long rateLimited,
                           @Min(0) long authFailures, @Min(0) long timeouts, @Min(0) long latencySumMs,
                           @Min(0) long latencyMaxMs) {}

    public record EventIn(@NotNull Instant ts, @NotBlank @Size(max = 40) String type, @NotBlank @Pattern(regexp = "INFO|WARN|ERROR") String severity,
                          @Size(max = 80) String route, @NotBlank @Size(max = 500) String message,
                          @Size(max = 64) String correlationId) {}

    public record TelemetryBatch(@NotBlank @Size(max = 64) String batchId, @NotNull @Size(max = 500) List<@Valid BucketIn> buckets,
                                 @NotNull @Size(max = 500) List<@Valid EventIn> events) {}

    public record TokenRequest(@NotBlank String username, @NotBlank String password) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, List<String> roles, String team) {}
}

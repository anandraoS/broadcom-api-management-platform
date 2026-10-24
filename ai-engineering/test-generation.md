# Test generation module

**Goal:** tests that fail for the right reason and document behaviour. Generated tests are code: review them as carefully as production code and make them fail first (mutate the code or assertion) to prove they test something.

**Process**
1. State the behaviour in one sentence ("a POST is attempted exactly once even if the backend returns 503").
2. Pick the layer: pure logic -> unit; Spring wiring/security/JPA -> `*IT` with MockMvc (control plane) or `RANDOM_PORT` + real backend (gateway); browser flows -> Playwright; needs Redis/MySQL -> Testcontainers (auto-skip without Docker).
3. Arrange minimal data with unique identifiers (tests share an in-memory DB); inject fake clocks for time.
4. Cover: happy path, each validation boundary, each authorization denial (wrong role, other team), failure of each dependency, concurrency where state is shared, idempotent replays.
5. Assert on **observable contract** (status, body code, headers, persisted state, audit rows, metrics), not private fields.
6. Name tests as sentences; one behaviour per test; no `Thread.sleep` where a clock can be injected; no order dependence.
7. Run the test alone and the full suite; record the real outcome.

**Existing patterns to copy:** `ControlPlaneApiIT` (token helper, `as(...)`), `GatewayRoutingIT` (backend counters, `apply(route...)`), `InMemoryRateLimiterTest` (fake clock + threads), `ResilientRateLimiterTest` (failing fake dependency), `DestinationValidatorTest` (attack-string table), Angular specs (`HttpTestingController`, `fakeToken`).

**Review checklist for generated tests:** asserts something specific? would it fail if the feature broke? deterministic? independent? secrets/real data absent? skipped for a documented reason only?

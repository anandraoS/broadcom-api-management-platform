package io.apiplatform.gateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PathSafetyTest {
    @Test
    void ordinaryPathsAreSafe() {
        for (String p : List.of("", "/", "/items/5", "/a/b.c/d-e_f", "/search%20term"))
            assertThat(GatewayFilter.unsafePath(p)).as(p).isFalse();
    }

    @Test
    void dotSegmentsAndEncodingTricksAreRejected() {
        for (String p : List.of("/../admin", "/a/../b", "/%2e%2e/admin", "/%2E%2E/admin", "/%2e%2e%2fb", "/.", "//evil",
                "/a\\b", "/a%5cb", "/%00", "/a//b", "/%zz"))
            assertThat(GatewayFilter.unsafePath(p)).as(p).isTrue();
    }
}

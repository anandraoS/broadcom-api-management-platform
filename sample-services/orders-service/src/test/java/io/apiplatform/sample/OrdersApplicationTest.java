package io.apiplatform.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OrdersApplicationTest {
    private final OrdersApplication app = new OrdersApplication("t1");

    @Test
    void createsAndReadsOrder() {
        var created = app.create(new OrdersApplication.NewOrder("SKU-X", 3));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(app.get(created.getBody().id()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unknownOrderIs404() {
        assertThat(app.get(99999).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void flakyAlwaysFailsAt100Percent() {
        assertThat(app.flaky(100).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}

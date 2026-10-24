package io.apiplatform.controlplane.repo;

import io.apiplatform.controlplane.domain.Subscription;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByConsumerId(Long consumerId);
    boolean existsByConsumerIdAndApiId(Long consumerId, Long apiId);
    List<Subscription> findByStatus(String status);
}

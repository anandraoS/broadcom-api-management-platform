package io.apiplatform.controlplane.repo;

import io.apiplatform.controlplane.domain.ApiKey;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    List<ApiKey> findByConsumerId(Long consumerId);
}

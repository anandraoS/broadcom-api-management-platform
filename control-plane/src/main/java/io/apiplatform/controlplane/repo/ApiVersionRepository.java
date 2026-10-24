package io.apiplatform.controlplane.repo;

import io.apiplatform.controlplane.domain.ApiVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiVersionRepository extends JpaRepository<ApiVersion, Long> {
    List<ApiVersion> findByApiId(Long apiId);
    boolean existsByBasePath(String basePath);
}

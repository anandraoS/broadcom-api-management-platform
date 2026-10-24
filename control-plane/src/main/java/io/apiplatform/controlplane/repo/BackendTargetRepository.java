package io.apiplatform.controlplane.repo;

import io.apiplatform.controlplane.domain.BackendTarget;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackendTargetRepository extends JpaRepository<BackendTarget, Long> {
    List<BackendTarget> findByApiVersionId(Long apiVersionId);
}

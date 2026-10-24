package io.apiplatform.controlplane.repo;

import io.apiplatform.controlplane.domain.PolicyStatus;
import io.apiplatform.controlplane.domain.PolicyVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {
    List<PolicyVersion> findByApiIdOrderByVersionNoDesc(Long apiId);
    Optional<PolicyVersion> findByApiIdAndStatus(Long apiId, PolicyStatus status);
    Optional<PolicyVersion> findByApiIdAndVersionNo(Long apiId, int versionNo);
    List<PolicyVersion> findByStatus(PolicyStatus status);

    @Query("select coalesce(max(p.versionNo), 0) from PolicyVersion p where p.api.id = :apiId")
    int maxVersionNo(@Param("apiId") Long apiId);
}

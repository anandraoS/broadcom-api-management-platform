package io.apiplatform.controlplane.repo;

import io.apiplatform.controlplane.domain.OperationalEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationalEventRepository extends JpaRepository<OperationalEvent, Long> {
    @Query("""
        select e from OperationalEvent e
        where (:type is null or e.type = :type)
          and (:route is null or e.routeSlug = :route)
          and (:severity is null or e.severity = :severity)
        """)
    Page<OperationalEvent> search(@Param("type") String type, @Param("route") String route,
                                  @Param("severity") String severity, Pageable pageable);

    List<OperationalEvent> findByTsAfterOrderByTsDesc(Instant since);

    @Modifying
    @Query("delete from OperationalEvent e where e.ts < :before")
    int deleteOlderThan(@Param("before") Instant before);
}

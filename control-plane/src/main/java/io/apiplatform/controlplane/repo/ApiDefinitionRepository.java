package io.apiplatform.controlplane.repo;

import io.apiplatform.controlplane.domain.ApiDefinition;
import io.apiplatform.controlplane.domain.Lifecycle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiDefinitionRepository extends JpaRepository<ApiDefinition, Long> {
    Optional<ApiDefinition> findBySlug(String slug);

    /** Parameterised JPQL: no string concatenation, so user input cannot alter the query structure. */
    @Query("""
        select a from ApiDefinition a
        where (:q is null or lower(a.name) like lower(concat('%', :q, '%')) or lower(a.slug) like lower(concat('%', :q, '%')))
          and (:lifecycle is null or a.lifecycle = :lifecycle)
          and (:owner is null or a.ownerTeam = :owner)
        """)
    Page<ApiDefinition> search(@Param("q") String q, @Param("lifecycle") Lifecycle lifecycle,
                               @Param("owner") String owner, Pageable pageable);

    /** One query for the whole routing snapshot (avoids N+1). */
    @Query("select distinct a from ApiDefinition a left join fetch a.versions v where a.lifecycle = :l")
    List<ApiDefinition> findAllWithVersionsByLifecycle(@Param("l") Lifecycle lifecycle);
}

package io.apiplatform.controlplane.repo;

import io.apiplatform.controlplane.domain.Consumer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsumerRepository extends JpaRepository<Consumer, Long> {
    @Query("select c from Consumer c where :q is null or lower(c.name) like lower(concat('%', :q, '%'))")
    Page<Consumer> search(@Param("q") String q, Pageable pageable);
}

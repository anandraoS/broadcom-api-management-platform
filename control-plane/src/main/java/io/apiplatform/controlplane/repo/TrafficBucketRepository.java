package io.apiplatform.controlplane.repo;

import io.apiplatform.controlplane.domain.TrafficBucket;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrafficBucketRepository extends JpaRepository<TrafficBucket, Long> {
    boolean existsByBatchIdAndRouteSlugAndBucketStart(String batchId, String routeSlug, Instant bucketStart);
    List<TrafficBucket> findByBucketStartAfter(Instant since);
    List<TrafficBucket> findByRouteSlugAndBucketStartAfterOrderByBucketStartAsc(String routeSlug, Instant since);

    @Modifying
    @Query("delete from TrafficBucket b where b.bucketStart < :before")
    int deleteOlderThan(@Param("before") Instant before);
}

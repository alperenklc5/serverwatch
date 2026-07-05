package com.serverwatch.repository;

import com.serverwatch.model.entity.MetricSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data repository for {@link MetricSnapshot} time-series records.
 */
@Repository
public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, Long> {

    List<MetricSnapshot> findByMetricTypeAndRecordedAtBetween(
            String metricType, Instant from, Instant to
    );

    @Modifying
    @Query("DELETE FROM MetricSnapshot m WHERE m.recordedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}

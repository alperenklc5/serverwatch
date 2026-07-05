package com.serverwatch.repository;

import com.serverwatch.model.entity.AlertHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {

    /** Returns paginated history for a single rule, newest first. */
    List<AlertHistory> findByRuleIdOrderByCreatedAtDesc(Long ruleId, Pageable pageable);

    /** Returns all history entries in a time window, newest first. */
    List<AlertHistory> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);

    /** Returns the most recent history entry for a rule (used for cooldown checks). */
    Optional<AlertHistory> findTopByRuleIdOrderByCreatedAtDesc(Long ruleId);

    /** Bulk-deletes history older than a given cutoff. */
    @Modifying
    @Query("DELETE FROM AlertHistory h WHERE h.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}

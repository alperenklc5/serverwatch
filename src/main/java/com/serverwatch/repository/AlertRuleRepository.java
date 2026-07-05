package com.serverwatch.repository;

import com.serverwatch.model.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    /** Returns all rules that are currently enabled. */
    List<AlertRule> findByEnabledTrue();

    /** Returns all rules targeting a given metric type. */
    List<AlertRule> findByMetricType(String metricType);
}

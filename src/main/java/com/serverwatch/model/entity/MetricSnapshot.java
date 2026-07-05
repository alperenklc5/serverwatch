package com.serverwatch.model.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Persisted time-series record for a single metric data point.
 * Mapped to the {@code metric_snapshots} table created by V1 migration.
 */
@Entity
@Table(name = "metric_snapshots",
       indexes = @Index(name = "idx_metric_snapshots_type_time",
                        columnList = "metric_type, recorded_at"))
public class MetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Logical category, e.g. CPU_USAGE, MEMORY_USAGE, DISK_USAGE, NET_RECV, NET_SENT. */
    @Column(name = "metric_type", nullable = false, length = 50)
    private String metricType;

    /** Optional sub-key, e.g. interface name or mount point. */
    @Column(name = "metric_key", length = 255)
    private String metricKey;

    @Column(nullable = false)
    private double value;

    @Column(length = 20)
    private String unit;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected MetricSnapshot() {}

    public MetricSnapshot(String metricType, String metricKey, double value,
                          String unit, Instant recordedAt) {
        this.metricType = metricType;
        this.metricKey  = metricKey;
        this.value      = value;
        this.unit       = unit;
        this.recordedAt = recordedAt;
    }

    public Long getId()           { return id; }
    public String getMetricType() { return metricType; }
    public String getMetricKey()  { return metricKey; }
    public double getValue()      { return value; }
    public String getUnit()       { return unit; }
    public Instant getRecordedAt(){ return recordedAt; }
}

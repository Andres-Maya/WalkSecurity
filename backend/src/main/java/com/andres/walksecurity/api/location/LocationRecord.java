package com.andres.walksecurity.api.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "location_records")
public class LocationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "accuracy_meters")
    private Float accuracyMeters;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected LocationRecord() {}

    public LocationRecord(Long userId, Double latitude, Double longitude, Float accuracyMeters, Instant recordedAt) {
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.recordedAt = recordedAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Float getAccuracyMeters() { return accuracyMeters; }
    public Instant getRecordedAt() { return recordedAt; }
}

package com.andres.walksecurity.api.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "alerts")
public class Alert {

    public enum Type { SOS, RISK_ZONE }

    public enum Status { ACTIVE, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    private Double latitude;

    private Double longitude;

    @Column(name = "accuracy_meters")
    private Float accuracyMeters;

    @Column(length = 500)
    private String message;

    @Column(name = "risk_zone_id")
    private Long riskZoneId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Alert() {}

    public Alert(Long userId, Type type, Double latitude, Double longitude, Float accuracyMeters, String message) {
        this.userId = userId;
        this.type = type;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.message = message;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Float getAccuracyMeters() { return accuracyMeters; }
    public String getMessage() { return message; }
    public Long getRiskZoneId() { return riskZoneId; }
    public Instant getCreatedAt() { return createdAt; }
}

package com.andres.walksecurity.api.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "trusted_contacts")
public class TrustedContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 60)
    private String relationship;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TrustedContact() {}

    public TrustedContact(Long userId, String name, String phone, String relationship) {
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.relationship = relationship;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getRelationship() { return relationship; }
    public Instant getCreatedAt() { return createdAt; }
}

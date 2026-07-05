package com.serverwatch.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Audit record for every login attempt — used for brute-force detection and security review.
 */
@Entity
@Table(name = "login_attempts")
@Getter
@Setter
@NoArgsConstructor
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt = Instant.now();
}

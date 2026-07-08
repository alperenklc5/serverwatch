package com.serverwatch.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "user_permissions")
@Getter
@Setter
@NoArgsConstructor
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Permission permission;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "granted_by")
    private Long grantedBy;
}

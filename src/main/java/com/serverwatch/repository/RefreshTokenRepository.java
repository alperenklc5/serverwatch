package com.serverwatch.repository;

import com.serverwatch.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    List<RefreshToken> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :time")
    int deleteByExpiresAtBefore(@Param("time") Instant time);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.userId = :userId")
    void revokeAllForUser(@Param("userId") Long userId);
}

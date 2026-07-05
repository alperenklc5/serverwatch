package com.serverwatch.repository;

import com.serverwatch.model.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    long countByUsernameAndSuccessFalseAndAttemptedAtAfter(String username, Instant since);

    long countByIpAddressAndSuccessFalseAndAttemptedAtAfter(String ipAddress, Instant since);
}

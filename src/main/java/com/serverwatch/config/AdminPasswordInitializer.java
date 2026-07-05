package com.serverwatch.config;

import com.serverwatch.model.entity.User;
import com.serverwatch.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Startup check that ensures the seeded admin user's password hash is valid.
 *
 * <p>Runs first (Order 1) after the application context is fully initialised.
 * If the BCrypt hash stored in the DB does not match "changeme" (e.g. because
 * the static string in V3__users_and_auth.sql was wrong), a fresh hash is
 * generated and persisted so that {@code admin / changeme} works immediately.
 *
 * <p>NOTE: {@code @Transactional} is intentionally absent from {@code run()}.
 * Wrapping a {@code CommandLineRunner} in a Spring-managed transaction via AOP
 * can silently prevent the proxy from being registered as a runner. Each
 * repository call carries its own transaction, which is sufficient here.
 */
@Slf4j
@Component
@Order(1)
public class AdminPasswordInitializer implements CommandLineRunner {

    private static final String ADMIN_USERNAME   = "admin";
    private static final String DEFAULT_PASSWORD = "changeme";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminPasswordInitializer(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // System.err bypasses the logging framework — visible even if logback isn't wired yet
        System.err.println("[AdminPasswordInitializer] run() invoked");

        try {
            userRepository.findByUsername(ADMIN_USERNAME).ifPresentOrElse(
                    this::verifyOrFixHash,
                    () -> log.warn("Admin user '{}' not found in DB — skipping password check",
                            ADMIN_USERNAME)
            );
        } catch (Exception ex) {
            log.error("AdminPasswordInitializer failed: {}", ex.getMessage(), ex);
        }
    }

    private void verifyOrFixHash(User admin) {
        if (passwordEncoder.matches(DEFAULT_PASSWORD, admin.getPasswordHash())) {
            log.info("[AdminPasswordInitializer] Hash OK — '{}' can log in with '{}'",
                    ADMIN_USERNAME, DEFAULT_PASSWORD);
        } else {
            log.warn("[AdminPasswordInitializer] Hash mismatch — re-encoding '{}' now",
                    DEFAULT_PASSWORD);

            String freshHash = passwordEncoder.encode(DEFAULT_PASSWORD);
            log.debug("[AdminPasswordInitializer] Fresh hash: {}", freshHash);

            admin.setPasswordHash(freshHash);
            userRepository.save(admin);

            log.info("[AdminPasswordInitializer] Hash updated. Login: username='{}' password='{}'",
                    ADMIN_USERNAME, DEFAULT_PASSWORD);
        }
    }
}

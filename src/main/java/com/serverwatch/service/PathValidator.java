package com.serverwatch.service;

import com.serverwatch.config.ServerWatchProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Security gate for every file-system operation.
 *
 * <p><b>Threat model defended against:</b>
 * <ol>
 *   <li><b>Path traversal</b> — {@code ..} sequences are eliminated by
 *       {@link Path#normalize()} before any comparison.</li>
 *   <li><b>Symlink escapes</b> — for existing paths {@link Path#toRealPath()} follows
 *       every symlink in the chain and the resolved target is re-checked against the
 *       allow-list. For non-existing target paths (create / upload) the deepest
 *       existing ancestor is resolved first so a symlink-to-directory parent cannot
 *       redirect the write outside the allowed roots.</li>
 *   <li><b>Null-byte injection</b> — rejected before parsing.</li>
 *   <li><b>Absolute-path bypass</b> — all paths are made absolute and normalised;
 *       relative paths cannot escape the root they are resolved against.</li>
 *   <li><b>Denied-path access</b> — a glob-style block-list ({@code /etc/shadow},
 *       {@code /home/*‌/.ssh}, …) is applied after symlink resolution.</li>
 * </ol>
 *
 * <p><b>Critical:</b> this class uses only {@link java.nio.file} APIs — never
 * string concatenation on paths.
 */
@Slf4j
@Component
public class PathValidator {

    private final ServerWatchProperties properties;
    private List<Path> allowedRoots;
    private List<Path> readonlyRoots;
    private List<Pattern> deniedPatterns;

    public PathValidator(ServerWatchProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        ServerWatchProperties.FileManager cfg = properties.getFilemanager();

        allowedRoots  = normalisePaths(cfg.getAllowedRoots(),  "allowed-root");
        readonlyRoots = normalisePaths(cfg.getReadonlyRoots(), "readonly-root");
        deniedPatterns = compileDeniedPatterns(cfg.getDeniedPaths());

        log.info("PathValidator ready — {} allowed roots, {} readonly, {} denied patterns",
                allowedRoots.size(), readonlyRoots.size(), deniedPatterns.size());
        allowedRoots.forEach(r -> log.debug("  allowed: {}", r));
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Validates and resolves a user-supplied path string to an absolute, normalised,
     * symlink-followed {@link Path} that is within the configured allowed roots.
     *
     * @throws SecurityException    if the path is outside allowed roots, matches a
     *                              denied pattern, or contains illegal characters
     * @throws UncheckedIOException if the filesystem cannot be queried
     */
    public Path validateAndResolve(String userPath) {
        // 1. Basic sanity
        if (userPath == null || userPath.isBlank()) {
            throw new SecurityException("Path must not be null or empty");
        }
        if (userPath.indexOf('\0') >= 0) {
            throw new SecurityException("Path contains null bytes");
        }

        // 2. Normalise — eliminates .., ., multiple separators
        Path candidate;
        try {
            candidate = Paths.get(userPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new SecurityException("Invalid path: " + e.getReason());
        }

        // 3. Follow symlinks (prevents symlink-escape attacks)
        Path resolved = resolveWithSymlinks(candidate);

        // 4. Must start with at least one allowed root
        boolean permitted = allowedRoots.stream().anyMatch(resolved::startsWith);
        if (!permitted) {
            // Don't echo the full resolved path in the message (info-leak to callers)
            throw new SecurityException("Access denied: path is outside allowed roots");
        }

        // 5. Check denied block-list (applied after symlink resolution)
        String pathStr = resolved.toString().replace('\\', '/');
        for (Pattern p : deniedPatterns) {
            if (p.matcher(pathStr).matches()) {
                throw new SecurityException("Access denied: path is explicitly blocked");
            }
        }

        return resolved;
    }

    /** Returns {@code true} if the path falls under a configured read-only root. */
    public boolean isReadOnly(Path path) {
        return readonlyRoots.stream().anyMatch(path::startsWith);
    }

    /**
     * Throws {@link SecurityException} if the path is under a read-only root.
     *
     * @throws SecurityException if read-only
     */
    public void assertWritable(Path path) {
        if (isReadOnly(path)) {
            throw new SecurityException("Path is read-only: " + path);
        }
    }

    /**
     * Throws {@link UncheckedIOException} wrapping a {@link NoSuchFileException}
     * if the path does not exist.
     */
    public void assertExists(Path path) {
        if (!Files.exists(path)) {
            throw new UncheckedIOException(new NoSuchFileException(path.toString()));
        }
    }

    /** Immutable view of the configured allowed roots (used by FileService for breadcrumbs). */
    public List<Path> getAllowedRoots() {
        return Collections.unmodifiableList(allowedRoots);
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    /**
     * Resolves symlinks as far as possible.
     *
     * <p>For an <em>existing</em> path, {@link Path#toRealPath()} is used to follow
     * every symlink in the chain.
     *
     * <p>For a <em>non-existing</em> path (a create / upload target), we walk up the
     * ancestor chain until we reach an existing directory, call {@code toRealPath()} on
     * that, and then re-append the non-existing tail. This prevents a symlink-to-directory
     * ancestor from redirecting writes outside the allowed roots.
     */
    private Path resolveWithSymlinks(Path normalised) {
        try {
            if (Files.exists(normalised, LinkOption.NOFOLLOW_LINKS)) {
                // Existing path — follow all symlinks
                return normalised.toRealPath();
            }

            // Non-existing target: find deepest existing ancestor
            Path existing = normalised;
            while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                existing = existing.getParent();
            }

            if (existing == null) {
                // Nothing exists up the chain — just use the normalised path
                return normalised;
            }

            // Follow symlinks on the existing portion, then re-append the tail
            Path realAncestor = existing.toRealPath();
            Path tail = existing.relativize(normalised);
            return realAncestor.resolve(tail).normalize();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> normalisePaths(List<String> rawPaths, String label) {
        List<Path> result = new ArrayList<>();
        for (String raw : rawPaths) {
            try {
                Path p = Paths.get(raw).toAbsolutePath().normalize();
                result.add(p);
            } catch (Exception e) {
                log.warn("Ignoring invalid {} '{}': {}", label, raw, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Converts glob-style denied-path entries to {@link Pattern}s.
     * {@code *} matches one path segment (no slashes).
     * Example: {@code /home/*‌/.ssh} → {@code ^/home/[^/]+/\.ssh(/.*)?$}
     */
    private static List<Pattern> compileDeniedPatterns(List<String> deniedPaths) {
        List<Pattern> patterns = new ArrayList<>();
        for (String raw : deniedPaths) {
            // Normalise to forward-slashes for matching
            String normalised = raw.replace('\\', '/');
            // Escape dots, then replace * wildcard
            String regex = normalised
                    .replace(".", "\\.")
                    .replace("*", "[^/]+");
            patterns.add(Pattern.compile("^" + regex + "(/.*)?$"));
        }
        return patterns;
    }
}

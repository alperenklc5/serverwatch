package com.serverwatch.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.serverwatch.config.ServerWatchProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of registered Git repositories, backed by a JSON file at
 * {@code {base-path}/repos.json}.
 *
 * <p>All mutations are written atomically: the JSON is first written to a
 * {@code .tmp} file, then renamed over the real file to prevent partial writes.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap} for reads; writes synchronize on
 * {@code this} to keep the in-memory map and the file consistent.
 */
@Component
public class RepoRegistry {

    private static final Logger log = LoggerFactory.getLogger(RepoRegistry.class);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE =
            new TypeToken<Map<String, RepoConfig>>() {}.getType();

    private final Path registryFile;
    private final Map<String, RepoConfig> repos = new ConcurrentHashMap<>();

    public RepoRegistry(ServerWatchProperties properties) {
        Path basePath = Path.of(properties.getGit().getBasePath());
        this.registryFile = basePath.resolve("repos.json");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PostConstruct
    public void load() {
        try {
            Files.createDirectories(registryFile.getParent());
        } catch (IOException e) {
            log.warn("Could not create git base-path directory: {}", e.getMessage());
        }

        if (Files.exists(registryFile)) {
            try {
                String json = Files.readString(registryFile, StandardCharsets.UTF_8);
                Map<String, RepoConfig> loaded = GSON.fromJson(json, MAP_TYPE);
                if (loaded != null) {
                    repos.putAll(loaded);
                    log.info("Loaded {} repo(s) from registry at {}", repos.size(), registryFile);
                }
            } catch (IOException e) {
                log.error("Failed to load repo registry from {}: {}", registryFile, e.getMessage());
            }
        } else {
            log.info("No existing repo registry found at {} — starting fresh", registryFile);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Registers a new repository in the registry.
     *
     * @param name       human-readable display name
     * @param localPath  absolute path to the repository root on disk
     * @param remoteUrl  origin remote URL (may be null for local-only repos)
     * @return the newly created {@link RepoConfig} with a generated UUID
     */
    public synchronized RepoConfig register(String name, String localPath, String remoteUrl) {
        RepoConfig config = new RepoConfig(
                UUID.randomUUID().toString(),
                name,
                localPath,
                remoteUrl
        );
        repos.put(config.id(), config);
        persist();
        log.info("Registered repo '{}' (id={}) at {}", name, config.id(), localPath);
        return config;
    }

    /**
     * Removes a repository from the registry.
     * The actual files on disk are NOT deleted.
     *
     * @param repoId the UUID of the repository to remove
     * @throws IllegalArgumentException if no repo with that ID exists
     */
    public synchronized void unregister(String repoId) {
        RepoConfig removed = repos.remove(repoId);
        if (removed == null) {
            throw new IllegalArgumentException("Repository not found: " + repoId);
        }
        persist();
        log.info("Unregistered repo '{}' (id={})", removed.name(), repoId);
    }

    /**
     * Returns the {@link RepoConfig} for the given ID.
     *
     * @param repoId the repository UUID
     * @return an {@link Optional} containing the config, or empty if not found
     */
    public Optional<RepoConfig> get(String repoId) {
        return Optional.ofNullable(repos.get(repoId));
    }

    /**
     * Returns an unmodifiable snapshot of all registered repositories.
     *
     * @return list of all {@link RepoConfig} entries
     */
    public List<RepoConfig> getAll() {
        return List.copyOf(repos.values());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void persist() {
        Path tmp = registryFile.getParent().resolve("repos.json.tmp");
        try {
            String json = GSON.toJson(repos);
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, registryFile, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Failed to persist repo registry: {}", e.getMessage());
        }
    }

    // ── Inner model ───────────────────────────────────────────────────────────

    /**
     * Immutable configuration for a registered repository.
     * Uses a plain class (not record) for reliable Gson serialisation.
     */
    public static final class RepoConfig {

        private final String id;
        private final String name;
        private final String localPath;
        private final String remoteUrl;

        public RepoConfig(String id, String name, String localPath, String remoteUrl) {
            this.id        = id;
            this.name      = name;
            this.localPath = localPath;
            this.remoteUrl = remoteUrl;
        }

        public String id()        { return id; }
        public String name()      { return name; }
        public String localPath() { return localPath; }
        public String remoteUrl() { return remoteUrl; }
    }
}

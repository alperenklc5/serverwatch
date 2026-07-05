package com.serverwatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strongly-typed configuration properties for the serverwatch namespace.
 * Bound from the {@code serverwatch.*} keys in application.yml.
 */
@ConfigurationProperties(prefix = "serverwatch")
public class ServerWatchProperties {

    private final Collector collector = new Collector();
    private final Docker docker = new Docker();
    private final Alert alert = new Alert();
    private final Git git = new Git();
    private final Security security = new Security();
    private final FileManager filemanager = new FileManager();
    private final Terminal terminal = new Terminal();

    public Collector getCollector()       { return collector; }
    public Docker getDocker()             { return docker; }
    public Alert getAlert()               { return alert; }
    public Git getGit()                   { return git; }
    public Security getSecurity()         { return security; }
    public FileManager getFilemanager()   { return filemanager; }
    public Terminal getTerminal()         { return terminal; }

    public static class Collector {
        private long intervalMs = 2000;
        private int historyRetentionHours = 24;

        public long getIntervalMs()              { return intervalMs; }
        public void setIntervalMs(long v)        { this.intervalMs = v; }
        public int getHistoryRetentionHours()    { return historyRetentionHours; }
        public void setHistoryRetentionHours(int v) { this.historyRetentionHours = v; }
    }

    public static class Docker {
        private String socketPath = "unix:///var/run/docker.sock";

        public String getSocketPath()       { return socketPath; }
        public void setSocketPath(String v) { this.socketPath = v; }
    }

    public static class Alert {
        private int cooldownMinutes = 5;
        private long evaluationIntervalMs = 5000;

        public int getCooldownMinutes()             { return cooldownMinutes; }
        public void setCooldownMinutes(int v)       { this.cooldownMinutes = v; }
        public long getEvaluationIntervalMs()       { return evaluationIntervalMs; }
        public void setEvaluationIntervalMs(long v) { this.evaluationIntervalMs = v; }
    }

    public static class Git {
        private String basePath = "/opt/repos";

        public String getBasePath()       { return basePath; }
        public void setBasePath(String v) { this.basePath = v; }
    }

    public static class Security {
        private String jwtSecret = "change-me-in-production-this-is-dev-only";
        private long jwtExpirationMs = 86400000L;

        public String getJwtSecret()              { return jwtSecret; }
        public void setJwtSecret(String v)        { this.jwtSecret = v; }
        public long getJwtExpirationMs()          { return jwtExpirationMs; }
        public void setJwtExpirationMs(long v)    { this.jwtExpirationMs = v; }
    }

    public static class FileManager {
        /** Absolute paths users may browse. */
        private List<String> allowedRoots = new ArrayList<>(List.of("/home", "/opt", "/var/www", "/etc", "/tmp"));
        /** Subset of allowedRoots where writes are forbidden. */
        private List<String> readonlyRoots = new ArrayList<>(List.of("/etc"));
        /** Paths (glob-style * = one segment) that are always blocked. */
        private List<String> deniedPaths = new ArrayList<>(
                List.of("/etc/shadow", "/etc/sudoers", "/root/.ssh", "/home/*/.ssh"));
        /** Maximum file size accepted for upload, in MB. */
        private int maxUploadSizeMb = 500;
        /** Maximum file size that can be opened in the text editor, in MB. */
        private int maxEditableSizeMb = 10;

        public List<String> getAllowedRoots()           { return allowedRoots; }
        public void setAllowedRoots(List<String> v)    { this.allowedRoots = v; }
        public List<String> getReadonlyRoots()          { return readonlyRoots; }
        public void setReadonlyRoots(List<String> v)   { this.readonlyRoots = v; }
        public List<String> getDeniedPaths()            { return deniedPaths; }
        public void setDeniedPaths(List<String> v)     { this.deniedPaths = v; }
        public int getMaxUploadSizeMb()                 { return maxUploadSizeMb; }
        public void setMaxUploadSizeMb(int v)          { this.maxUploadSizeMb = v; }
        public int getMaxEditableSizeMb()               { return maxEditableSizeMb; }
        public void setMaxEditableSizeMb(int v)        { this.maxEditableSizeMb = v; }
    }

    public static class Terminal {
        /** Default shell executable. */
        private String shell = "/bin/bash";
        /** Shells users are permitted to request; others are rejected. */
        private List<String> availableShells = new ArrayList<>(List.of("/bin/bash", "/bin/sh", "/bin/zsh"));
        /** Default working directory for new sessions. */
        private String defaultCwd = System.getProperty("user.home");
        /** Extra environment variables injected into every shell. */
        private Map<String, String> env = new HashMap<>(Map.of("TERM", "xterm-256color", "LANG", "en_US.UTF-8"));
        /** Idle session timeout in minutes. */
        private int sessionTimeoutMinutes = 30;
        /** Maximum number of concurrent terminal sessions. */
        private int maxSessions = 10;
        /** Maximum output bytes buffered per session (for reconnect replay). */
        private int maxBufferBytes = 1_048_576; // 1 MB

        public String getShell()                        { return shell; }
        public void setShell(String v)                  { this.shell = v; }
        public List<String> getAvailableShells()        { return availableShells; }
        public void setAvailableShells(List<String> v)  { this.availableShells = v; }
        public String getDefaultCwd()                   { return defaultCwd; }
        public void setDefaultCwd(String v)             { this.defaultCwd = v; }
        public Map<String, String> getEnv()             { return env; }
        public void setEnv(Map<String, String> v)       { this.env = v; }
        public int getSessionTimeoutMinutes()            { return sessionTimeoutMinutes; }
        public void setSessionTimeoutMinutes(int v)     { this.sessionTimeoutMinutes = v; }
        public int getMaxSessions()                     { return maxSessions; }
        public void setMaxSessions(int v)               { this.maxSessions = v; }
        public int getMaxBufferBytes()                  { return maxBufferBytes; }
        public void setMaxBufferBytes(int v)            { this.maxBufferBytes = v; }
    }
}

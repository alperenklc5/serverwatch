package com.serverwatch.model.dto;

public record PermissionDTO(
        String permission,
        String label,
        String category,
        boolean granted
) {
    private static final java.util.Map<String, String[]> META = java.util.Map.ofEntries(
        java.util.Map.entry("TERMINAL_ACCESS",  new String[]{"Access web terminal",               "Terminal"}),
        java.util.Map.entry("FILES_VIEW",        new String[]{"View and browse files",             "Files"}),
        java.util.Map.entry("FILES_WRITE",       new String[]{"Create, edit and upload files",     "Files"}),
        java.util.Map.entry("FILES_DELETE",      new String[]{"Delete files and directories",      "Files"}),
        java.util.Map.entry("DOCKER_VIEW",       new String[]{"View containers and stats",         "Docker"}),
        java.util.Map.entry("DOCKER_CONTROL",    new String[]{"Start / stop / restart containers", "Docker"}),
        java.util.Map.entry("DOCKER_DELETE",     new String[]{"Remove containers",                 "Docker"}),
        java.util.Map.entry("GIT_VIEW",          new String[]{"View repos, commits, diffs",        "Git"}),
        java.util.Map.entry("GIT_WRITE",         new String[]{"Clone, pull, push, branch",         "Git"}),
        java.util.Map.entry("ALERTS_VIEW",       new String[]{"View alert rules and history",      "Alerts"}),
        java.util.Map.entry("ALERTS_MANAGE",     new String[]{"Create / edit / delete rules",      "Alerts"}),
        java.util.Map.entry("USER_MANAGEMENT",   new String[]{"Manage users and permissions",      "Administration"})
    );

    public static PermissionDTO of(String permission, boolean granted) {
        String[] meta = META.getOrDefault(permission, new String[]{permission, "Other"});
        return new PermissionDTO(permission, meta[0], meta[1], granted);
    }
}

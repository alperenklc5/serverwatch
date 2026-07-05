package com.serverwatch.service;

import com.serverwatch.config.ServerWatchProperties;
import com.serverwatch.model.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.file.attribute.PosixFilePermission.*;

/**
 * All filesystem operations for the file manager.
 *
 * <p>Every method validates the incoming path(s) through {@link PathValidator} before
 * touching the filesystem. Write operations are audit-logged at INFO level.
 */
@Slf4j
@Service
public class FileService {

    /** Directory names skipped during recursive search. */
    private static final Set<String> SEARCH_SKIP_DIRS = Set.of(
            ".git", "node_modules", ".svn", "__pycache__", "target",
            ".idea", ".gradle", "venv", ".venv", ".tox");

    /** Extension → MIME type fallback table for types not detected by the OS. */
    private static final Map<String, String> EXT_MIME = Map.ofEntries(
            Map.entry("ts",   "text/typescript"),
            Map.entry("tsx",  "text/typescript"),
            Map.entry("jsx",  "text/javascript"),
            Map.entry("json", "application/json"),
            Map.entry("yaml", "text/yaml"),
            Map.entry("yml",  "text/yaml"),
            Map.entry("xml",  "application/xml"),
            Map.entry("md",   "text/markdown"),
            Map.entry("sh",   "text/x-sh"),
            Map.entry("bash", "text/x-sh"),
            Map.entry("py",   "text/x-python"),
            Map.entry("java", "text/x-java"),
            Map.entry("go",   "text/x-go"),
            Map.entry("rs",   "text/x-rust"),
            Map.entry("toml", "text/toml"),
            Map.entry("env",  "text/plain"),
            Map.entry("log",  "text/plain"),
            Map.entry("conf", "text/plain"),
            Map.entry("cfg",  "text/plain"),
            Map.entry("ini",  "text/plain"),
            Map.entry("sql",  "text/x-sql"),
            Map.entry("css",  "text/css"),
            Map.entry("html", "text/html"),
            Map.entry("htm",  "text/html"),
            Map.entry("js",   "text/javascript")
    );

    private static final boolean POSIX =
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private final PathValidator pathValidator;
    private final ServerWatchProperties properties;

    public FileService(PathValidator pathValidator, ServerWatchProperties properties) {
        this.pathValidator = pathValidator;
        this.properties = properties;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BROWSING
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lists the contents of a directory, sorted directories-first then alphabetically.
     *
     * @param pathStr    absolute path string
     * @param showHidden include entries whose name starts with {@code .}
     */
    public DirectoryListingDTO listDirectory(String pathStr, boolean showHidden) {
        Path path = pathValidator.validateAndResolve(pathStr);

        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Not a directory: " + path.getFileName());
        }

        List<FileEntryDTO> entries;
        try (Stream<Path> stream = Files.list(path)) {
            entries = stream
                    .filter(p -> showHidden || !p.getFileName().toString().startsWith("."))
                    .sorted(Comparator
                            .<Path, Boolean>comparing(p -> !Files.isDirectory(p)) // dirs first
                            .thenComparing(p -> p.getFileName().toString(),
                                    String.CASE_INSENSITIVE_ORDER))
                    .map(p -> {
                        try {
                            return toFileEntry(p);
                        } catch (Exception e) {
                            log.debug("Skipping unreadable entry {}: {}", p, e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        long totalSize  = entries.stream().filter(e -> "FILE".equals(e.type())).mapToLong(FileEntryDTO::size).sum();
        int dirCount    = (int) entries.stream().filter(e -> "DIRECTORY".equals(e.type())).count();
        int fileCount   = (int) entries.stream().filter(e -> "FILE".equals(e.type())).count();
        String parent   = resolveParentPath(path);

        return new DirectoryListingDTO(
                path.toString(), parent, buildBreadcrumbs(path),
                entries, entries.size(), dirCount, fileCount,
                totalSize, pathValidator.isReadOnly(path));
    }

    /** Returns the configured allowed roots (for the UI's root selector). */
    public List<String> getAllowedRoots() {
        return pathValidator.getAllowedRoots().stream().map(Path::toString).toList();
    }

    /** Returns breadcrumb navigation for the given path. */
    public List<PathBreadcrumb> getBreadcrumbs(String pathStr) {
        Path path = pathValidator.validateAndResolve(pathStr);
        return buildBreadcrumbs(path);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // READ
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Reads a text file and returns its content with metadata.
     * Binary files are detected and returned with {@code binary=true} and {@code content=null}.
     *
     * @throws IllegalArgumentException if the file exceeds the configured editable size limit
     */
    public FileContentDTO readFile(String pathStr) {
        Path path = pathValidator.validateAndResolve(pathStr);
        pathValidator.assertExists(path);

        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException("Cannot read a directory as a file");
        }

        long size = fileSize(path);
        long maxBytes = (long) properties.getFilemanager().getMaxEditableSizeMb() * 1024 * 1024;

        if (size > maxBytes) {
            throw new IllegalArgumentException(
                    "File too large to edit (%.1f MB). Use the download endpoint instead."
                            .formatted(size / 1_048_576.0));
        }

        try {
            // Binary detection: sample first 8 KB
            int sampleLen = (int) Math.min(size, 8192);
            boolean binary = false;
            if (sampleLen > 0) {
                try (InputStream is = Files.newInputStream(path)) {
                    byte[] sample = is.readNBytes(sampleLen);
                    int nonPrintable = 0;
                    for (byte b : sample) {
                        int c = b & 0xFF;
                        if (c == 0) { binary = true; break; }
                        if (c < 8 || (c > 13 && c < 32 && c != 27)) nonPrintable++;
                    }
                    if (!binary && nonPrintable > sampleLen * 0.3) binary = true;
                }
            }

            if (binary) {
                return new FileContentDTO(path.toString(), null, null, null, size, 0, true);
            }

            // Try UTF-8, fall back to ISO-8859-1
            String encoding = "UTF-8";
            String content;
            try {
                content = Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                content  = Files.readString(path, StandardCharsets.ISO_8859_1);
                encoding = "ISO-8859-1";
            }

            // Detect line ending
            long crlfCount = content.chars().filter(c -> c == '\r').count();
            String lineEnding = crlfCount > 0 ? "CRLF" : "LF";
            int lineCount = content.split("\n", -1).length;

            return new FileContentDTO(path.toString(), content, encoding, lineEnding, size, lineCount, false);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WRITE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Atomically writes text content to a file, creating it if it does not exist.
     * An existing file is backed up to {@code <filename>.sw-backup} before writing.
     *
     * @param pathStr  target file path
     * @param content  text content
     * @param encoding charset name; defaults to UTF-8 if null
     */
    public FileEntryDTO writeFile(String pathStr, String content, String encoding) {
        Path path = pathValidator.validateAndResolve(pathStr);
        pathValidator.assertWritable(path);

        Charset charset = charset(encoding);
        Path tmp = path.resolveSibling(path.getFileName() + ".sw-tmp");

        // Backup existing file
        if (Files.exists(path)) {
            Path backup = path.resolveSibling(path.getFileName() + ".sw-backup");
            try {
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.warn("Backup failed for {}: {}", path, e.getMessage());
            }
        }

        try {
            Files.writeString(tmp, content, charset);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            throw new UncheckedIOException(e);
        }

        auditLog("WRITE", path);
        return toFileEntry(path);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CREATE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a new regular file.
     *
     * @param parentPath parent directory
     * @param name       new file name (must not contain path separators or {@code ..})
     * @param content    optional initial content
     */
    public FileEntryDTO createFile(String parentPath, String name, String content) {
        String sanitised = sanitiseName(name);
        Path parent = pathValidator.validateAndResolve(parentPath);
        pathValidator.assertWritable(parent);

        Path target = parent.resolve(sanitised);
        // Re-validate: the resolved target must also be within allowed roots
        pathValidator.validateAndResolve(target.toString());

        try {
            Files.createFile(target);
            if (content != null && !content.isEmpty()) {
                Files.writeString(target, content, StandardCharsets.UTF_8);
            }
        } catch (FileAlreadyExistsException e) {
            throw new UncheckedIOException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        auditLog("CREATE_FILE", target);
        return toFileEntry(target);
    }

    /**
     * Creates a new directory (does not create intermediate parents).
     *
     * @param parentPath parent directory
     * @param name       new directory name
     */
    public FileEntryDTO createDirectory(String parentPath, String name) {
        String sanitised = sanitiseName(name);
        Path parent = pathValidator.validateAndResolve(parentPath);
        pathValidator.assertWritable(parent);

        Path target = parent.resolve(sanitised);
        pathValidator.validateAndResolve(target.toString());

        try {
            Files.createDirectory(target);
        } catch (FileAlreadyExistsException e) {
            throw new UncheckedIOException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        auditLog("CREATE_DIR", target);
        return toFileEntry(target);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Deletes a file or directory.
     *
     * @param pathStr   target path
     * @param recursive required to be {@code true} for non-empty directories
     * @throws IllegalStateException if directory is non-empty and {@code recursive} is false
     */
    public void delete(String pathStr, boolean recursive) {
        Path path = pathValidator.validateAndResolve(pathStr);
        pathValidator.assertWritable(path);
        pathValidator.assertExists(path);

        try {
            if (Files.isDirectory(path)) {
                if (!recursive) {
                    try (Stream<Path> s = Files.list(path)) {
                        if (s.findAny().isPresent()) {
                            throw new IllegalStateException(
                                    "Directory is not empty. Pass recursive=true to delete.");
                        }
                    }
                    Files.delete(path);
                } else {
                    // Walk reverse-post-order: files before their parent dirs
                    try (Stream<Path> walk = Files.walk(path)) {
                        walk.sorted(Comparator.reverseOrder())
                                .forEach(p -> {
                                    try {
                                        Files.delete(p);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                });
                    }
                }
            } else {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        auditLog("DELETE" + (recursive ? "_RECURSIVE" : ""), path);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MOVE / COPY
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Moves or renames a file or directory atomically (falls back to non-atomic if unsupported).
     */
    public FileEntryDTO move(String sourceStr, String targetStr) {
        Path source = pathValidator.validateAndResolve(sourceStr);
        Path target = pathValidator.validateAndResolve(targetStr);
        pathValidator.assertWritable(source);
        assertParentWritable(target);

        try {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        auditLog("MOVE " + source + " → " + target, target);
        return toFileEntry(target);
    }

    /**
     * Copies a file or directory tree.
     */
    public FileEntryDTO copy(String sourceStr, String targetStr) {
        Path source = pathValidator.validateAndResolve(sourceStr);
        Path target = pathValidator.validateAndResolve(targetStr);
        assertParentWritable(target);

        try {
            if (Files.isDirectory(source)) {
                Files.walkFileTree(source, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) throws IOException {
                        Files.createDirectories(target.resolve(source.relativize(dir)));
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                        Files.copy(file, target.resolve(source.relativize(file)),
                                StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.copy(source, target,
                        StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        auditLog("COPY " + source + " → " + target, target);
        return toFileEntry(target);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PERMISSIONS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Changes POSIX file permissions.
     *
     * @param pathStr         target path
     * @param octalPermissions 3 or 4 octal digits, e.g. {@code 755} or {@code 0644}
     * @throws UnsupportedOperationException on non-POSIX systems (Windows)
     */
    public FileEntryDTO chmod(String pathStr, String octalPermissions) {
        if (!POSIX) {
            throw new UnsupportedOperationException(
                    "POSIX permissions are not supported on this operating system");
        }
        if (octalPermissions == null || !octalPermissions.matches("[0-7]{3,4}")) {
            throw new IllegalArgumentException(
                    "Invalid permission format — use 3 or 4 octal digits, e.g. '755' or '0644'");
        }

        Path path = pathValidator.validateAndResolve(pathStr);
        pathValidator.assertWritable(path);
        pathValidator.assertExists(path);

        // Use last 3 digits (ignore setuid/setgid/sticky for safety)
        String three = octalPermissions.length() == 4
                ? octalPermissions.substring(1) : octalPermissions;
        Set<PosixFilePermission> perms = octalToPermissions(three);

        try {
            Files.setPosixFilePermissions(path, perms);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        auditLog("CHMOD " + octalPermissions, path);
        return toFileEntry(path);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UPLOAD
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Saves a multipart-uploaded file to the specified directory.
     * If a file with the same name already exists, a numeric suffix is appended
     * (e.g. {@code report (1).pdf}).
     *
     * @throws IllegalArgumentException if the upload exceeds the configured size limit
     */
    public UploadResponseDTO upload(String targetDirStr, MultipartFile file) {
        Path targetDir = pathValidator.validateAndResolve(targetDirStr);
        pathValidator.assertWritable(targetDir);

        long maxBytes = (long) properties.getFilemanager().getMaxUploadSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "Upload exceeds the %d MB limit".formatted(
                            properties.getFilemanager().getMaxUploadSizeMb()));
        }

        String filename = sanitiseFilename(file.getOriginalFilename());
        Path targetPath = resolveUploadPath(targetDir, filename);

        // Final safety check on the resolved upload target
        pathValidator.validateAndResolve(targetPath.toString());

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String mime = detectMimeType(targetPath);
        auditLog("UPLOAD", targetPath);
        return new UploadResponseDTO(targetPath.toString(), targetPath.getFileName().toString(),
                fileSize(targetPath), mime);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DOWNLOAD
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Prepares a streaming download response for a file or directory.
     * Directories are automatically zipped on the fly.
     */
    public DownloadResponse prepareDownload(String pathStr) {
        Path path = pathValidator.validateAndResolve(pathStr);
        pathValidator.assertExists(path);

        if (Files.isDirectory(path)) {
            String zipName = path.getFileName() + ".zip";
            StreamingResponseBody body = out -> zipDirectory(path, out);
            return new DownloadResponse(zipName, "application/zip", -1L, body);
        }

        long size = fileSize(path);
        String mime = detectMimeType(path);
        String filename = path.getFileName().toString();
        StreamingResponseBody body = out -> {
            try (InputStream in = Files.newInputStream(path)) {
                in.transferTo(out);
            }
        };
        return new DownloadResponse(filename, mime, size, body);
    }

    /** Immutable response descriptor returned by {@link #prepareDownload}. */
    public record DownloadResponse(String filename, String contentType,
                                   long contentLength, StreamingResponseBody body) {}

    // ══════════════════════════════════════════════════════════════════════════
    // SEARCH
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Searches for files whose names contain {@code query} (case-insensitive) under
     * {@code rootPathStr} up to 5 directory levels deep.
     * Known heavyweight directories ({@code .git}, {@code node_modules}, …) are skipped.
     *
     * @param maxResults capped at 200
     */
    public List<FileEntryDTO> search(String rootPathStr, String query, int maxResults) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }
        Path root = pathValidator.validateAndResolve(rootPathStr);
        String lowerQuery = query.toLowerCase();
        int limit = Math.min(Math.max(maxResults, 1), 200);
        List<FileEntryDTO> results = new ArrayList<>();

        try {
            Files.walkFileTree(root, Set.of(), 5, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                    if (results.size() >= limit) return FileVisitResult.TERMINATE;
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (SEARCH_SKIP_DIRS.contains(name) && !dir.equals(root)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
                    if (results.size() >= limit) return FileVisitResult.TERMINATE;
                    if (file.getFileName().toString().toLowerCase().contains(lowerQuery)) {
                        try { results.add(toFileEntry(file)); } catch (Exception ignored) { }
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE; // skip permission-denied entries
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return results;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DISK USAGE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Calculates total size, file count, and subdirectory count for a directory.
     */
    public Map<String, Long> getDirectorySize(String pathStr) {
        Path path = pathValidator.validateAndResolve(pathStr);
        pathValidator.assertExists(path);

        long[] size = {0}, files = {0}, dirs = {0};
        try (Stream<Path> walk = Files.walk(path)) {
            walk.forEach(p -> {
                if (Files.isRegularFile(p)) {
                    size[0]  += fileSize(p);
                    files[0] += 1;
                } else if (Files.isDirectory(p) && !p.equals(path)) {
                    dirs[0]  += 1;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return Map.of("size", size[0], "fileCount", files[0], "dirCount", dirs[0]);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ══════════════════════════════════════════════════════════════════════════

    /** Converts a filesystem path to the public-facing DTO with all metadata. */
    public FileEntryDTO toFileEntry(Path path) {
        try {
            boolean isSymlink = Files.isSymbolicLink(path);
            String type;
            if (isSymlink) {
                type = "SYMLINK";
            } else if (Files.isDirectory(path)) {
                type = "DIRECTORY";
            } else {
                type = "FILE";
            }

            BasicFileAttributes basic = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

            String permissions = "", permissionsNumeric = "", owner = "", group = "";
            if (POSIX) {
                try {
                    PosixFileAttributes posix = Files.readAttributes(
                            path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    permissions        = PosixFilePermissions.toString(posix.permissions());
                    permissionsNumeric = permissionsToOctal(posix.permissions());
                    owner = posix.owner().getName();
                    group = posix.group().getName();
                } catch (Exception e) {
                    log.trace("Could not read POSIX attrs for {}: {}", path, e.getMessage());
                }
            }

            long size = "FILE".equals(type) ? basic.size() : 0L;
            String name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
            String mime = "FILE".equals(type) ? detectMimeType(path) : null;
            long editLimit = (long) properties.getFilemanager().getMaxEditableSizeMb() * 1024 * 1024;
            boolean editable = "FILE".equals(type) && isTextMime(mime) && size <= editLimit;

            String symlinkTarget = null;
            if (isSymlink) {
                try {
                    symlinkTarget = Files.readSymbolicLink(path).toString();
                } catch (IOException e) {
                    symlinkTarget = "[unresolvable]";
                }
            }

            return new FileEntryDTO(
                    name,
                    path.toString(),
                    buildRelativePath(path),
                    type,
                    size,
                    permissions,
                    permissionsNumeric,
                    owner,
                    group,
                    basic.lastModifiedTime().toInstant(),
                    basic.creationTime().toInstant(),
                    name.startsWith("."),
                    Files.isReadable(path),
                    Files.isWritable(path),
                    Files.isExecutable(path),
                    mime,
                    editable,
                    symlinkTarget
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String detectMimeType(Path path) {
        try {
            String detected = Files.probeContentType(path);
            if (detected != null) return detected;
        } catch (IOException ignored) { }

        String name = path.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        return EXT_MIME.getOrDefault(name.substring(dot + 1), "application/octet-stream");
    }

    private static boolean isTextMime(String mime) {
        if (mime == null) return false;
        return mime.startsWith("text/") || mime.equals("application/json")
                || mime.equals("application/xml") || mime.equals("application/javascript");
    }

    private List<PathBreadcrumb> buildBreadcrumbs(Path path) {
        Path root = pathValidator.getAllowedRoots().stream()
                .filter(path::startsWith)
                .min(Comparator.comparingInt(r -> r.getNameCount())) // deepest matching root
                .orElse(path.getRoot());

        List<PathBreadcrumb> crumbs = new ArrayList<>();
        String rootLabel = root.getFileName() != null ? root.getFileName().toString() : root.toString();
        crumbs.add(new PathBreadcrumb(rootLabel, root.toString()));

        if (!root.equals(path)) {
            Path rel = root.relativize(path);
            Path current = root;
            for (int i = 0; i < rel.getNameCount(); i++) {
                current = current.resolve(rel.getName(i));
                crumbs.add(new PathBreadcrumb(rel.getName(i).toString(), current.toString()));
            }
        }
        return crumbs;
    }

    private String buildRelativePath(Path path) {
        return pathValidator.getAllowedRoots().stream()
                .filter(path::startsWith)
                .min(Comparator.comparingInt(r -> r.getNameCount()))
                .map(root -> root.relativize(path).toString().replace('\\', '/'))
                .orElse(path.toString());
    }

    private String resolveParentPath(Path path) {
        Path parent = path.getParent();
        if (parent == null) return null;
        boolean allowed = pathValidator.getAllowedRoots().stream()
                .anyMatch(r -> parent.startsWith(r) || parent.equals(r));
        return allowed ? parent.toString() : null;
    }

    private static String sanitiseName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        String cleaned = name.replace("\0", "").trim();
        if (cleaned.contains("/") || cleaned.contains("\\")
                || cleaned.equals("..") || cleaned.equals(".")) {
            throw new IllegalArgumentException("Name contains illegal characters: " + name);
        }
        return cleaned;
    }

    private static String sanitiseFilename(String original) {
        if (original == null || original.isBlank()) return "upload";
        // Strip any path components the browser might include
        Path p = Paths.get(original).getFileName();
        String name = p != null ? p.toString() : original;
        // Replace chars that are unsafe on common filesystems
        name = name.replaceAll("[^a-zA-Z0-9._\\-() ]", "_").trim();
        return name.isEmpty() ? "upload" : name;
    }

    /** Resolves an upload target path, appending a numeric suffix if the name is taken. */
    private static Path resolveUploadPath(Path dir, String filename) {
        Path target = dir.resolve(filename);
        if (!Files.exists(target)) return target;

        String base = filename, ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot > 0) { base = filename.substring(0, dot); ext = filename.substring(dot); }

        for (int i = 1; i < 1000; i++) {
            target = dir.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(target)) return target;
        }
        return target; // last resort — will overwrite
    }

    private void zipDirectory(Path dir, OutputStream out) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            Path parent = dir.getParent() != null ? dir.getParent() : dir;
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                    String entry = parent.relativize(file).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entry));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult preVisitDirectory(Path sub, BasicFileAttributes a) throws IOException {
                    if (!sub.equals(dir)) {
                        String entry = parent.relativize(sub).toString().replace('\\', '/') + "/";
                        zos.putNextEntry(new ZipEntry(entry));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static Set<PosixFilePermission> octalToPermissions(String three) {
        Set<PosixFilePermission> perms = new HashSet<>();
        int o = Character.getNumericValue(three.charAt(0));
        int g = Character.getNumericValue(three.charAt(1));
        int ot = Character.getNumericValue(three.charAt(2));
        if ((o & 4) != 0) perms.add(OWNER_READ);    if ((o & 2) != 0) perms.add(OWNER_WRITE);  if ((o & 1) != 0) perms.add(OWNER_EXECUTE);
        if ((g & 4) != 0) perms.add(GROUP_READ);    if ((g & 2) != 0) perms.add(GROUP_WRITE);  if ((g & 1) != 0) perms.add(GROUP_EXECUTE);
        if ((ot & 4) != 0) perms.add(OTHERS_READ);  if ((ot & 2) != 0) perms.add(OTHERS_WRITE); if ((ot & 1) != 0) perms.add(OTHERS_EXECUTE);
        return perms;
    }

    private static String permissionsToOctal(Set<PosixFilePermission> perms) {
        int o = (perms.contains(OWNER_READ) ? 4 : 0) | (perms.contains(OWNER_WRITE) ? 2 : 0) | (perms.contains(OWNER_EXECUTE) ? 1 : 0);
        int g = (perms.contains(GROUP_READ) ? 4 : 0) | (perms.contains(GROUP_WRITE) ? 2 : 0) | (perms.contains(GROUP_EXECUTE) ? 1 : 0);
        int ot = (perms.contains(OTHERS_READ) ? 4 : 0) | (perms.contains(OTHERS_WRITE) ? 2 : 0) | (perms.contains(OTHERS_EXECUTE) ? 1 : 0);
        return "" + o + g + ot;
    }

    private static Charset charset(String encoding) {
        try {
            return encoding != null && !encoding.isBlank()
                    ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static long fileSize(Path path) {
        try { return Files.size(path); } catch (IOException e) { return 0L; }
    }

    private void assertParentWritable(Path path) {
        Path parent = path.getParent();
        if (parent != null) pathValidator.assertWritable(parent);
    }

    private void auditLog(String operation, Path path) {
        log.info("[AUDIT] {} | path={}", operation, path);
    }
}

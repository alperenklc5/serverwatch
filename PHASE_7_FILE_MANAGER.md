# Phase 7 — File Manager (WinSCP-like)

## Objective
Implement a full file management system that lets users browse the VPS filesystem, upload/download files, edit text files, create/delete/rename/move files and directories, and manage permissions — all through REST APIs. This turns ServerWatch into a complete replacement for WinSCP and FileZilla.

## Prerequisites
- Phase 6 completed — Alert engine operational
- All previous phases functional

## Security Warning
This phase gives ServerWatch full filesystem access. Path traversal prevention is CRITICAL. Every file operation must validate that the target path is within the allowed root directories. Never trust user-provided paths directly.

## Configuration

Add to `application.yml`:
```yaml
serverwatch:
  filemanager:
    # Allowed root directories users can browse
    allowed-roots:
      - /home
      - /opt
      - /var/www
      - /etc
      - /tmp
    # Read-only roots (browse but can't modify)
    readonly-roots:
      - /etc
    # Maximum upload size
    max-upload-size-mb: 500
    # Maximum file size for text editor (larger files can be downloaded only)
    max-editable-size-mb: 10
    # Deny access to these paths even under allowed roots
    denied-paths:
      - /etc/shadow
      - /etc/sudoers
      - /root/.ssh
      - /home/*/.ssh
```

## Step 1: DTOs

### FileEntryDTO.java (record)
```java
// - name (String) — file/directory name
// - path (String) — absolute path
// - relativePath (String) — path relative to the current browse root
// - type (String) — "FILE", "DIRECTORY", "SYMLINK"
// - size (long) — bytes; 0 for directories
// - permissions (String) — POSIX permissions like "rwxr-xr-x"
// - permissionsNumeric (String) — octal like "755"
// - owner (String) — file owner username
// - group (String) — file group
// - modifiedAt (Instant)
// - createdAt (Instant)
// - isHidden (boolean) — starts with "."
// - isReadable (boolean)
// - isWritable (boolean)
// - isExecutable (boolean)
// - mimeType (String) — for files, e.g., "text/plain", "application/json"
// - isEditable (boolean) — true for text files under max-editable-size
// - symlinkTarget (String) — for symlinks
```

### DirectoryListingDTO.java (record)
```java
// - path (String) — current directory
// - parentPath (String) — parent path (null for root)
// - breadcrumbs (List<PathBreadcrumb>) — for UI navigation
// - entries (List<FileEntryDTO>)
// - totalCount (int)
// - directoryCount (int)
// - fileCount (int)
// - totalSize (long) — sum of file sizes
// - isReadOnly (boolean) — under a readonly root
```

### PathBreadcrumb.java (record)
```java
// - name (String) — segment name
// - path (String) — full path up to this segment
```

### FileContentDTO.java (record)
```java
// - path (String)
// - content (String) — text content
// - encoding (String) — "UTF-8", "ISO-8859-1", etc.
// - lineEnding (String) — "LF", "CRLF"
// - size (long)
// - lineCount (int)
// - isBinary (boolean) — if true, content will be null
```

### FileOperationRequest.java (record)
```java
// For create:
// - path (String, required) — parent directory
// - name (String, required) — new file/dir name
// - type (String, required) — "FILE" or "DIRECTORY"
// - content (String, optional) — initial content for files
//
// For rename/move:
// - sourcePath (String, required)
// - targetPath (String, required)
//
// For chmod:
// - path (String, required)
// - permissions (String, required) — octal like "755"
//
// For write:
// - path (String, required)
// - content (String, required)
// - encoding (String, default "UTF-8")
```

### UploadResponseDTO.java (record)
```java
// - path (String) — final saved path
// - filename (String)
// - size (long)
// - mimeType (String)
```

## Step 2: PathValidator

### PathValidator.java
The heart of security. Every file operation goes through this first.

```java
@Component
public class PathValidator {

    private final List<Path> allowedRoots;
    private final List<Path> readonlyRoots;
    private final List<Pattern> deniedPatterns;

    @PostConstruct
    public void init() {
        // Normalize all configured roots to absolute paths
        // Convert denied paths with wildcards to regex patterns
        // Example: "/home/*/.ssh" → "^/home/[^/]+/\\.ssh(/.*)?$"
    }

    public Path validateAndResolve(String userPath) {
        // 1. Reject null, empty, or paths containing null bytes
        // 2. Normalize to absolute path
        // 3. Follow symlinks and check the REAL path (not the symlink path)
        //    to prevent symlink attacks pointing outside allowed roots
        // 4. Check that resolved path starts with at least one allowed root
        // 5. Check against denied patterns
        // 6. If all pass, return the resolved Path
        //
        // Throw SecurityException with clear message on any failure
    }

    public boolean isReadOnly(Path path) {
        // Check if path is under any readonlyRoot
    }

    public void assertWritable(Path path) {
        if (isReadOnly(path)) {
            throw new SecurityException("Path is read-only: " + path);
        }
    }

    public void assertExists(Path path) {
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Path does not exist: " + path);
        }
    }
}
```

**Critical:** Use `Files.walk()`, `Files.readSymbolicLink()`, and `Path.toRealPath()` — NEVER string concatenation on paths. Java's `Path` API handles OS differences.

## Step 3: FileService

### FileService.java

```java
@Service
public class FileService {

    private final PathValidator pathValidator;
    private final ServerWatchProperties properties;

    // ====== BROWSING ======

    public DirectoryListingDTO listDirectory(String pathStr, boolean showHidden) {
        Path path = pathValidator.validateAndResolve(pathStr);

        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Not a directory: " + path);
        }

        try (Stream<Path> stream = Files.list(path)) {
            List<FileEntryDTO> entries = stream
                .filter(p -> showHidden || !p.getFileName().toString().startsWith("."))
                .sorted(Comparator
                    .comparing((Path p) -> !Files.isDirectory(p)) // dirs first
                    .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .map(this::toFileEntry)
                .toList();

            // Build breadcrumbs, calculate counts and total size
            // Return DirectoryListingDTO
        }
    }

    private FileEntryDTO toFileEntry(Path path) {
        // Read BasicFileAttributes for size, times, type
        // Read PosixFileAttributes for permissions, owner, group (Linux only)
        // On Windows, use DosFileAttributes and skip POSIX fields
        //
        // Detect mimeType via Files.probeContentType(path) with a fallback
        // for common types not detected (jsx, ts, yaml, etc.)
        //
        // Determine isEditable: text mime type AND size <= max-editable-size
    }

    // ====== READ FILE ======

    public FileContentDTO readFile(String pathStr) {
        Path path = pathValidator.validateAndResolve(pathStr);
        pathValidator.assertExists(path);

        long size = Files.size(path);
        long maxSize = properties.getFilemanager().getMaxEditableSizeMb() * 1024 * 1024L;

        if (size > maxSize) {
            throw new IllegalArgumentException(
                "File too large to edit (%.1f MB). Download it instead.".formatted(size / 1024.0 / 1024.0)
            );
        }

        // Detect if binary — read first 8KB, check for null bytes or high ratio of non-printable chars
        // If binary, return DTO with content=null, isBinary=true
        //
        // If text:
        //   Detect encoding — try UTF-8 first, fall back to ISO-8859-1
        //   Detect line ending — count \r\n vs \n
        //   Read full content, count lines
        //   Return FileContentDTO
    }

    // ====== WRITE FILE ======

    public FileEntryDTO writeFile(String pathStr, String content, String encoding) {
        Path path = pathValidator.validateAndResolve(pathStr);
        pathValidator.assertWritable(path);

        // If file exists, back it up to path + ".sw-backup" (single backup, overwrite)
        // Write content with specified encoding
        // Atomic write: write to path + ".tmp" first, then move
        // Return updated FileEntryDTO
    }

    // ====== CREATE ======

    public FileEntryDTO createFile(String parentPath, String name, String content) {
        Path parent = pathValidator.validateAndResolve(parentPath);
        pathValidator.assertWritable(parent);

        // Sanitize name: no /, \, .., null bytes
        // Path target = parent.resolve(name);
        // Validate that target is still within allowed roots
        // Files.createFile(target)
        // If content provided, write it
        // Return FileEntryDTO
    }

    public FileEntryDTO createDirectory(String parentPath, String name) {
        // Same validation as createFile
        // Files.createDirectory(target)
    }

    // ====== DELETE ======

    public void delete(String pathStr, boolean recursive) {
        Path path = pathValidator.validateAndResolve(pathStr);
        pathValidator.assertWritable(path);

        if (Files.isDirectory(path)) {
            if (!recursive) {
                // Check if empty, throw if not
                try (Stream<Path> stream = Files.list(path)) {
                    if (stream.findAny().isPresent()) {
                        throw new IllegalStateException("Directory not empty. Use recursive=true.");
                    }
                }
                Files.delete(path);
            } else {
                // Walk in reverse order (files before parents)
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); }
                            catch (IOException e) { throw new UncheckedIOException(e); }
                        });
                }
            }
        } else {
            Files.delete(path);
        }
    }

    // ====== RENAME / MOVE ======

    public FileEntryDTO move(String sourcePathStr, String targetPathStr) {
        Path source = pathValidator.validateAndResolve(sourcePathStr);
        Path target = pathValidator.validateAndResolve(targetPathStr);
        pathValidator.assertWritable(source);
        pathValidator.assertWritable(target.getParent());

        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        return toFileEntry(target);
    }

    public FileEntryDTO copy(String sourcePathStr, String targetPathStr) {
        Path source = pathValidator.validateAndResolve(sourcePathStr);
        Path target = pathValidator.validateAndResolve(targetPathStr);
        pathValidator.assertWritable(target.getParent());

        if (Files.isDirectory(source)) {
            // Walk source, copy each file/dir preserving structure
        } else {
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
        return toFileEntry(target);
    }

    // ====== PERMISSIONS ======

    public FileEntryDTO chmod(String pathStr, String octalPermissions) {
        Path path = pathValidator.validateAndResolve(pathStr);
        pathValidator.assertWritable(path);

        // Validate octal string: exactly 3 digits, each 0-7
        // Convert to Set<PosixFilePermission>
        //   Digit 1 (owner): 4=r, 2=w, 1=x
        //   Digit 2 (group): 4=r, 2=w, 1=x
        //   Digit 3 (other): 4=r, 2=w, 1=x
        //
        // Files.setPosixFilePermissions(path, permissions)
        // Note: Windows doesn't support POSIX permissions — return 501 Not Implemented
    }

    // ====== UPLOAD / DOWNLOAD ======

    public UploadResponseDTO upload(String targetDirStr, MultipartFile file) {
        Path targetDir = pathValidator.validateAndResolve(targetDirStr);
        pathValidator.assertWritable(targetDir);

        // Sanitize filename from multipart
        String filename = sanitizeFilename(file.getOriginalFilename());
        Path targetPath = targetDir.resolve(filename);

        // Check target is still within allowed roots
        // Check size against max-upload-size
        // If file exists, append counter: file.txt → file (1).txt

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return new UploadResponseDTO(...);
    }

    public Resource download(String pathStr) {
        Path path = pathValidator.validateAndResolve(pathStr);
        pathValidator.assertExists(path);

        if (Files.isDirectory(path)) {
            // Create a temp ZIP of the directory
            // Return zip as Resource
            // Delete temp on cleanup (schedule for after response)
        }

        return new FileSystemResource(path);
    }

    // ====== SEARCH ======

    public List<FileEntryDTO> search(String rootPathStr, String query, int maxResults) {
        Path root = pathValidator.validateAndResolve(rootPathStr);

        // Files.walk with depth limit (e.g., 5 levels)
        // Case-insensitive contains match on filename
        // Skip hidden dirs like .git, node_modules unless explicitly requested
        // Cap at maxResults, break early
    }

    // ====== DISK USAGE ======

    public Map<String, Long> getDirectorySize(String pathStr) {
        Path path = pathValidator.validateAndResolve(pathStr);

        // Files.walk, sum sizes of regular files
        // Return {size, fileCount, dirCount}
    }
}
```

## Step 4: REST Controller

### FileController.java

```
# Browsing
GET    /api/files/list                         → DirectoryListingDTO
       Query: path=/home/user, showHidden=false

GET    /api/files/roots                        → List<String> (allowed roots)

GET    /api/files/breadcrumbs                  → List<PathBreadcrumb>
       Query: path=/home/user/projects

# Reading
GET    /api/files/read                         → FileContentDTO
       Query: path=/home/user/file.txt

GET    /api/files/download                     → binary stream
       Query: path=/home/user/file.zip
       Response: Content-Disposition: attachment; filename=...

# Writing
POST   /api/files/write                        → FileEntryDTO
       Body: {path, content, encoding}

# Create
POST   /api/files/create                       → FileEntryDTO
       Body: {path (parent), name, type: "FILE"|"DIRECTORY", content}

# Delete
DELETE /api/files                              → 204
       Query: path=..., recursive=true/false

# Move / Copy / Rename
POST   /api/files/move                         → FileEntryDTO
       Body: {sourcePath, targetPath}
POST   /api/files/copy                         → FileEntryDTO
       Body: {sourcePath, targetPath}

# Upload
POST   /api/files/upload                       → UploadResponseDTO
       Multipart: file, targetPath (form field)

# Permissions
POST   /api/files/chmod                        → FileEntryDTO
       Body: {path, permissions: "755"}

# Search
GET    /api/files/search                       → List<FileEntryDTO>
       Query: root=/home, query=config, maxResults=50

# Directory size
GET    /api/files/size                         → {size, fileCount, dirCount}
       Query: path=/var/log
```

## Step 5: Multipart Configuration

Add to `application.yml`:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB
      enabled: true
```

## Step 6: Exception Handling

Add to `GlobalExceptionHandler.java`:
- `SecurityException` → 403 Forbidden
- `FileNotFoundException` → 404 Not Found
- `FileAlreadyExistsException` → 409 Conflict
- `AccessDeniedException` → 403 Forbidden
- `IOException` → 500 with sanitized message

**Never expose full paths in error messages to unauthenticated users.** (After Phase 9 adds auth, admins can see full paths.)

## Step 7: Audit Logging

Every write operation (create, delete, move, chmod, upload, write) is logged with:
- Timestamp
- Operation type
- Path(s)
- User (once auth is added in Phase 9)
- Result (success/failure)

Add a simple `AuditLog` entity if you want DB-backed audit trail, or just log to a dedicated audit log file with a Logback appender.

## Step 8: Streaming Large Downloads

For large files, use `StreamingResponseBody` instead of loading into memory:
```java
@GetMapping("/download")
public ResponseEntity<StreamingResponseBody> download(@RequestParam String path) {
    Path filePath = pathValidator.validateAndResolve(path);

    StreamingResponseBody stream = out -> {
        try (InputStream in = Files.newInputStream(filePath)) {
            in.transferTo(out);
        }
    };

    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"" + filePath.getFileName() + "\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(Files.size(filePath))
        .body(stream);
}
```

## Acceptance Criteria
- [ ] `GET /api/files/list?path=/home` returns directory contents with all metadata
- [ ] `GET /api/files/read?path=...` returns file content for text files
- [ ] Binary files are detected and return `isBinary=true` with null content
- [ ] `POST /api/files/write` writes content with atomic rename and backup
- [ ] `POST /api/files/create` creates files and directories
- [ ] `DELETE /api/files` deletes files (or dirs with recursive=true)
- [ ] `POST /api/files/move` renames/moves files
- [ ] `POST /api/files/upload` accepts multipart uploads up to configured limit
- [ ] `GET /api/files/download` streams file downloads (or ZIPs directories)
- [ ] `POST /api/files/chmod` changes permissions on Linux
- [ ] `GET /api/files/search?root=/home&query=config` finds matching files
- [ ] Path traversal attempts (`..`, symlink escapes) return 403
- [ ] Paths outside allowed roots return 403
- [ ] Denied paths (like `/etc/shadow`) return 403
- [ ] Files under readonly roots cannot be modified (403 on write)
- [ ] Large files (>10MB) return error on read but can be downloaded
- [ ] All write operations are logged to audit log

## Files to Create
```
src/main/java/com/serverwatch/
├── model/dto/
│   ├── FileEntryDTO.java
│   ├── DirectoryListingDTO.java
│   ├── PathBreadcrumb.java
│   ├── FileContentDTO.java
│   ├── FileOperationRequest.java
│   └── UploadResponseDTO.java
├── service/
│   ├── FileService.java
│   └── PathValidator.java
└── controller/
    └── FileController.java

MODIFY:
├── config/ServerWatchProperties.java — add FileManager nested config
├── exception/GlobalExceptionHandler.java — add file-specific handlers
└── application.yml — add filemanager + multipart config
```

package com.serverwatch.controller;

import com.serverwatch.model.dto.*;
import com.serverwatch.service.FileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

/**
 * REST API for the file manager.
 *
 * <p>All endpoints require authentication (enforced by the JWT filter).
 * Write and destructive operations additionally require the {@code ADMIN} role.
 *
 * <pre>
 * READ (any authenticated user):
 *   GET  /api/files/list          — browse a directory
 *   GET  /api/files/roots         — list allowed root paths
 *   GET  /api/files/breadcrumbs   — navigation trail for a path
 *   GET  /api/files/read          — read a text file
 *   GET  /api/files/download      — download file or zip a directory
 *   GET  /api/files/search        — filename search
 *   GET  /api/files/size          — recursive directory size
 *
 * WRITE (ADMIN only):
 *   POST   /api/files/write       — overwrite / create text file
 *   POST   /api/files/create      — create file or directory
 *   DELETE /api/files             — delete file or directory
 *   POST   /api/files/move        — rename or move
 *   POST   /api/files/copy        — copy file or directory
 *   POST   /api/files/upload      — multipart file upload
 *   POST   /api/files/chmod       — change POSIX permissions
 * </pre>
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    // ── Read endpoints ────────────────────────────────────────────────────────

    /**
     * Lists the contents of a directory.
     *
     * @param path       absolute path to browse
     * @param showHidden include entries starting with {@code .} (default false)
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<DirectoryListingDTO>> list(
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean showHidden) {

        return ok(fileService.listDirectory(path, showHidden));
    }

    /** Returns the configured allowed root paths. */
    @GetMapping("/roots")
    public ResponseEntity<ApiResponse<List<String>>> roots() {
        return ok(fileService.getAllowedRoots());
    }

    /** Returns breadcrumb navigation segments for the given path. */
    @GetMapping("/breadcrumbs")
    public ResponseEntity<ApiResponse<List<PathBreadcrumb>>> breadcrumbs(
            @RequestParam String path) {

        return ok(fileService.getBreadcrumbs(path));
    }

    /**
     * Reads a text file and returns its content.
     * Returns {@code isBinary=true} with null content for binary files.
     */
    @GetMapping("/read")
    public ResponseEntity<ApiResponse<FileContentDTO>> read(@RequestParam String path) {
        return ok(fileService.readFile(path));
    }

    /**
     * Streams a file download. Directories are transparently zipped on the fly.
     */
    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> download(@RequestParam String path) {
        FileService.DownloadResponse dr = fileService.prepareDownload(path);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + escapeHeaderValue(dr.filename()) + "\"")
                .contentType(safeMediaType(dr.contentType()));

        if (dr.contentLength() > 0) {
            builder = builder.contentLength(dr.contentLength());
        }

        return builder.body(dr.body());
    }

    /**
     * Searches for files whose names contain {@code query} under {@code root}.
     *
     * @param root       directory to search within
     * @param query      case-insensitive filename fragment
     * @param maxResults maximum hits to return (capped at 200 by the service)
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<FileEntryDTO>>> search(
            @RequestParam String root,
            @RequestParam String query,
            @RequestParam(defaultValue = "50") int maxResults) {

        return ok(fileService.search(root, query, maxResults));
    }

    /**
     * Returns recursive disk usage stats for a directory.
     * Response: {@code {size, fileCount, dirCount}}.
     */
    @GetMapping("/size")
    public ResponseEntity<ApiResponse<Map<String, Long>>> size(@RequestParam String path) {
        return ok(fileService.getDirectorySize(path));
    }

    // ── Write endpoints (ADMIN only) ──────────────────────────────────────────

    /**
     * Writes text content to a file (creates it if it does not exist).
     * An atomic rename is used; the previous content is backed up as {@code *.sw-backup}.
     */
    @PostMapping("/write")
    public ResponseEntity<ApiResponse<FileEntryDTO>> write(
            @RequestBody FileOperationRequest req) {

        if (req.path() == null || req.path().isBlank()) {
            return badRequest("path is required");
        }
        if (req.content() == null) {
            return badRequest("content is required");
        }
        return ok(fileService.writeFile(req.path(), req.content(), req.encoding()));
    }

    /**
     * Creates a new file or directory.
     * Required fields: {@code path} (parent dir), {@code name}, {@code type} (FILE|DIRECTORY).
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<FileEntryDTO>> create(
            @RequestBody FileOperationRequest req) {

        if (req.path() == null || req.path().isBlank()) return badRequest("path is required");
        if (req.name() == null || req.name().isBlank()) return badRequest("name is required");
        if (req.type() == null || req.type().isBlank()) return badRequest("type is required");

        FileEntryDTO result = switch (req.type().toUpperCase()) {
            case "FILE"      -> fileService.createFile(req.path(), req.name(), req.content());
            case "DIRECTORY" -> fileService.createDirectory(req.path(), req.name());
            default          -> throw new IllegalArgumentException("type must be FILE or DIRECTORY");
        };
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    /**
     * Deletes a file or directory.
     *
     * @param path      absolute path to delete
     * @param recursive required for non-empty directories (default false)
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean recursive) {

        fileService.delete(path, recursive);
        return ResponseEntity.noContent().<ApiResponse<Void>>build();
    }

    /**
     * Moves or renames a file or directory.
     * Required fields: {@code sourcePath}, {@code targetPath}.
     */
    @PostMapping("/move")
    public ResponseEntity<ApiResponse<FileEntryDTO>> move(
            @RequestBody FileOperationRequest req) {

        if (req.sourcePath() == null) return badRequest("sourcePath is required");
        if (req.targetPath() == null) return badRequest("targetPath is required");
        return ok(fileService.move(req.sourcePath(), req.targetPath()));
    }

    /**
     * Copies a file or directory tree.
     * Required fields: {@code sourcePath}, {@code targetPath}.
     */
    @PostMapping("/copy")
    public ResponseEntity<ApiResponse<FileEntryDTO>> copy(
            @RequestBody FileOperationRequest req) {

        if (req.sourcePath() == null) return badRequest("sourcePath is required");
        if (req.targetPath() == null) return badRequest("targetPath is required");
        return ok(fileService.copy(req.sourcePath(), req.targetPath()));
    }

    /**
     * Accepts a multipart file upload.
     *
     * @param targetPath form field — absolute directory path on the server
     * @param file       the uploaded file
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadResponseDTO>> upload(
            @RequestParam("targetPath") String targetPath,
            @RequestPart("file") MultipartFile file) {

        if (file.isEmpty()) return badRequest("Uploaded file is empty");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(fileService.upload(targetPath, file)));
    }

    /**
     * Changes POSIX permissions on a file or directory (Linux only).
     * Required fields: {@code path}, {@code permissions} (octal, e.g. {@code 755}).
     */
    @PostMapping("/chmod")
    public ResponseEntity<ApiResponse<FileEntryDTO>> chmod(
            @RequestBody FileOperationRequest req) {

        if (req.path() == null || req.path().isBlank()) return badRequest("path is required");
        if (req.permissions() == null || req.permissions().isBlank()) {
            return badRequest("permissions is required");
        }
        return ok(fileService.chmod(req.path(), req.permissions()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private static <T> ResponseEntity<ApiResponse<T>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(ApiResponse.error(msg));
    }

    private static MediaType safeMediaType(String mime) {
        try {
            return MediaType.parseMediaType(mime);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /** Strips double-quotes from a filename so it is safe to embed in a header value. */
    private static String escapeHeaderValue(String filename) {
        return filename != null ? filename.replace("\"", "") : "download";
    }
}

package com.serverwatch.controller;

import com.serverwatch.model.dto.*;
import com.serverwatch.service.GitService;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Git repository management.
 *
 * <p>All responses are wrapped in {@link ApiResponse}. Controller-scoped
 * exception handlers translate JGit and application exceptions into
 * appropriate HTTP status codes before the global handler fires.
 *
 * <pre>
 * Repo Management:
 *   GET    /api/git/repos                         → List&lt;GitRepoDTO&gt;
 *   GET    /api/git/repos/{id}                    → GitRepoDTO
 *   POST   /api/git/repos/clone                   → GitRepoDTO
 *   POST   /api/git/repos/add                     → GitRepoDTO
 *   DELETE /api/git/repos/{id}                    → 200 OK
 *
 * Status &amp; History:
 *   GET    /api/git/repos/{id}/status             → GitStatusDTO
 *   GET    /api/git/repos/{id}/log                → List&lt;GitCommitDTO&gt;
 *   GET    /api/git/repos/{id}/commits/{hash}     → GitCommitDTO
 *   GET    /api/git/repos/{id}/commits/{hash}/diff → GitDiffDTO
 *   GET    /api/git/repos/{id}/file               → String
 *
 * Branch Operations:
 *   GET    /api/git/repos/{id}/branches           → List&lt;GitBranchDTO&gt;
 *   POST   /api/git/repos/{id}/branches           → GitBranchDTO
 *   DELETE /api/git/repos/{id}/branches/{name}    → 200 OK
 *   POST   /api/git/repos/{id}/checkout           → GitRepoDTO
 *
 * Remote Operations:
 *   POST   /api/git/repos/{id}/pull               → GitRepoDTO
 *   POST   /api/git/repos/{id}/push               → 200 OK
 *   POST   /api/git/repos/{id}/fetch              → 200 OK
 * </pre>
 */
@RestController
@RequestMapping("/api/git")
public class GitController {

    private final GitService gitService;

    public GitController(GitService gitService) {
        this.gitService = gitService;
    }

    // ── Repo Management ───────────────────────────────────────────────────────

    /**
     * Returns summary information for all registered repositories.
     */
    @GetMapping("/repos")
    public ResponseEntity<ApiResponse<List<GitRepoDTO>>> listRepos() {
        return ResponseEntity.ok(ApiResponse.ok(gitService.listRepos()));
    }

    /**
     * Returns detailed information for a single repository.
     */
    @GetMapping("/repos/{id}")
    public ResponseEntity<ApiResponse<GitRepoDTO>> getRepo(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(gitService.getRepoInfo(id)));
    }

    /**
     * Clones a remote repository into the configured base path.
     *
     * <p>Request body fields used: {@code remoteUrl} (required), {@code name}
     * (required), {@code branch} (optional, defaults to main).
     */
    @PostMapping("/repos/clone")
    public ResponseEntity<ApiResponse<GitRepoDTO>> cloneRepo(
            @RequestBody GitOperationRequest request) {

        if (request.remoteUrl() == null || request.remoteUrl().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("remoteUrl is required"));
        }
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("name is required"));
        }

        GitRepoDTO dto = gitService.cloneRepository(
                request.remoteUrl(), request.name(), request.branch());
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /**
     * Registers an existing local repository (must contain a {@code .git} directory).
     *
     * <p>Request body fields used: {@code localPath} (required), {@code name} (required).
     */
    @PostMapping("/repos/add")
    public ResponseEntity<ApiResponse<GitRepoDTO>> addRepo(
            @RequestBody GitOperationRequest request) {

        if (request.localPath() == null || request.localPath().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("localPath is required"));
        }
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("name is required"));
        }

        GitRepoDTO dto = gitService.addExistingRepo(request.localPath(), request.name());
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /**
     * Unregisters a repository. The files on disk are NOT deleted.
     */
    @DeleteMapping("/repos/{id}")
    public ResponseEntity<ApiResponse<Void>> removeRepo(@PathVariable String id) {
        gitService.removeRepo(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Status & History ──────────────────────────────────────────────────────

    /**
     * Returns the working-tree and index status for a repository.
     */
    @GetMapping("/repos/{id}/status")
    public ResponseEntity<ApiResponse<GitStatusDTO>> getStatus(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(gitService.getStatus(id)));
    }

    /**
     * Returns a page of commits from the specified branch.
     *
     * @param branch   branch or ref to log (defaults to HEAD)
     * @param limit    max commits to return (default 50, max 200)
     * @param skip     commits to skip for pagination (default 0)
     */
    @GetMapping("/repos/{id}/log")
    public ResponseEntity<ApiResponse<List<GitCommitDTO>>> getLog(
            @PathVariable String id,
            @RequestParam(required = false) String branch,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int skip) {

        return ResponseEntity.ok(ApiResponse.ok(
                gitService.getCommitLog(id, branch, limit, skip)));
    }

    /**
     * Returns metadata and diff statistics for a single commit.
     */
    @GetMapping("/repos/{id}/commits/{hash}")
    public ResponseEntity<ApiResponse<GitCommitDTO>> getCommit(
            @PathVariable String id,
            @PathVariable String hash) {

        return ResponseEntity.ok(ApiResponse.ok(gitService.getCommitInfo(id, hash)));
    }

    /**
     * Returns a file-by-file unified diff for a single commit.
     * Individual files are capped at 50 KB; total diff is capped at 500 KB.
     */
    @GetMapping("/repos/{id}/commits/{hash}/diff")
    public ResponseEntity<ApiResponse<GitDiffDTO>> getCommitDiff(
            @PathVariable String id,
            @PathVariable String hash) {

        return ResponseEntity.ok(ApiResponse.ok(gitService.getCommitDiff(id, hash)));
    }

    /**
     * Returns the raw content of a file at a specific commit.
     * Returns {@code null} for binary files.
     *
     * @param commit commit SHA or branch name
     * @param path   relative file path within the repository
     */
    @GetMapping("/repos/{id}/file")
    public ResponseEntity<ApiResponse<String>> getFileContent(
            @PathVariable String id,
            @RequestParam String commit,
            @RequestParam String path) {

        return ResponseEntity.ok(ApiResponse.ok(
                gitService.getFileContent(id, commit, path)));
    }

    // ── Branch Operations ─────────────────────────────────────────────────────

    /**
     * Lists all local and remote branches with ahead/behind tracking info.
     */
    @GetMapping("/repos/{id}/branches")
    public ResponseEntity<ApiResponse<List<GitBranchDTO>>> listBranches(
            @PathVariable String id) {

        return ResponseEntity.ok(ApiResponse.ok(gitService.listBranches(id)));
    }

    /**
     * Creates a new local branch.
     *
     * <p>Request body fields used: {@code name} (required), {@code startPoint} (optional).
     */
    @PostMapping("/repos/{id}/branches")
    public ResponseEntity<ApiResponse<GitBranchDTO>> createBranch(
            @PathVariable String id,
            @RequestBody GitOperationRequest request) {

        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("name is required"));
        }

        return ResponseEntity.ok(ApiResponse.ok(
                gitService.createBranch(id, request.name(), request.startPoint())));
    }

    /**
     * Deletes a local branch (non-forced — unmerged branches are rejected).
     */
    @DeleteMapping("/repos/{id}/branches/{name}")
    public ResponseEntity<ApiResponse<Void>> deleteBranch(
            @PathVariable String id,
            @PathVariable String name) {

        gitService.deleteBranch(id, name);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Checks out a branch, optionally creating it.
     *
     * <p>Request body fields used: {@code branch} (required), {@code createNew}
     * (optional, defaults to false).
     */
    @PostMapping("/repos/{id}/checkout")
    public ResponseEntity<ApiResponse<GitRepoDTO>> checkout(
            @PathVariable String id,
            @RequestBody GitOperationRequest request) {

        if (request.branch() == null || request.branch().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("branch is required"));
        }

        return ResponseEntity.ok(ApiResponse.ok(
                gitService.checkout(id, request.branch(), request.isCreateNew())));
    }

    // ── Remote Operations ─────────────────────────────────────────────────────

    /**
     * Pulls from a remote.
     *
     * <p>Request body fields used: {@code remoteName} (optional, defaults to origin).
     */
    @PostMapping("/repos/{id}/pull")
    public ResponseEntity<ApiResponse<GitRepoDTO>> pull(
            @PathVariable String id,
            @RequestBody(required = false) GitOperationRequest request) {

        String remote = request != null ? request.remoteNameOrDefault() : "origin";
        return ResponseEntity.ok(ApiResponse.ok(gitService.pull(id, remote)));
    }

    /**
     * Pushes to a remote.
     *
     * <p>Request body fields used: {@code remoteName} (optional), {@code branch} (optional).
     */
    @PostMapping("/repos/{id}/push")
    public ResponseEntity<ApiResponse<Void>> push(
            @PathVariable String id,
            @RequestBody(required = false) GitOperationRequest request) {

        String remote = request != null ? request.remoteNameOrDefault() : "origin";
        String branch = request != null ? request.branch() : null;
        gitService.push(id, remote, branch);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Fetches from a remote without merging.
     *
     * <p>Request body fields used: {@code remoteName} (optional, defaults to origin).
     */
    @PostMapping("/repos/{id}/fetch")
    public ResponseEntity<ApiResponse<Void>> fetch(
            @PathVariable String id,
            @RequestBody(required = false) GitOperationRequest request) {

        String remote = request != null ? request.remoteNameOrDefault() : "origin";
        gitService.fetch(id, remote);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Controller-scoped exception handlers ──────────────────────────────────

    @ExceptionHandler(org.eclipse.jgit.api.errors.TransportException.class)
    public ResponseEntity<ApiResponse<Void>> handleTransportException(
            org.eclipse.jgit.api.errors.TransportException ex) {
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("Auth fail") || msg.contains("not authorized")
                || msg.contains("Authentication"))) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Git authentication failed. Set the GIT_TOKEN environment variable."));
        }
        return ResponseEntity.status(502)
                .body(ApiResponse.error("Git transport error: " + sanitize(msg)));
    }

    @ExceptionHandler(org.eclipse.jgit.errors.RepositoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRepoNotFound(
            org.eclipse.jgit.errors.RepositoryNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(ApiResponse.error("Git repository not found on disk: " + sanitize(ex.getMessage())));
    }

    @ExceptionHandler(GitAPIException.class)
    public ResponseEntity<ApiResponse<Void>> handleGitApiException(GitAPIException ex) {
        return ResponseEntity.status(500)
                .body(ApiResponse.error("Git operation failed: " + sanitize(ex.getMessage())));
    }

    private static String sanitize(String msg) {
        if (msg == null) return "unknown error";
        return msg.length() > 300 ? msg.substring(0, 300) + "…" : msg;
    }
}

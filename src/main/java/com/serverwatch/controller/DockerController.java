package com.serverwatch.controller;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.serverwatch.model.dto.*;
import com.serverwatch.service.DockerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for Docker container management.
 *
 * <pre>
 * GET    /api/docker/containers              — list running containers
 * GET    /api/docker/containers?all=true      — include stopped
 * GET    /api/docker/containers/{id}          — full inspect
 * GET    /api/docker/containers/{id}/stats    — live resource stats
 * GET    /api/docker/containers/{id}/logs     — tail logs
 *
 * POST   /api/docker/containers/{id}/start   — start
 * POST   /api/docker/containers/{id}/stop    — stop  (?timeout=10)
 * POST   /api/docker/containers/{id}/restart — restart (?timeout=10)
 * POST   /api/docker/containers/{id}/pause   — pause
 * POST   /api/docker/containers/{id}/unpause — unpause
 * DELETE /api/docker/containers/{id}         — remove (?force=false)
 *
 * GET    /api/docker/images                  — list local images
 * GET    /api/docker/info                    — daemon info
 * </pre>
 */
@RestController
@RequestMapping("/api/docker")
public class DockerController {

    private final DockerService dockerService;

    public DockerController(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    // =========================================================================
    // Container listing & inspection
    // =========================================================================

    @GetMapping("/containers")
    public ResponseEntity<ApiResponse<List<ContainerInfoDTO>>> listContainers(
            @RequestParam(defaultValue = "false") boolean all) {
        return ok(dockerService.listContainers(all));
    }

    @GetMapping("/containers/{id}")
    public ResponseEntity<ApiResponse<InspectContainerResponse>> inspectContainer(
            @PathVariable String id) {
        return ok(dockerService.inspectContainer(id));
    }

    @GetMapping("/containers/{id}/stats")
    public ResponseEntity<ApiResponse<ContainerStatsDTO>> getStats(@PathVariable String id) {
        return ok(dockerService.getContainerStats(id));
    }

    @GetMapping("/containers/{id}/logs")
    public ResponseEntity<ApiResponse<ContainerLogDTO>> getLogs(
            @PathVariable String id,
            @RequestParam(defaultValue = "100")  int     tail,
            @RequestParam(defaultValue = "true") boolean stdout,
            @RequestParam(defaultValue = "true") boolean stderr) {
        if (tail < 1 || tail > 10_000) {
            return badRequest("tail must be between 1 and 10000");
        }
        return ok(dockerService.getContainerLogs(id, tail, stdout, stderr));
    }

    // =========================================================================
    // Lifecycle operations
    // =========================================================================

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/containers/{id}/start")
    public ResponseEntity<ApiResponse<Void>> startContainer(@PathVariable String id) {
        dockerService.startContainer(id);
        return ok(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/containers/{id}/stop")
    public ResponseEntity<ApiResponse<Void>> stopContainer(
            @PathVariable String id,
            @RequestParam(defaultValue = "10") int timeout) {
        dockerService.stopContainer(id, timeout);
        return ok(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/containers/{id}/restart")
    public ResponseEntity<ApiResponse<Void>> restartContainer(
            @PathVariable String id,
            @RequestParam(defaultValue = "10") int timeout) {
        dockerService.restartContainer(id, timeout);
        return ok(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/containers/{id}/pause")
    public ResponseEntity<ApiResponse<Void>> pauseContainer(@PathVariable String id) {
        dockerService.pauseContainer(id);
        return ok(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/containers/{id}/unpause")
    public ResponseEntity<ApiResponse<Void>> unpauseContainer(@PathVariable String id) {
        dockerService.unpauseContainer(id);
        return ok(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/containers/{id}")
    public ResponseEntity<ApiResponse<Void>> removeContainer(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean force) {
        dockerService.removeContainer(id, force);
        return ok(null);
    }

    // =========================================================================
    // Images & system
    // =========================================================================

    @GetMapping("/images")
    public ResponseEntity<ApiResponse<List<ImageDTO>>> listImages() {
        return ok(dockerService.listImages());
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<DockerInfoDTO>> getInfo() {
        return ok(dockerService.getDockerInfo());
    }

    // =========================================================================
    // Docker-specific exception handling (controller-scoped)
    // =========================================================================

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Container or resource not found: " + ex.getMessage()));
    }

    @ExceptionHandler(NotModifiedException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotModified(NotModifiedException ex) {
        // 304 cannot carry a body by spec; return 409 with a descriptive message
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Container state unchanged (already started/stopped): " + ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Docker conflict: " + ex.getMessage()));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private static <T> ResponseEntity<ApiResponse<T>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(ApiResponse.error(msg));
    }
}

package com.github.kevinldg.backend.container;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.gameserver.ClassificationRequest;
import com.github.kevinldg.backend.role.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/containers")
@RequiredArgsConstructor
public class ContainerController {

    /** Container IDs or names, as accepted by Docker. */
    private static final String CONTAINER_ID_PATTERN = "[a-zA-Z0-9][a-zA-Z0-9_.-]*";

    private final ContainerService containerService;
    private final ContainerLogStreamService logStreamService;

    @GetMapping
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public ContainerOverviewResponse getOverview() {
        return containerService.getOverview();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTAINER_VIEW')")
    public ContainerDetailsResponse getDetails(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        boolean includeEnvironment = user.getPermissions().contains(Permission.CONTAINER_ENV_VIEW);
        return containerService.getDetails(id, includeEnvironment);
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority('CONTAINER_LOGS_VIEW')")
    public ContainerLogsResponse getLogs(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                                         @RequestParam(defaultValue = "500") @Min(1) @Max(5000) int tail) {
        return containerService.getLogs(id, tail);
    }

    /** Live logs as Server-Sent Events, see {@link ContainerLogStreamService}. */
    @GetMapping(value = "/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('CONTAINER_LOGS_VIEW')")
    public SseEmitter streamLogs(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return logStreamService.openStream(id, user);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('CONTAINER_START')")
    public ResponseEntity<Void> start(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        containerService.start(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAuthority('CONTAINER_STOP')")
    public ResponseEntity<Void> stop(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        containerService.stop(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restart")
    @PreAuthorize("hasAuthority('CONTAINER_RESTART')")
    public ResponseEntity<Void> restart(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        containerService.restart(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/force-stop")
    @PreAuthorize("hasAuthority('CONTAINER_FORCE_STOP')")
    public ResponseEntity<Void> forceStop(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        containerService.forceStop(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    /** Manual game server classification; mode AUTOMATIC removes it. */
    @PutMapping("/{id}/classification")
    @PreAuthorize("hasAuthority('GAMESERVER_MANAGE')")
    public ResponseEntity<Void> classify(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                                         @Valid @RequestBody ClassificationRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        containerService.classify(id, request, user);
        return ResponseEntity.noContent().build();
    }

    /** Deletes a stopped container; its volumes are kept. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTAINER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        containerService.delete(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}

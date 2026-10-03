package com.github.kevinldg.backend.container.creation;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@PreAuthorize("hasAuthority('CONTAINER_CREATE')")
@RequiredArgsConstructor
public class ContainerCreationController {

    private final ContainerCreationService creationService;

    @GetMapping("/api/containers/creation-options")
    public CreationOptions getOptions() {
        return creationService.getOptions();
    }

    /** Starts creating a container; returns 202 with the job to follow. */
    @PostMapping("/api/containers")
    public ResponseEntity<CreationJobResponse> create(@Valid @RequestBody ContainerCreateRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(creationService.create(request, user));
    }

    @GetMapping("/api/container-jobs/{id}")
    public CreationJobResponse getJob(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser user) {
        return creationService.getJob(id, user);
    }

    @GetMapping(value = "/api/container-jobs/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter followJob(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser user) {
        return creationService.subscribe(id, user);
    }
}

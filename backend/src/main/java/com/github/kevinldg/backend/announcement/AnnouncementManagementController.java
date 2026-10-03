package com.github.kevinldg.backend.announcement;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/announcements")
@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
@RequiredArgsConstructor
public class AnnouncementManagementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public List<AnnouncementResponse> listAll() {
        return announcementService.listAll();
    }

    @PostMapping
    public ResponseEntity<AnnouncementResponse> create(@Valid @RequestBody AnnouncementRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(announcementService.create(request, actor));
    }

    /** Also used to activate or deactivate an announcement. */
    @PutMapping("/{id}")
    public AnnouncementResponse update(@PathVariable String id, @Valid @RequestBody AnnouncementRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        return announcementService.update(id, request, actor);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser actor) {
        announcementService.delete(id, actor);
        return ResponseEntity.noContent().build();
    }
}

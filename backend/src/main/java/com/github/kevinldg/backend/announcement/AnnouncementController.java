package com.github.kevinldg.backend.announcement;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** Announcements currently shown on the dashboard. */
    @GetMapping("/api/announcements")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public List<VisibleAnnouncement> listVisible() {
        return announcementService.listVisible();
    }
}

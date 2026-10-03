package com.github.kevinldg.backend.system;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SystemInfoController {

    private final SystemInfoService systemInfoService;

    @GetMapping("/api/admin/system")
    @PreAuthorize("hasAuthority('SYSTEM_INFO_VIEW')")
    public SystemInfoResponse getSystemInfo() {
        return systemInfoService.getSystemInfo();
    }
}

package com.github.kevinldg.backend.configfile;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.Backup;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.DirectoryListing;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.FileContent;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.Overview;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.SaveRequest;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.SaveResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Configuration files of game servers, see {@link ConfigFileService}. */
@RestController
@RequestMapping("/api/containers/{id}/config-files")
@RequiredArgsConstructor
public class ConfigFileController {

    private static final String CONTAINER_ID_PATTERN = "[a-zA-Z0-9][a-zA-Z0-9_.-]*";

    private final ConfigFileService configFileService;

    @GetMapping
    @PreAuthorize("hasAuthority('GAMESERVER_CONFIG_VIEW')")
    public Overview getOverview(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id) {
        return configFileService.getOverview(id);
    }

    @GetMapping("/directory")
    @PreAuthorize("hasAuthority('GAMESERVER_CONFIG_VIEW')")
    public DirectoryListing listDirectory(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                                          @RequestParam String path) {
        return configFileService.listDirectory(id, path);
    }

    @GetMapping("/content")
    @PreAuthorize("hasAuthority('GAMESERVER_CONFIG_VIEW')")
    public FileContent readFile(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                                @RequestParam String path,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return configFileService.readFile(id, path, user.getUsername());
    }

    @GetMapping("/backup")
    @PreAuthorize("hasAuthority('GAMESERVER_CONFIG_VIEW')")
    public Backup getBackup(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                            @RequestParam String path,
                            @AuthenticationPrincipal AuthenticatedUser user) {
        return configFileService.getBackup(id, path, user.getUsername());
    }

    @PutMapping("/content")
    @PreAuthorize("hasAuthority('GAMESERVER_CONFIG_EDIT')")
    public SaveResponse saveFile(@PathVariable @Pattern(regexp = CONTAINER_ID_PATTERN) String id,
                                 @Valid @RequestBody SaveRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return configFileService.saveFile(id, request.path(), request.content(), request.expectedSha256(), user);
    }
}

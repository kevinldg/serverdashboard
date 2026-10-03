package com.github.kevinldg.backend.configfile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** Request and response types of the configuration file API. */
public final class ConfigFileResponses {

    private ConfigFileResponses() {
    }

    /**
     * @param roots      directories where the file browser starts
     * @param knownFiles relevant files of the game server's profile that exist
     * @param running    browsing directories requires a running container
     */
    public record Overview(List<String> roots, List<FileInfo> knownFiles, boolean running) {
    }

    /**
     * @param editable regular text file with an allowed extension and size
     */
    public record FileInfo(String path, String name, ContainerFiles.Type type, long size, Instant modifiedAt,
                           boolean editable) {
    }

    public record DirectoryListing(String path, List<FileInfo> entries) {
    }

    /**
     * @param sha256    identifies the loaded version; required for saving (conflict detection)
     * @param hints     e.g. that values are overwritten on restart
     * @param backup    the previous version, if the file was saved through the application before
     */
    public record FileContent(String path, String content, String sha256, long size, Instant modifiedAt,
                              List<String> hints, BackupInfo backup) {
    }

    public record BackupInfo(Instant replacedAt, String replacedBy) {
    }

    public record Backup(String path, String content, Instant replacedAt, String replacedBy) {
    }

    /**
     * @param expectedSha256 the version the edit is based on
     */
    public record SaveRequest(@NotBlank String path, @NotNull String content, @NotBlank String expectedSha256) {
    }

    public record SaveResponse(String path, String sha256, Instant savedAt) {
    }
}

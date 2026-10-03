package com.github.kevinldg.backend.configfile;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.common.PosixPaths;
import com.github.kevinldg.backend.docker.DockerCalls;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Low-level file access inside containers.
 * <ul>
 *     <li>Single files are read and written with Docker's archive API (works for stopped containers).</li>
 *     <li>Directories are listed by running {@code find} inside the container (running containers only).
 *         Arguments are passed directly, without a shell, so paths cannot inject commands.</li>
 * </ul>
 */
@Component
public class ContainerFiles {

    private static final String NOT_FOUND = "The container does not exist (anymore).";
    private static final long LIST_TIMEOUT_SECONDS = 30;

    private final DockerClient dockerClient;

    public ContainerFiles(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public enum Type {
        FILE, DIRECTORY, SYMLINK, OTHER
    }

    /** An entry of a directory listing. */
    public record Entry(String path, Type type, long size, Instant modifiedAt) {
    }

    /**
     * A file read from a container.
     *
     * @param content null if the file is not a regular file or larger than the requested maximum
     */
    public record FileData(String path, Type type, long size, Instant modifiedAt, int mode, long userId, long groupId,
                           byte[] content) {
    }

    /** Lists the direct children of a directory. The container must be running. */
    public List<Entry> list(String containerId, String directory) {
        String output = exec(containerId, "find", directory, "-mindepth", "1", "-maxdepth", "1",
                "-exec", "stat", "-c", "%F|%s|%Y|%n", "{}", "+");
        List<Entry> entries = new ArrayList<>();
        for (String line : output.split("\n")) {
            String[] parts = line.split("\\|", 4);
            if (parts.length < 4) {
                continue;
            }
            try {
                entries.add(new Entry(parts[3], typeOf(parts[0]), Long.parseLong(parts[1]),
                        Instant.ofEpochSecond(Long.parseLong(parts[2]))));
            } catch (NumberFormatException e) {
                // Unexpected output line (e.g. a file name containing a line break); skip it
            }
        }
        return entries;
    }

    /**
     * Reads a file without following symlinks.
     *
     * @param maxContentSize content is only returned for regular files up to this size
     * @return empty if the path does not exist
     */
    public Optional<FileData> read(String containerId, String path, long maxContentSize) {
        InputStream archive;
        try {
            archive = DockerCalls.call(() -> dockerClient.copyArchiveFromContainerCmd(containerId, path).exec(),
                    NOT_FOUND, "The file could not be read.");
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND && e.getCause() instanceof NotFoundException notFound
                    && !String.valueOf(notFound.getMessage()).contains("No such container")) {
                return Optional.empty();
            }
            throw e;
        }
        try (TarArchiveInputStream tar = new TarArchiveInputStream(archive)) {
            TarArchiveEntry entry = tar.getNextEntry();
            if (entry == null) {
                return Optional.empty();
            }
            Type type = entry.isSymbolicLink() ? Type.SYMLINK : entry.isDirectory() ? Type.DIRECTORY
                    : entry.isFile() ? Type.FILE : Type.OTHER;
            byte[] content = type == Type.FILE && entry.getSize() <= maxContentSize ? tar.readAllBytes() : null;
            return Optional.of(new FileData(path, type, entry.getSize(), entry.getModTime().toInstant(),
                    entry.getMode(), entry.getLongUserId(), entry.getLongGroupId(), content));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The file could not be read.", e);
        }
    }

    /**
     * Replaces a file. Owner, group and permissions are set explicitly, so the game server can still access it.
     */
    public void write(String containerId, String path, byte[] content, int mode, long userId, long groupId) {
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(archive)) {
            TarArchiveEntry entry = new TarArchiveEntry(PosixPaths.fileName(path));
            entry.setSize(content.length);
            entry.setMode(mode);
            entry.setUserId(userId);
            entry.setGroupId(groupId);
            entry.setModTime(Instant.now().toEpochMilli());
            tar.putArchiveEntry(entry);
            tar.write(content);
            tar.closeArchiveEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        DockerCalls.call(() -> {
            dockerClient.copyArchiveToContainerCmd(containerId)
                    .withRemotePath(PosixPaths.parent(path))
                    .withTarInputStream(new ByteArrayInputStream(archive.toByteArray()))
                    .withNoOverwriteDirNonDir(true)
                    .exec();
            return null;
        }, NOT_FOUND, "The file could not be saved.");
    }

    private String exec(String containerId, String... command) {
        ExecCreateCmdResponse created = DockerCalls.call(() -> dockerClient.execCreateCmd(containerId)
                        .withCmd(command)
                        .withAttachStdout(true)
                        .withAttachStderr(true)
                        .exec(),
                NOT_FOUND, "The container is not running.");

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            boolean finished = dockerClient.execStartCmd(created.getId()).exec(new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    (frame.getStreamType() == StreamType.STDERR ? stderr : stdout).writeBytes(frame.getPayload());
                }
            }).awaitCompletion(LIST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "Listing the directory took too long.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Listing the directory was interrupted.", e);
        }

        Long exitCode = dockerClient.inspectExecCmd(created.getId()).exec().getExitCodeLong();
        if (exitCode != null && exitCode != 0) {
            String error = stderr.toString(StandardCharsets.UTF_8).strip();
            throw new ApiException(HttpStatus.BAD_GATEWAY, error.contains("not found")
                    ? "The directory could not be listed: the container lacks 'find' or 'stat'."
                    : "The directory could not be listed.",
                    new IllegalStateException("exit code " + exitCode + ": " + error));
        }
        return stdout.toString(StandardCharsets.UTF_8);
    }

    private static Type typeOf(String statType) {
        return switch (statType) {
            case "regular file", "regular empty file" -> Type.FILE;
            case "directory" -> Type.DIRECTORY;
            case "symbolic link" -> Type.SYMLINK;
            default -> Type.OTHER;
        };
    }
}

package com.github.kevinldg.backend.configfile;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kevinldg.backend.audit.AuditAction;
import com.github.kevinldg.backend.audit.AuditEvent;
import com.github.kevinldg.backend.audit.AuditService;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.common.PosixPaths;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.Backup;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.BackupInfo;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.DirectoryListing;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.FileContent;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.FileInfo;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.Overview;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.SaveResponse;
import com.github.kevinldg.backend.configfile.ContainerFiles.FileData;
import com.github.kevinldg.backend.configfile.ContainerFiles.Type;
import com.github.kevinldg.backend.docker.DockerCalls;
import com.github.kevinldg.backend.gameserver.GameServerService;
import com.github.kevinldg.backend.gameserver.GameServerService.ContainerRef;
import com.github.kevinldg.backend.gameserver.GameServerStatus;
import com.github.kevinldg.backend.gameserver.profile.GameServerProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Viewing and editing configuration files of game servers.
 * <p>
 * Rules:
 * <ul>
 *     <li>Only containers that are game servers; only paths below the profile's config roots (or the container's mounts
 *         for generic game servers).</li>
 *     <li>Only regular text files (UTF-8, no NUL bytes) with an allowed extension, up to {@value #MAX_FILE_SIZE} bytes.
 *         Symlinks are listed but never followed.</li>
 *     <li>Saving requires the hash of the loaded version (conflict detection), keeps owner, group and permissions,
 *         and stores the previous content as backup (one version per file).</li>
 * </ul>
 * Opening files and backups, saving, and failed saves are recorded in the audit log (never the content).
 */
@Service
@RequiredArgsConstructor
public class ConfigFileService {

    static final int MAX_FILE_SIZE = 1024 * 1024;
    static final Set<String> EDITABLE_EXTENSIONS = Set.of(
            "properties", "json", "yml", "yaml", "toml", "txt", "cfg", "conf", "ini", "xml", "env", "sh");

    private static final Set<String> MINECRAFT_PROFILES = Set.of("minecraft-java", "minecraft-bedrock");

    private final DockerClient dockerClient;
    private final ContainerFiles containerFiles;
    private final GameServerService gameServerService;
    private final ConfigFileBackupRepository backupRepository;
    private final AuditService auditService;
    private final Clock clock;

    public Overview getOverview(String containerId) {
        Context context = context(containerId);
        List<FileInfo> knownFiles = context.profile().map(GameServerProfile::knownConfigFiles).orElse(List.of()).stream()
                .flatMap(path -> containerFiles.read(context.id(), path, 0).stream())
                .map(ConfigFileService::toInfo)
                .toList();
        return new Overview(context.roots(), knownFiles, context.running());
    }

    public DirectoryListing listDirectory(String containerId, String directory) {
        Context context = context(containerId);
        String path = requireWithinRoots(directory, context, true);
        if (!context.running()) {
            throw new ApiException(HttpStatus.CONFLICT, "Browsing files is only possible while the container is running. "
                    + "The known configuration files can still be opened.");
        }
        List<FileInfo> entries = containerFiles.list(context.id(), path).stream()
                .map(entry -> new FileInfo(entry.path(), PosixPaths.fileName(entry.path()), entry.type(), entry.size(),
                        entry.modifiedAt(), isEditable(entry.path(), entry.type(), entry.size())))
                .sorted(Comparator.comparing((FileInfo info) -> info.type() != Type.DIRECTORY)
                        .thenComparing(FileInfo::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new DirectoryListing(path, entries);
    }

    /**
     * @param viewer username, for the audit log
     */
    public FileContent readFile(String containerId, String filePath, String viewer) {
        Context context = context(containerId);
        String path = requireEditablePath(filePath, context);
        FileData file = readTextFile(context, path);
        auditService.recordDeduplicated(AuditEvent.success(viewer, AuditAction.CONFIG_FILE_OPEN, context.name(),
                "Opened '" + path + "' in container '" + context.name() + "'"));

        Optional<ConfigFileBackup> backup = backupRepository.findById(ConfigFileBackup.idOf(context.name(), path));
        return new FileContent(path, new String(file.content(), StandardCharsets.UTF_8), sha256(file.content()),
                file.size(), file.modifiedAt(), hints(context, path),
                backup.map(b -> new BackupInfo(b.getReplacedAt(), b.getReplacedBy())).orElse(null));
    }

    public SaveResponse saveFile(String containerId, String filePath, String content, String expectedSha256,
                                 AuthenticatedUser actor) {
        String containerName = containerId;
        try {
            Context context = context(containerId);
            containerName = context.name();
            String path = requireEditablePath(filePath, context);
            byte[] newContent = content.getBytes(StandardCharsets.UTF_8);
            if (newContent.length > MAX_FILE_SIZE) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "The file is too large (maximum 1 MB).");
            }

            FileData current = readTextFile(context, path);
            if (!sha256(current.content()).equals(expectedSha256)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "The file was changed in the meantime (e.g. by the game server). Reload it and apply your changes again.");
            }

            ConfigFileBackup backup = new ConfigFileBackup();
            backup.setId(ConfigFileBackup.idOf(context.name(), path));
            backup.setContent(new String(current.content(), StandardCharsets.UTF_8));
            backup.setReplacedAt(clock.instant());
            backup.setReplacedBy(actor.getUsername());
            backupRepository.save(backup);

            containerFiles.write(context.id(), path, newContent, current.mode(), current.userId(), current.groupId());
            auditService.record(AuditEvent.success(actor.getUsername(), AuditAction.CONFIG_FILE_SAVE, context.name(),
                    "Saved '" + path + "' in container '" + context.name() + "'"));
            return new SaveResponse(path, sha256(newContent), clock.instant());
        } catch (RuntimeException e) {
            auditService.record(AuditEvent.failure(actor.getUsername(), AuditAction.CONFIG_FILE_SAVE, containerName,
                    "Could not save '" + filePath + "' in container '" + containerName + "'", e.getMessage()));
            throw e;
        }
    }

    /**
     * @param viewer username, for the audit log
     */
    public Backup getBackup(String containerId, String filePath, String viewer) {
        Context context = context(containerId);
        String path = requireEditablePath(filePath, context);
        Backup backup = backupRepository.findById(ConfigFileBackup.idOf(context.name(), path))
                .map(b -> new Backup(path, b.getContent(), b.getReplacedAt(), b.getReplacedBy()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "There is no previous version of this file."));
        auditService.recordDeduplicated(AuditEvent.success(viewer, AuditAction.CONFIG_FILE_BACKUP_VIEW, context.name(),
                "Viewed the previous version of '" + path + "' in container '" + context.name() + "'"));
        return backup;
    }

    /** What the rules depend on: game server status, profile, roots, and whether the container is running. */
    private record Context(String id, String name, boolean running, Optional<GameServerProfile> profile,
                           List<String> roots, Map<String, String> environment) {
    }

    private Context context(String containerId) {
        InspectContainerResponse container = DockerCalls.call(() -> dockerClient.inspectContainerCmd(containerId).exec(),
                "The container does not exist (anymore).", "The container could not be inspected.");
        String name = container.getName() != null && container.getName().startsWith("/")
                ? container.getName().substring(1) : container.getName();
        String image = container.getConfig() != null ? container.getConfig().getImage() : null;
        Map<String, String> labels = container.getConfig() != null ? container.getConfig().getLabels() : null;

        GameServerStatus status = gameServerService.detect(new ContainerRef(name, image, labels));
        if (!status.gameServer()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Configuration files are only available for game servers.");
        }
        Optional<GameServerProfile> profile = Optional.ofNullable(status.profileId()).flatMap(gameServerService::findProfile);

        List<String> roots = profile.map(GameServerProfile::configRoots).filter(list -> !list.isEmpty())
                .orElseGet(() -> mountTargets(container));
        boolean running = container.getState() != null && Boolean.TRUE.equals(container.getState().getRunning());
        return new Context(container.getId(), name, running, profile, roots, environment(container));
    }

    /** For generic game servers: the container's volumes and bind mounts. */
    private static List<String> mountTargets(InspectContainerResponse container) {
        if (container.getMounts() == null) {
            return List.of();
        }
        return container.getMounts().stream()
                .filter(mount -> mount.getDestination() != null)
                .map(mount -> mount.getDestination().getPath())
                .flatMap(path -> PosixPaths.normalizeAbsolute(path).stream())
                .filter(path -> !path.equals("/"))
                .distinct()
                .sorted()
                .toList();
    }

    private static Map<String, String> environment(InspectContainerResponse container) {
        if (container.getConfig() == null || container.getConfig().getEnv() == null) {
            return Map.of();
        }
        return Arrays.stream(container.getConfig().getEnv())
                .filter(entry -> entry.contains("="))
                .collect(Collectors.toMap(entry -> entry.substring(0, entry.indexOf('=')),
                        entry -> entry.substring(entry.indexOf('=') + 1), (first, second) -> second));
    }

    /**
     * @param allowRoot whether the root directory itself is allowed (for listing)
     * @return the normalized path
     */
    private static String requireWithinRoots(String path, Context context, boolean allowRoot) {
        String normalized = PosixPaths.normalizeAbsolute(path)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "The path must be absolute and must not contain '..'."));
        boolean allowed = context.roots().stream()
                .anyMatch(root -> PosixPaths.isBelow(normalized, root) || (allowRoot && normalized.equals(root)));
        if (!allowed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only files below " + String.join(", ", context.roots())
                    + " are accessible.");
        }
        return normalized;
    }

    private static String requireEditablePath(String path, Context context) {
        String normalized = requireWithinRoots(path, context, false);
        if (!hasEditableExtension(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This file type cannot be edited. Allowed: "
                    + EDITABLE_EXTENSIONS.stream().sorted().map(extension -> "." + extension).collect(Collectors.joining(" ")));
        }
        return normalized;
    }

    private FileData readTextFile(Context context, String path) {
        FileData file = containerFiles.read(context.id(), path, MAX_FILE_SIZE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "The file does not exist."));
        if (file.type() == Type.SYMLINK) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Symbolic links cannot be opened.");
        }
        if (file.type() != Type.FILE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only regular files can be opened.");
        }
        if (file.content() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file is too large (maximum 1 MB).");
        }
        if (!isText(file.content())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file is not a text file.");
        }
        return file;
    }

    private List<String> hints(Context context, String path) {
        boolean minecraft = context.profile().map(profile -> MINECRAFT_PROFILES.contains(profile.id())).orElse(false);
        boolean overridden = !"false".equalsIgnoreCase(context.environment().get("OVERRIDE_SERVER_PROPERTIES"));
        if (minecraft && overridden && PosixPaths.fileName(path).equals("server.properties")) {
            return List.of("This container sets server properties from its environment variables on every start. "
                    + "Properties that are also set as environment variables are overwritten on restart. "
                    + "Set OVERRIDE_SERVER_PROPERTIES=false to manage this file only manually.");
        }
        return List.of();
    }

    private static FileInfo toInfo(FileData file) {
        return new FileInfo(file.path(), PosixPaths.fileName(file.path()), file.type(), file.size(), file.modifiedAt(),
                isEditable(file.path(), file.type(), file.size()));
    }

    private static boolean isEditable(String path, Type type, long size) {
        return type == Type.FILE && size <= MAX_FILE_SIZE && hasEditableExtension(path);
    }

    private static boolean hasEditableExtension(String path) {
        String name = PosixPaths.fileName(path).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && EDITABLE_EXTENSIONS.contains(name.substring(dot + 1));
    }

    /** Valid UTF-8 without NUL bytes. */
    static boolean isText(byte[] content) {
        for (byte b : content) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

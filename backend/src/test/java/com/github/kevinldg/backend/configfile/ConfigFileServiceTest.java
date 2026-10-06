package com.github.kevinldg.backend.configfile;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kevinldg.backend.audit.AuditAction;
import com.github.kevinldg.backend.audit.AuditOutcome;
import com.github.kevinldg.backend.audit.AuditService;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.FileContent;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.FileInfo;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.Overview;
import com.github.kevinldg.backend.configfile.ContainerFiles.Entry;
import com.github.kevinldg.backend.configfile.ContainerFiles.FileData;
import com.github.kevinldg.backend.configfile.ContainerFiles.Type;
import com.github.kevinldg.backend.gameserver.GameServerService;
import com.github.kevinldg.backend.gameserver.GameServerStatus;
import com.github.kevinldg.backend.gameserver.profile.MinecraftJavaProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigFileServiceTest {

    private final AuditService auditService = mock(AuditService.class);

    private static final ObjectMapper DOCKER_JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final byte[] PROPERTIES = "motd=Hello\nmax-players=10\n".getBytes(StandardCharsets.UTF_8);

    private final DockerClient dockerClient = mock(DockerClient.class, RETURNS_DEEP_STUBS);
    private final ContainerFiles containerFiles = mock(ContainerFiles.class);
    private final GameServerService gameServerService = mock(GameServerService.class);
    private final ConfigFileBackupRepository backupRepository = mock(ConfigFileBackupRepository.class);
    private final AuthenticatedUser admin = new AuthenticatedUser("a", "kevin", null, true, true, 0, Set.of());
    private ConfigFileService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ConfigFileService(dockerClient, containerFiles, gameServerService, backupRepository, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        stubContainer("mc", "minecraft-server01", true, "[]", "[\"EULA=TRUE\"]");
        MinecraftJavaProfile minecraft = new MinecraftJavaProfile();
        when(gameServerService.detect(any())).thenReturn(
                new GameServerStatus(true, "minecraft-java", "Minecraft (Java Edition)", GameServerStatus.Source.IMAGE));
        when(gameServerService.findProfile("minecraft-java")).thenReturn(Optional.of(minecraft));
        when(containerFiles.read(eq("mc"), anyString(), anyLong())).thenReturn(Optional.empty());
        when(containerFiles.read("mc", "/data/server.properties", ConfigFileService.MAX_FILE_SIZE))
                .thenReturn(Optional.of(file("/data/server.properties", Type.FILE, PROPERTIES)));
        when(backupRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void onlyGameServersHaveConfigFiles() {
        when(gameServerService.detect(any())).thenReturn(new GameServerStatus(false, null, null, GameServerStatus.Source.NONE));

        assertStatus(() -> service.getOverview("mc"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void overviewListsExistingKnownFiles() {
        when(containerFiles.read("mc", "/data/server.properties", 0))
                .thenReturn(Optional.of(new FileData("/data/server.properties", Type.FILE, 27, NOW, 0100644, 1000, 1000, null)));
        when(containerFiles.read("mc", "/data/ops.json", 0))
                .thenReturn(Optional.of(new FileData("/data/ops.json", Type.FILE, 2, NOW, 0100644, 1000, 1000, null)));

        Overview overview = service.getOverview("mc");

        assertThat(overview.roots()).containsExactly("/data");
        assertThat(overview.running()).isTrue();
        assertThat(overview.knownFiles()).extracting(FileInfo::name).containsExactly("server.properties", "ops.json");
        assertThat(overview.knownFiles()).allMatch(FileInfo::editable);
    }

    @Test
    void genericGameServersUseTheirMountsAsRoots() throws Exception {
        stubContainer("ts", "teamspeak", true, "[{\"Name\": \"abc\", \"Destination\": \"/var/ts3server\"}]", "[]");
        when(gameServerService.detect(any())).thenReturn(new GameServerStatus(true, null, "Game server", GameServerStatus.Source.MANUAL));

        assertThat(service.getOverview("ts").roots()).containsExactly("/var/ts3server");
    }

    @Test
    void listingRequiresARunningContainer() throws Exception {
        stubContainer("mc", "minecraft-server01", false, "[]", "[]");

        assertStatus(() -> service.listDirectory("mc", "/data"), HttpStatus.CONFLICT);
    }

    @Test
    void listingIsLimitedToRoots() {
        assertStatus(() -> service.listDirectory("mc", "/etc"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.listDirectory("mc", "/data/../etc"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.listDirectory("mc", "data"), HttpStatus.BAD_REQUEST);
        verify(containerFiles, never()).list(anyString(), anyString());
    }

    @Test
    void listingSortsDirectoriesFirstAndMarksEditableFiles() {
        when(containerFiles.list("mc", "/data")).thenReturn(List.of(
                new Entry("/data/server.properties", Type.FILE, 27, NOW),
                new Entry("/data/world", Type.DIRECTORY, 4096, NOW),
                new Entry("/data/server.jar", Type.FILE, 50_000_000, NOW),
                new Entry("/data/link.json", Type.SYMLINK, 11, NOW),
                new Entry("/data/huge.json", Type.FILE, 2_000_000, NOW)));

        List<FileInfo> entries = service.listDirectory("mc", "/data/").entries();

        assertThat(entries).extracting(FileInfo::name)
                .containsExactly("world", "huge.json", "link.json", "server.jar", "server.properties");
        assertThat(entries).filteredOn(FileInfo::editable).extracting(FileInfo::name).containsExactly("server.properties");
    }

    @Test
    void readReturnsContentHashAndMinecraftHint() {
        FileContent content = service.readFile("mc", "/data/server.properties", "kevin");

        assertThat(content.content()).isEqualTo("motd=Hello\nmax-players=10\n");
        assertThat(content.sha256()).isEqualTo(ConfigFileService.sha256(PROPERTIES));
        assertThat(content.hints()).singleElement().asString().contains("OVERRIDE_SERVER_PROPERTIES");
        assertThat(content.backup()).isNull();
    }

    @Test
    void noHintWhenServerPropertiesAreManagedManually() throws Exception {
        stubContainer("mc", "minecraft-server01", true, "[]", "[\"OVERRIDE_SERVER_PROPERTIES=false\"]");

        assertThat(service.readFile("mc", "/data/server.properties", "kevin").hints()).isEmpty();
    }

    @Test
    void onlyEditableTextFilesCanBeOpened() {
        assertStatus(() -> service.readFile("mc", "/data/server.jar", "kevin"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.readFile("mc", "/data/missing.json", "kevin"), HttpStatus.NOT_FOUND);

        when(containerFiles.read("mc", "/data/link.json", ConfigFileService.MAX_FILE_SIZE))
                .thenReturn(Optional.of(file("/data/link.json", Type.SYMLINK, null)));
        assertStatus(() -> service.readFile("mc", "/data/link.json", "kevin"), HttpStatus.BAD_REQUEST);

        when(containerFiles.read("mc", "/data/huge.json", ConfigFileService.MAX_FILE_SIZE))
                .thenReturn(Optional.of(file("/data/huge.json", Type.FILE, null)));
        assertStatus(() -> service.readFile("mc", "/data/huge.json", "kevin"), HttpStatus.BAD_REQUEST);

        when(containerFiles.read("mc", "/data/binary.txt", ConfigFileService.MAX_FILE_SIZE))
                .thenReturn(Optional.of(file("/data/binary.txt", Type.FILE, new byte[]{'a', 0, 'b'})));
        assertStatus(() -> service.readFile("mc", "/data/binary.txt", "kevin"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void textDetection() {
        assertThat(ConfigFileService.isText("äöü ✓".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(ConfigFileService.isText(new byte[]{(byte) 0xC3, (byte) 0x28})).isFalse();
        assertThat(ConfigFileService.isText(new byte[]{'x', 0})).isFalse();
    }

    @Test
    void saveKeepsOwnerAndStoresPreviousVersion() {
        service.saveFile("mc", "/data/server.properties", "motd=Changed\n", ConfigFileService.sha256(PROPERTIES), admin);

        verify(containerFiles).write("mc", "/data/server.properties", "motd=Changed\n".getBytes(StandardCharsets.UTF_8),
                0100644, 1000, 1000);
        ArgumentCaptor<ConfigFileBackup> backup = ArgumentCaptor.forClass(ConfigFileBackup.class);
        verify(backupRepository).save(backup.capture());
        assertThat(backup.getValue().getId()).isEqualTo("minecraft-server01:/data/server.properties");
        assertThat(backup.getValue().getContent()).isEqualTo("motd=Hello\nmax-players=10\n");
        assertThat(backup.getValue().getReplacedBy()).isEqualTo("kevin");
        assertThat(backup.getValue().getReplacedAt()).isEqualTo(NOW);
        verify(auditService).record(argThat(event -> event.action() == AuditAction.CONFIG_FILE_SAVE
                && event.outcome() == AuditOutcome.SUCCESS
                && event.target().equals("minecraft-server01")
                && event.summary().equals("Saved '/data/server.properties' in container 'minecraft-server01'")));
    }

    @Test
    void saveDetectsConcurrentChanges() {
        assertStatus(() -> service.saveFile("mc", "/data/server.properties", "x", "outdated-hash", admin), HttpStatus.CONFLICT);
        verify(containerFiles, never()).write(anyString(), anyString(), any(), anyInt(), anyLong(), anyLong());
        verify(backupRepository, never()).save(any());
        verify(auditService).record(argThat(event -> event.action() == AuditAction.CONFIG_FILE_SAVE
                && event.outcome() == AuditOutcome.FAILURE
                && event.details().get("error").startsWith("The file was changed in the meantime")));
    }

    @Test
    void saveRejectsTooLargeContent() {
        assertStatus(() -> service.saveFile("mc", "/data/server.properties", "x".repeat(ConfigFileService.MAX_FILE_SIZE + 1),
                ConfigFileService.sha256(PROPERTIES), admin), HttpStatus.BAD_REQUEST);
    }

    @Test
    void backupIsReturnedOrNotFound() {
        assertStatus(() -> service.getBackup("mc", "/data/server.properties", "kevin"), HttpStatus.NOT_FOUND);
        verify(auditService, never()).recordDeduplicated(any());

        ConfigFileBackup backup = new ConfigFileBackup();
        backup.setContent("old");
        backup.setReplacedAt(NOW);
        backup.setReplacedBy("kevin");
        when(backupRepository.findById("minecraft-server01:/data/server.properties")).thenReturn(Optional.of(backup));

        assertThat(service.getBackup("mc", "/data/server.properties", "kevin").content()).isEqualTo("old");
        assertThat(service.readFile("mc", "/data/server.properties", "kevin").backup().replacedBy()).isEqualTo("kevin");
        verify(auditService).recordDeduplicated(argThat(event -> event.action() == AuditAction.CONFIG_FILE_BACKUP_VIEW
                && event.actor().equals("kevin")));
        verify(auditService).recordDeduplicated(argThat(event -> event.action() == AuditAction.CONFIG_FILE_OPEN
                && event.summary().equals("Opened '/data/server.properties' in container 'minecraft-server01'")));
    }

    private void stubContainer(String id, String name, boolean running, String mountsJson, String envJson) throws Exception {
        InspectContainerResponse response = DOCKER_JSON.readValue("""
                {"Id": "%s", "Name": "/%s", "State": {"Running": %s},
                 "Config": {"Image": "itzg/minecraft-server", "Env": %s, "Labels": {}},
                 "Mounts": %s}
                """.formatted(id, name, running, envJson, mountsJson), InspectContainerResponse.class);
        when(dockerClient.inspectContainerCmd(id).exec()).thenReturn(response);
    }

    private static FileData file(String path, Type type, byte[] content) {
        return new FileData(path, type, content == null ? 2_000_000 : content.length, NOW, 0100644, 1000, 1000, content);
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(status));
    }
}

package com.github.kevinldg.backend.configfile;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.kevinldg.backend.configfile.ContainerFiles.Entry;
import com.github.kevinldg.backend.configfile.ContainerFiles.FileData;
import com.github.kevinldg.backend.configfile.ContainerFiles.Type;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContainerFilesTest {

    private final DockerClient dockerClient = mock(DockerClient.class, RETURNS_DEEP_STUBS);
    private final ContainerFiles files = new ContainerFiles(dockerClient);

    @Test
    void readReturnsContentAndOwnership() throws Exception {
        when(dockerClient.copyArchiveFromContainerCmd("c1", "/data/server.properties").exec())
                .thenReturn(tarWithFile("server.properties", "motd=Hi\n", 0100640, 1000, 1001));

        FileData file = files.read("c1", "/data/server.properties", 1024).orElseThrow();

        assertThat(file.type()).isEqualTo(Type.FILE);
        assertThat(new String(file.content(), StandardCharsets.UTF_8)).isEqualTo("motd=Hi\n");
        assertThat(file.mode() & 0777).isEqualTo(0640);
        assertThat(file.userId()).isEqualTo(1000);
        assertThat(file.groupId()).isEqualTo(1001);
    }

    @Test
    void contentIsOmittedAboveTheLimit() throws Exception {
        when(dockerClient.copyArchiveFromContainerCmd("c1", "/data/big.json").exec())
                .thenReturn(tarWithFile("big.json", "x".repeat(100), 0100644, 0, 0));

        FileData file = files.read("c1", "/data/big.json", 10).orElseThrow();

        assertThat(file.size()).isEqualTo(100);
        assertThat(file.content()).isNull();
    }

    @Test
    void symlinksAreNotFollowed() throws Exception {
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(archive)) {
            TarArchiveEntry link = new TarArchiveEntry("link.json", TarArchiveEntry.LF_SYMLINK);
            link.setLinkName("/etc/shadow");
            tar.putArchiveEntry(link);
            tar.closeArchiveEntry();
        }
        when(dockerClient.copyArchiveFromContainerCmd("c1", "/data/link.json").exec())
                .thenReturn(new ByteArrayInputStream(archive.toByteArray()));

        FileData file = files.read("c1", "/data/link.json", 1024).orElseThrow();

        assertThat(file.type()).isEqualTo(Type.SYMLINK);
        assertThat(file.content()).isNull();
    }

    @Test
    void missingFileIsEmpty() {
        when(dockerClient.copyArchiveFromContainerCmd("c1", "/data/missing.json").exec())
                .thenThrow(new NotFoundException("Could not find the file /data/missing.json in container c1"));

        assertThat(files.read("c1", "/data/missing.json", 1024)).isEmpty();
    }

    @Test
    void writeSendsTarWithOriginalOwnershipIntoParentDirectory() throws Exception {
        CopyArchiveToContainerCmd copy = mock(CopyArchiveToContainerCmd.class, RETURNS_SELF);
        when(dockerClient.copyArchiveToContainerCmd("c1")).thenReturn(copy);

        files.write("c1", "/data/config/paper-global.yml", "a: 1\n".getBytes(StandardCharsets.UTF_8), 0100640, 1000, 1001);

        verify(copy).withRemotePath("/data/config");
        ArgumentCaptor<InputStream> tarStream = ArgumentCaptor.forClass(InputStream.class);
        verify(copy).withTarInputStream(tarStream.capture());
        try (TarArchiveInputStream tar = new TarArchiveInputStream(tarStream.getValue())) {
            TarArchiveEntry entry = tar.getNextEntry();
            assertThat(entry.getName()).isEqualTo("paper-global.yml");
            assertThat(entry.getMode() & 0777).isEqualTo(0640);
            assertThat(entry.getLongUserId()).isEqualTo(1000);
            assertThat(entry.getLongGroupId()).isEqualTo(1001);
            assertThat(new String(tar.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("a: 1\n");
        }
        verify(copy).exec();
    }

    @Test
    void listParsesFindOutputWithoutShell() {
        ExecCreateCmd create = mock(ExecCreateCmd.class, RETURNS_SELF);
        ExecCreateCmdResponse created = mock(ExecCreateCmdResponse.class);
        when(created.getId()).thenReturn("exec-1");
        when(dockerClient.execCreateCmd("c1")).thenReturn(create);
        when(create.exec()).thenReturn(created);
        ExecStartCmd start = mock(ExecStartCmd.class, RETURNS_SELF);
        when(dockerClient.execStartCmd("exec-1")).thenReturn(start);
        when(start.exec(any())).thenAnswer(invocation -> {
            ResultCallback.Adapter<Frame> callback = invocation.getArgument(0);
            callback.onNext(new Frame(StreamType.STDOUT, ("""
                    regular file|27|1700000000|/data/server.properties
                    directory|4096|1700000001|/data/world
                    symbolic link|11|1700000002|/data/a|b.json
                    """).getBytes(StandardCharsets.UTF_8)));
            callback.onComplete();
            return callback;
        });
        when(dockerClient.inspectExecCmd("exec-1").exec().getExitCodeLong()).thenReturn(0L);

        List<Entry> entries = files.list("c1", "/data; rm -rf /");

        verify(create).withCmd("find", "/data; rm -rf /", "-mindepth", "1", "-maxdepth", "1",
                "-exec", "stat", "-c", "%F|%s|%Y|%n", "{}", "+");
        assertThat(entries).containsExactly(
                new Entry("/data/server.properties", Type.FILE, 27, Instant.ofEpochSecond(1700000000)),
                new Entry("/data/world", Type.DIRECTORY, 4096, Instant.ofEpochSecond(1700000001)),
                new Entry("/data/a|b.json", Type.SYMLINK, 11, Instant.ofEpochSecond(1700000002)));
    }

    private static InputStream tarWithFile(String name, String content, int mode, long uid, long gid) throws Exception {
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(archive)) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry entry = new TarArchiveEntry(name);
            entry.setSize(bytes.length);
            entry.setMode(mode);
            entry.setUserId(uid);
            entry.setGroupId(gid);
            tar.putArchiveEntry(entry);
            tar.write(bytes);
            tar.closeArchiveEntry();
        }
        return new ByteArrayInputStream(archive.toByteArray());
    }

}

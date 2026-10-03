package com.github.kevinldg.backend.common;

import com.github.kevinldg.backend.container.creation.ContainerCreationProperties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PosixPathsTest {

    @Test
    void normalizesAbsolutePaths() {
        assertThat(PosixPaths.normalizeAbsolute("/srv/gameservers")).contains("/srv/gameservers");
        assertThat(PosixPaths.normalizeAbsolute("//srv/./gameservers/")).contains("/srv/gameservers");
        assertThat(PosixPaths.normalizeAbsolute("/")).contains("/");
    }

    @Test
    void rejectsRelativePathsAndParentSegments() {
        assertThat(PosixPaths.normalizeAbsolute("srv/data")).isEmpty();
        assertThat(PosixPaths.normalizeAbsolute("/srv/gameservers/../../etc")).isEmpty();
        assertThat(PosixPaths.normalizeAbsolute("C:\\data")).isEmpty();
        assertThat(PosixPaths.normalizeAbsolute(null)).isEmpty();
    }

    @Test
    void parentAndFileName() {
        assertThat(PosixPaths.parent("/data/server.properties")).isEqualTo("/data");
        assertThat(PosixPaths.parent("/data")).isEqualTo("/");
        assertThat(PosixPaths.fileName("/data/config/paper-global.yml")).isEqualTo("paper-global.yml");
    }

    @Test
    void isBelowRequiresAStrictSubdirectory() {
        assertThat(PosixPaths.isBelow("/srv/gameservers/mc", "/srv/gameservers")).isTrue();
        assertThat(PosixPaths.isBelow("/srv/gameservers", "/srv/gameservers")).isFalse();
        assertThat(PosixPaths.isBelow("/srv/gameservers-evil/mc", "/srv/gameservers")).isFalse();
    }

    @Test
    void bindMountRootIsValidatedOnStartup() {
        assertThat(new ContainerCreationProperties("/srv/gameservers/").bindMountRoot()).isEqualTo("/srv/gameservers");
        assertThat(new ContainerCreationProperties("").bindMountsEnabled()).isFalse();
        assertThat(new ContainerCreationProperties(null).bindMountsEnabled()).isFalse();
        assertThatThrownBy(() -> new ContainerCreationProperties("/")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ContainerCreationProperties("relative/dir")).isInstanceOf(IllegalStateException.class);
    }
}

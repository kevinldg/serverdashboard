package com.github.kevinldg.backend.container.creation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * @param bindMountRoot host directory below which bind mounts are allowed (e.g. {@code /srv/gameservers});
 *                      empty disables bind mounts. Must be an absolute path other than {@code /}.
 */
@ConfigurationProperties("app.containers")
public record ContainerCreationProperties(String bindMountRoot) {

    public ContainerCreationProperties {
        if (StringUtils.hasText(bindMountRoot)) {
            String configured = bindMountRoot;
            bindMountRoot = PosixPaths.normalizeAbsolute(configured.trim())
                    .filter(normalized -> !normalized.equals("/"))
                    .orElseThrow(() -> new IllegalStateException(
                            "app.containers.bind-mount-root must be an absolute directory other than '/': " + configured));
        } else {
            bindMountRoot = null;
        }
    }

    public boolean bindMountsEnabled() {
        return bindMountRoot != null;
    }
}

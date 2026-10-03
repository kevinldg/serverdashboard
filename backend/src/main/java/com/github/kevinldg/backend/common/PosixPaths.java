package com.github.kevinldg.backend.common;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Path handling for paths on the Docker host and inside containers (always Linux), independent of the operating
 * system the backend runs on.
 */
public final class PosixPaths {

    private static final Pattern SEPARATORS = Pattern.compile("/+");

    private PosixPaths() {
    }

    /**
     * Normalizes an absolute path ({@code //a/./b/} becomes {@code /a/b}).
     *
     * @return empty if the path is not absolute or contains {@code ..} or a NUL character
     */
    public static Optional<String> normalizeAbsolute(String path) {
        if (path == null || !path.startsWith("/") || path.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        StringBuilder normalized = new StringBuilder();
        for (String segment : SEPARATORS.split(path)) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                return Optional.empty();
            }
            normalized.append('/').append(segment);
        }
        return Optional.of(normalized.isEmpty() ? "/" : normalized.toString());
    }

    /** Whether {@code path} is strictly below {@code root} (both normalized). */
    public static boolean isBelow(String path, String root) {
        return path.startsWith(root.equals("/") ? "/" : root + "/") && !path.equals(root);
    }

    /** The parent directory of a normalized absolute path. */
    public static String parent(String path) {
        int separator = path.lastIndexOf('/');
        return separator <= 0 ? "/" : path.substring(0, separator);
    }

    /** The last segment of a normalized absolute path. */
    public static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}

package com.github.kevinldg.backend.container;

import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.kevinldg.backend.container.ContainerLogsResponse.LogLine;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Splits Docker log frames into lines per stream (stdout/stderr) and parses them.
 * <p>
 * Splitting happens on bytes, so multi-byte characters spanning two frames are decoded correctly.
 * ANSI escape sequences (colors) are removed. Not thread-safe; callers synchronize.
 */
class LogLineSplitter {

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]");

    private final Map<StreamType, ByteArrayOutputStream> buffers = new EnumMap<>(StreamType.class);
    private final Consumer<LogLine> lineConsumer;

    LogLineSplitter(Consumer<LogLine> lineConsumer) {
        this.lineConsumer = lineConsumer;
    }

    void accept(Frame frame) {
        ByteArrayOutputStream buffer = buffers.computeIfAbsent(frame.getStreamType(), type -> new ByteArrayOutputStream());
        for (byte b : frame.getPayload()) {
            if (b == '\n') {
                emit(frame.getStreamType(), buffer);
            } else {
                buffer.write(b);
            }
        }
    }

    /** Emits incomplete last lines. */
    void flush() {
        buffers.forEach((type, buffer) -> {
            if (buffer.size() > 0) {
                emit(type, buffer);
            }
        });
    }

    private void emit(StreamType streamType, ByteArrayOutputStream buffer) {
        String raw = ANSI_ESCAPE.matcher(buffer.toString(StandardCharsets.UTF_8)).replaceAll("");
        buffer.reset();
        if (raw.endsWith("\r")) {
            raw = raw.substring(0, raw.length() - 1);
        }

        // With timestamps enabled, Docker prefixes each line with an RFC 3339 timestamp and a space.
        String timestamp = null;
        String message = raw;
        int space = raw.indexOf(' ');
        if (space > 10 && raw.charAt(4) == '-' && raw.charAt(10) == 'T') {
            timestamp = raw.substring(0, space);
            message = raw.substring(space + 1);
        }
        lineConsumer.accept(new LogLine(timestamp, streamType == StreamType.STDERR ? "stderr" : "stdout", message));
    }
}

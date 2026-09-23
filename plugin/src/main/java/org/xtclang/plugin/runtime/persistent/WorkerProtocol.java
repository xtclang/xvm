package org.xtclang.plugin.runtime.persistent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.xtclang.plugin.runtime.DirectCompileRequest;
import org.xtclang.plugin.runtime.DirectRunRequest;
import org.xtclang.plugin.runtime.DirectTestRequest;

/**
 * Versioned, bounded local protocol. No Java object deserialization or Gradle objects cross this
 * boundary. Authenticate a connection before reading requests; serialize writes on each connection.
 */
final class WorkerProtocol {
    static final int VERSION = 1;
    static final int RUN = 1;
    static final int STOP = 2;
    static final int OUT = 3;
    static final int ERR = 4;
    static final int RESULT = 5;
    static final int FAILURE = 6;
    private static final int MAX_TEXT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_LIST_SIZE = 100_000;

    private WorkerProtocol() {}

    static void text(final DataOutputStream out, final String value) throws IOException {
        if (value == null) {
            out.writeInt(-1);
            return;
        }
        final var bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IOException("Worker message exceeds the protocol limit");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    static String text(final DataInputStream in) throws IOException {
        final int length = in.readInt();
        if (length == -1) {
            return null;
        }
        if (length < 0 || length > MAX_TEXT_BYTES) {
            throw new IOException("Invalid worker message length: " + length);
        }
        final var bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Truncated worker message");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void strings(final DataOutputStream out, final List<String> values) throws IOException {
        if (values.size() > MAX_LIST_SIZE) {
            throw new IOException("Worker list exceeds the protocol limit");
        }
        out.writeInt(values.size());
        for (final var value : values) {
            text(out, value);
        }
    }

    static List<String> strings(final DataInputStream in) throws IOException {
        final int count = in.readInt();
        if (count < 0 || count > MAX_LIST_SIZE) {
            throw new IOException("Invalid worker list size: " + count);
        }
        final var values = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            final var value = text(in);
            if (value == null) {
                throw new IOException("Null worker list entry");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    static void files(final DataOutputStream out, final List<File> values) throws IOException {
        strings(out, values.stream().map(File::getAbsolutePath).toList());
    }

    static List<File> files(final DataInputStream in) throws IOException {
        return strings(in).stream().map(File::new).toList();
    }

    private static void file(final DataOutputStream out, final File file) throws IOException {
        text(out, file == null ? null : file.getAbsolutePath());
    }

    private static File file(final DataInputStream in) throws IOException {
        final var value = text(in);
        return value == null ? null : new File(value);
    }

    static void request(final DataOutputStream out, final Object request) throws IOException {
        switch (request) {
            case DirectCompileRequest r -> {
                out.writeInt(1);
                file(out, r.projectDir());
                file(out, r.stdoutFile());
                file(out, r.stderrFile());
                file(out, r.outputDir());
                file(out, r.resourceDir());
                files(out, r.modulePath());
                files(out, r.sourceFiles());
                out.writeBoolean(r.rebuild());
                out.writeBoolean(r.showVersion());
                out.writeBoolean(r.verbose());
                out.writeBoolean(r.disableWarnings());
                out.writeBoolean(r.strict());
                out.writeBoolean(r.qualifiedOutputName());
                text(out, r.xtcVersion());
            }
            case DirectRunRequest r -> {
                out.writeInt(2);
                file(out, r.projectDir());
                file(out, r.stdoutFile());
                file(out, r.stderrFile());
                files(out, r.modulePath());
                out.writeBoolean(r.showVersion());
                out.writeBoolean(r.verbose());
                out.writeBoolean(r.jit());
                text(out, r.moduleName());
                text(out, r.methodName());
                strings(out, r.moduleArgs());
            }
            case DirectTestRequest r -> {
                out.writeInt(3);
                file(out, r.projectDir());
                file(out, r.stdoutFile());
                file(out, r.stderrFile());
                file(out, r.outputDir());
                files(out, r.modulePath());
                out.writeBoolean(r.showVersion());
                out.writeBoolean(r.verbose());
                out.writeBoolean(r.jit());
                text(out, r.moduleName());
                text(out, r.methodName());
                strings(out, r.moduleArgs());
            }
            default -> throw new IOException("Unsupported worker request");
        }
    }

    static Object request(final DataInputStream in) throws IOException {
        return switch (in.readInt()) {
            case 1 -> new DirectCompileRequest(file(in), file(in), file(in), file(in), file(in),
                files(in), files(in), in.readBoolean(), in.readBoolean(), in.readBoolean(),
                in.readBoolean(), in.readBoolean(), in.readBoolean(), text(in));
            case 2 -> new DirectRunRequest(file(in), file(in), file(in), files(in),
                in.readBoolean(), in.readBoolean(), in.readBoolean(), text(in), text(in), strings(in));
            case 3 -> new DirectTestRequest(file(in), file(in), file(in), file(in), files(in),
                in.readBoolean(), in.readBoolean(), in.readBoolean(), text(in), text(in), strings(in));
            default -> throw new IOException("Unknown worker request type");
        };
    }
}

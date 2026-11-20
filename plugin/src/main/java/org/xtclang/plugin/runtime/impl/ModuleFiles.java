package org.xtclang.plugin.runtime.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What the compiles of one build have read from module files, each kept with the digest of the content that it was
 * read from. A compile that finds a file with the same content again gets what an earlier compile read; a file whose
 * content changed, or whose earlier value is stale, is read again. The content decides rather than the file's time and
 * size, which a library rebuilt within the same second can keep.
 *
 * <p>Not thread-safe: the compiles that share it run one at a time.
 *
 * @param <T> what a module file is read into
 */
final class ModuleFiles<T> {
    private final Map<File, Read<T>> reads = new HashMap<>();
    private final Reader<T> reader;
    private final Predicate<T> stale;
    private int fileReads;
    private int reuses;

    /**
     * @param reader reads a module file
     * @param stale  tells whether what an earlier compile read may no longer be used, as when a compile changed it
     */
    ModuleFiles(final Reader<T> reader, final Predicate<T> stale) {
        this.reader = reader;
        this.stale = stale;
    }

    /**
     * Returns what the given module file holds: what an earlier compile read from the same content, or else what it
     * holds now.
     *
     * @param file a module file
     * @return what the file holds
     * @throws IOException if the file cannot be read
     */
    T get(final File file) throws IOException {
        final var key = file.toPath().toAbsolutePath().normalize().toFile();
        final var digest = digest(Files.readAllBytes(key.toPath()));
        final var read = reads.get(key);
        if (read != null && Arrays.equals(read.digest(), digest) && !stale.test(read.value())) {
            reuses++;
            return read.value();
        }
        final var value = reader.read(key);
        reads.put(key, new Read<>(digest, value));
        fileReads++;
        return value;
    }

    /**
     * @return how many times a module file was read
     */
    int fileReads() {
        return fileReads;
    }

    /**
     * @return how many times what an earlier compile read was used again
     */
    int reuses() {
        return reuses;
    }

    private static byte[] digest(final byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }

    /**
     * Reads a module file.
     *
     * @param <T> what the file is read into
     */
    @FunctionalInterface
    interface Reader<T> {
        T read(File file) throws IOException;
    }

    private record Read<T>(byte[] digest, T value) {
    }
}

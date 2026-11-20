package org.xtclang.plugin.runtime.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleFilesTest {
    private final List<File> read = new ArrayList<>();

    @Test
    void aFileWithTheSameContentIsReadOnce(@TempDir final Path dir) throws IOException {
        final var file = Files.writeString(dir.resolve("json.xtc"), "json").toFile();
        final var files = new ModuleFiles<>(this::read, value -> false);

        final var first = files.get(file);
        final var second = files.get(file);

        assertSame(first, second);
        assertEquals(1, read.size());
        assertEquals(1, files.fileReads());
        assertEquals(1, files.reuses());
    }

    @Test
    void aFileWhoseContentChangedIsReadAgainEvenWithTheSameTimeAndSize(@TempDir final Path dir) throws IOException {
        final var path = Files.writeString(dir.resolve("json.xtc"), "json");
        final var time = Files.getLastModifiedTime(path);
        final var files = new ModuleFiles<>(this::read, value -> false);
        final var first = files.get(path.toFile());

        // A library rebuilt within the same second can keep both
        Files.writeString(path, "JSON");
        Files.setLastModifiedTime(path, time);
        final var second = files.get(path.toFile());

        assertEquals(time, Files.getLastModifiedTime(path));
        assertNotSame(first, second);
        assertEquals("JSON", second.content());
        assertEquals(2, files.fileReads());
        assertEquals(0, files.reuses());
    }

    @Test
    void aStaleValueIsReadAgain(@TempDir final Path dir) throws IOException {
        final var file = Files.writeString(dir.resolve("json.xtc"), "json").toFile();
        final var files = new ModuleFiles<>(this::read, value -> true);

        final var first = files.get(file);
        final var second = files.get(file);

        assertNotSame(first, second);
        assertEquals(2, files.fileReads());
    }

    @Test
    void theSameFileNamedAnotherWayIsTheSameFile(@TempDir final Path dir) throws IOException {
        final var file = Files.writeString(dir.resolve("json.xtc"), "json").toFile();
        final var files = new ModuleFiles<>(this::read, value -> false);

        files.get(file);
        files.get(dir.resolve("lib").resolve("..").resolve("json.xtc").toFile());

        assertEquals(1, files.fileReads());
    }

    private Content read(final File file) throws IOException {
        read.add(file);
        return new Content(Files.readString(file.toPath()), FileTime.fromMillis(file.lastModified()));
    }

    private record Content(String content, FileTime time) {
    }
}

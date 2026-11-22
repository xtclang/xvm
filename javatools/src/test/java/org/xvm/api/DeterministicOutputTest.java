package org.xvm.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;

import org.xvm.tool.Console;
import org.xvm.tool.Launcher;

/**
 * The compiler's output depends only on its input: compiling the same source twice in one JVM gives the
 * same module bytes. A variable named {@code _} gets a name of its own, numbered within its method, and a
 * number shared by every compile in the JVM would make each compile's bytes differ, so that every module
 * compiled after it would be out of date and miss the build cache. Compiling it from two directories gives
 * the same bytes too: a module records neither the time nor the directory it was compiled in.
 */
class DeterministicOutputTest {
    private static final String SOURCE = """
            module Deterministic {
                void run() {
                    try {
                        check(1);
                    } catch (Exception _) {}
                    if (Int count := find()) {
                        check(count);
                    }
                }

                void other() {
                    try {
                        check(2);
                    } catch (IllegalState _) {}
                }

                conditional Int find() {
                    return True, 3;
                }

                void check(Int n) {}
            }
            """;

    @TempDir
    Path input;

    @Test
    void compilingTheSameSourceTwiceGivesTheSameBytes() throws URISyntaxException {
        var support = new EmbeddingSupport().configure(new DirRepository(xdkModules(), true), null);

        byte[] first  = bytes(compile(support));
        byte[] second = bytes(compile(support));

        assertArrayEquals(first, second, () -> "the same source compiled to different module bytes: "
                + firstDifference(first, second));
    }

    @Test
    void compilingTheSameSourceInTwoDirectoriesGivesTheSameBytes() throws IOException, URISyntaxException {
        byte[] first  = build(input.resolve("first"));
        byte[] second = build(input.resolve("second"));

        assertArrayEquals(first, second, () -> "the module records where it was compiled: "
                + firstDifference(first, second));

        var module = new FileStructure(new ByteArrayInputStream(first), false, false).getModule();
        assertNull(module.getSourceDir());
        // a class keeps its source path, relative to the directory of the module file
        var helper = (ClassStructure) module.getChild("Helper");
        assertEquals("Located/Helper.x", helper.getSourcePath().getValue());
    }

    /**
     * @return the bytes of the module that {@code xtc build} compiles from a module file under the specified
     *         directory and a class in a file of its own
     */
    private static byte[] build(Path dir) throws IOException, URISyntaxException {
        Path source = dir.resolve("Located.x");
        Path out    = dir.resolve("out");
        Files.createDirectories(dir.resolve("Located"));
        Files.writeString(source, "module Located {}\n");
        Files.writeString(dir.resolve("Located").resolve("Helper.x"), "class Helper {}\n");

        String[] args = {"-L", xdkModules().getPath(), "-o", out.toString(), source.toString()};
        var errs = new ErrorList(25);
        assertEquals(0, Launcher.launch(Launcher.CMD_BUILD, args, new Console() {}, errs),
                () -> "build failed: " + errs.getErrors());
        return Files.readAllBytes(out.resolve("Located.xtc"));
    }

    private static File xdkModules() throws URISyntaxException {
        var modules = DeterministicOutputTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        return new File(modules.toURI());
    }

    private ModuleStructure compile(EmbeddingSupport support) {
        var errs   = new ErrorList(25);
        var module = support.compile(SOURCE, new DirRepository(input.toFile(), true), errs);
        assertNotNull(module, () -> "compile failed: " + errs.getErrors());
        assertFalse(errs.hasSeriousErrors(), () -> "compile failed: " + errs.getErrors());
        return module;
    }

    /**
     * The printable text around the first byte where the two differ, from each, such as the name that
     * differs.
     */
    private static String firstDifference(byte[] first, byte[] second) {
        int at = 0;
        while (at < Math.min(first.length, second.length) && first[at] == second[at]) {
            ++at;
        }
        return "at " + at + ", \"" + text(first, at) + "\" against \"" + text(second, at) + "\"";
    }

    private static String text(byte[] bytes, int at) {
        var text = new StringBuilder();
        for (int i = Math.max(0, at - 24), end = Math.min(bytes.length, at + 24); i < end; ++i) {
            char ch = (char) (bytes[i] & 0xFF);
            text.append(ch >= ' ' && ch < 0x7F ? ch : '.');
        }
        return text.toString();
    }

    private static byte[] bytes(ModuleStructure module) {
        var out = new ByteArrayOutputStream();
        try {
            module.getFileStructure().writeTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}

package org.xvm.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.net.URISyntaxException;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ModuleStructure;

/**
 * The compiler's output depends only on its input: compiling the same source twice in one JVM gives the
 * same module bytes. A variable named {@code _} gets a name of its own, numbered within its method, and a
 * number shared by every compile in the JVM would make each compile's bytes differ, so that every module
 * compiled after it would be out of date and miss the build cache.
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
        var modules = DeterministicOutputTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        var support = new EmbeddingSupport().configure(new DirRepository(new File(modules.toURI()), true), null);

        byte[] first  = bytes(compile(support));
        byte[] second = bytes(compile(support));

        assertArrayEquals(first, second, () -> "the same source compiled to different module bytes: "
                + firstDifference(first, second));
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

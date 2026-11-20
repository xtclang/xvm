package org.xvm.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;

/**
 * A module that imports another compiles to the same bytes against two versions of it whose API
 * ({@link FileStructure#writeApi}) is the same, and the changes it can see change the API: a build may skip compiling
 * importers when the API stays the same.
 */
class ModuleApiTest {
    private static final String LIBRARY = """
            module Library {
                String describe(Int n) {
                    return n > 0 ? "positive" : "other";
                }

                interface Shape {
                    Int area();
                }

                static Int Limit = 10;
            }
            """;

    private static final String IMPORTER = """
            module Importer {
                package library import Library;

                String run() {
                    return library.describe(library.Limit);
                }
            }
            """;

    @TempDir
    Path work;

    private EmbeddingSupport support;

    @BeforeEach
    void configure() throws URISyntaxException {
        var modules = ModuleApiTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        support = new EmbeddingSupport().configure(new DirRepository(new File(modules.toURI()), true), null);
    }

    @Test
    void aChangeInsideAMethodBodyKeepsTheApiAndTheImportersBytes() throws IOException {
        String changed = LIBRARY.replace("""
                        return n > 0 ? "positive" : "other";
                """, """
                        // a comment, another statement and other strings
                        if (n == 42) {
                            return "the answer";
                        }
                        return n > 0 ? "more than nothing" : "nothing at all";
                """);
        byte[] first   = bytes(compile(LIBRARY, null));
        byte[] second  = bytes(compile(changed, null));
        assertFalse(Arrays.equals(first, second), "the change is in the library's bytes");

        assertArrayEquals(api(first), api(second));
        assertArrayEquals(bytes(compileAgainst(first)), bytes(compileAgainst(second)),
                "the importer compiles to the same bytes against both");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("visibleChanges")
    void aChangeAnImporterCanSeeChangesTheApi(String change, String target, String replacement) throws IOException {
        String changed = LIBRARY.replace(target, replacement);
        assertFalse(changed.equals(LIBRARY), "the change applies");

        byte[] before = api(bytes(compile(LIBRARY, null)));
        byte[] after  = api(bytes(compile(changed, null)));
        assertFalse(Arrays.equals(before, after), change + " changes the API");
    }

    static Stream<Arguments> visibleChanges() {
        return Stream.of(
                Arguments.of("a changed signature", "String describe(Int n)", "String describe(Int n, Int base = 0)"),
                Arguments.of("an interface method that gains a default body", "Int area();",
                        "Int area() {\n            return 0;\n        }"),
                Arguments.of("a changed constant", "static Int Limit = 10;", "static Int Limit = 11;"),
                Arguments.of("a new method", "static Int Limit = 10;",
                        "static Int Limit = 10;\n\n    void reset() {}"));
    }

    /**
     * @return the importer, compiled against the specified library module file
     */
    private ModuleStructure compileAgainst(byte[] library) throws IOException {
        Path dir = Files.createTempDirectory(work, "library");
        Files.write(dir.resolve("Library.xtc"), library);
        return compile(IMPORTER, dir);
    }

    private ModuleStructure compile(String source, Path modules) {
        var errs   = new ErrorList(25);
        var module = support.compile(source, modules == null ? null : new DirRepository(modules.toFile(), true), errs);
        assertNotNull(module, () -> "compile failed: " + errs.getErrors());
        assertFalse(errs.hasSeriousErrors(), () -> "compile failed: " + errs.getErrors());
        return module;
    }

    /**
     * @return the API of the specified module file
     */
    private static byte[] api(byte[] module) throws IOException {
        var out = new ByteArrayOutputStream();
        new FileStructure(new ByteArrayInputStream(module), false, false).writeApiTo(out);
        return out.toByteArray();
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

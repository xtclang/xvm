package org.xvm.asm;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ratchet for the stage-3 {@code FrozenArray.unsafeArray()} escape hatch: the hatch exists so
 * hot consumers (hashing, serialization, arraycopy, the JIT build plane) stay zero-copy, and
 * every use is deliberately greppable. This test pins the ceiling so the count can only go
 * DOWN: converting an escape site to wrapper operations (size/get/iteration/copy) lowers the
 * ceiling; adding a new escape above the ceiling fails here and demands justification in the
 * failing site itself. The ceiling is a count, not a site list, so unrelated refactors that
 * move an escape between files do not churn this test.
 */
public class FrozenArrayEscapeRatchetTest {
    /**
     * The current escape count. LOWER freely as sites convert; never raise without a reviewed
     * reason recorded at the new call site.
     */
    private static final int ESCAPE_CEILING = 115;

    @Test
    public void unsafeArrayEscapesOnlyShrink() throws IOException {
        Path root = sourceRoot();
        int  cEscapes;
        try (Stream<Path> stream = Files.walk(root)) {
            cEscapes = stream.filter(p -> p.toString().endsWith(".java"))
                    .mapToInt(FrozenArrayEscapeRatchetTest::countEscapes)
                    .sum();
        }

        assertTrue(cEscapes <= ESCAPE_CEILING,
                "unsafeArray() escapes grew from " + ESCAPE_CEILING + " to " + cEscapes
                        + "; either convert the new site to wrapper operations or lower is the"
                        + " only direction this ceiling moves");
    }

    private static int countEscapes(Path path) {
        try {
            int c = 0;
            for (String line : Files.readString(path).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//")) {
                    continue;
                }
                for (int i = line.indexOf(".unsafeArray()"); i >= 0;
                        i = line.indexOf(".unsafeArray()", i + 1)) {
                    ++c;
                }
            }
            return c;
        } catch (IOException e) {
            throw new IllegalStateException(path.toString(), e);
        }
    }

    private static Path sourceRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path project = cwd.resolve("src/main/java");
        if (Files.exists(project.resolve("org/xvm/asm/ConstantPool.java"))) {
            return project;
        }
        return cwd.resolve("javatools/src/main/java");
    }
}

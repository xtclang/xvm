package org.xvm.asm;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ratchet for the {@code unsafeArray()} escape hatch across the whole frozen family -
 * {@code FrozenArray} plus the {@code FrozenByteArray}/{@code FrozenCharArray}/
 * {@code FrozenIntArray} primitive siblings. The hatch exists so hot consumers (hashing,
 * serialization, arraycopy, the JIT build plane) stay zero-copy, and every use is deliberately
 * greppable. This test pins the ceiling so the count can only go DOWN: converting an escape
 * site to wrapper operations (size/get/iteration/copy) lowers the ceiling; adding a new escape
 * above the ceiling fails here and demands justification in the failing site itself. The
 * ceiling is a count, not a site list, so unrelated refactors that move an escape between files
 * do not churn this test.
 *
 * <p><b>The ceiling was raised once, 115 -> 135, on 2026-08-28</b>, when stage 4 closed the nine
 * primitive raw-array escapes that {@code FrozenArray<T>} could not express. Read that increase
 * carefully: it means the metric became MORE honest, not that exposure grew. Before, a
 * {@code public byte[] getValue()} on a pool-interned constant handed every consumer an
 * unbounded, UNCOUNTED mutable alias of shared storage. After, the payload is a
 * {@code FrozenByteArray}, its consumers use wrapper operations, and the handful of remaining
 * zero-copy reads inside the owning class are counted here. Net exposure fell while the number
 * rose. The 20 added escapes are: 3 each in {@code UInt8ArrayConstant}, {@code FPNConstant},
 * and {@code Float128Constant} (hash, serialize, hex-render of the payload they own); 6 in
 * {@code xString} (compare x2 on one line, concat arraycopy x2, hash, char scan); 2 in {@code Disassembler} and 2
 * in {@code xTerminalConsole} (read-only scans in tools/IO); and 1 in {@code AssertV}
 * ({@code StringBuilder.append(char[], int, int)}). This is the ONLY sanctioned raise; the
 * direction is down from here.
 */
public class FrozenArrayEscapeRatchetTest {
    /**
     * The current escape count. LOWER freely as sites convert; never raise without a reviewed
     * reason recorded at the new call site AND in this class's javadoc.
     */
    private static final int ESCAPE_CEILING = 135;

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

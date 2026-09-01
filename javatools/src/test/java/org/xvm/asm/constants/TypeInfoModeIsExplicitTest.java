package org.xvm.asm.constants;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * A source-shape gate: nothing may ask for a TypeInfo by handing it a listener that means "do not
 * listen".
 *
 * <p>{@code ensureTypeInfo(errs)} both builds a type and validates it, so its listener parameter was
 * doing double duty - it named a sink, but callers were really using it to say which of the two
 * operations they wanted. {@code ensureTypeInfo(ErrorListener.BLACKHOLE)} was that mode flag written
 * out longhand, at fourteen call sites. {@link TypeConstant#typeInfo()} says the same thing by being
 * a different method.
 *
 * <p>This is a shape test rather than a behavioural one because the two forms are behaviourally
 * identical - which is exactly why the old form would drift back in without something pinning it.
 *
 * <p>See docs/errorlistener/README.md section 8.
 */
public class TypeInfoModeIsExplicitTest {
    /**
     * The single legitimate occurrence: the body of {@code typeInfo()} itself, which is where the
     * idiom is now spelled once so that nowhere else has to.
     */
    private static final int ALLOWED_IN_TYPE_CONSTANT = 1;

    @Test
    public void nobodyAsksForATypeInfoWithABlackholeMode() throws IOException {
        var offenders = new ArrayList<String>();
        int inTypeConstant = 0;

        for (Path java : mainSources()) {
            List<String> lines = Files.readAllLines(java);
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).contains("ensureTypeInfo(ErrorListener.BLACKHOLE)")) {
                    continue;
                }
                if (java.endsWith(Path.of("org", "xvm", "asm", "constants", "TypeConstant.java"))) {
                    inTypeConstant++;
                } else {
                    offenders.add(java.getFileName() + ":" + (i + 1) + "  " + lines.get(i).strip());
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                () -> "use TypeConstant.typeInfo() instead of passing BLACKHOLE as a mode flag:\n  "
                        + String.join("\n  ", offenders));
        assertEquals(ALLOWED_IN_TYPE_CONSTANT, inTypeConstant,
                "exactly one occurrence is expected - the body of typeInfo() itself");
    }

    /**
     * The gate is only worth anything if it is actually reading the sources, so prove it found them.
     */
    @Test
    public void theGateIsReadingRealSources() throws IOException {
        List<Path> sources = mainSources();

        assertTrue(sources.size() > 500,
                () -> "expected the javatools main source tree, found " + sources.size() + " files");
        assertTrue(sources.stream().anyMatch(
                        p -> p.endsWith(Path.of("org", "xvm", "asm", "constants", "TypeConstant.java"))),
                "TypeConstant.java must be among the scanned sources");
    }

    private static List<Path> mainSources() throws IOException {
        Path root = sourceRoot();
        try (var paths = Files.walk(root)) {
            return paths.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static Path sourceRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        for (Path root : List.of(cwd.resolve("src/main/java"), cwd.resolve("javatools/src/main/java"))) {
            if (Files.isDirectory(root)) {
                return root;
            }
        }
        throw new IllegalStateException("Cannot locate javatools source root from " + cwd);
    }
}

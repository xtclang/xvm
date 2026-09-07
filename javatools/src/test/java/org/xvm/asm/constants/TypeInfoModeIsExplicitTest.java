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
 * operations they wanted. {@code ensureTypeInfo(<silent listener>)} was that mode flag written out
 * longhand, at fourteen call sites. {@link TypeConstant#typeInfo()} says the same thing by being a
 * different method.
 *
 * <p>This is a shape test rather than a behavioural one because the two forms are behaviourally
 * identical - which is exactly why the old form would drift back in without something pinning it.
 * It scans source text for the same reason: there is no runtime difference to assert on.
 *
 * <p>Both silent listeners are gated, for different reasons. {@code PROBE} is the right VALUE and
 * the wrong SPELLING - {@code typeInfo()} is where that idiom lives, so writing it out again puts
 * the mode back at the call site. {@code BLACKHOLE} is the wrong value outright: asking for a
 * TypeInfo is always a question, never a diagnostic with nowhere to go.
 *
 * <p>See docs/errorlistener/README.md section 8.
 */
public class TypeInfoModeIsExplicitTest {
    /**
     * The single legitimate occurrence: the body of {@code typeInfo()} itself, which is where the
     * idiom is now spelled once so that nowhere else has to.
     */
    private static final int ALLOWED_IN_TYPE_CONSTANT = 1;

    /**
     * Where the one allowed occurrence lives.
     */
    private static final Path TYPE_CONSTANT = Path.of("org", "xvm", "asm", "constants", "TypeConstant.java");

    /**
     * {@code ensureTypeInfo(ErrorListener.PROBE)} belongs in exactly one place: the body of
     * {@code typeInfo()}. Anywhere else is a caller spelling out a mode that a method name already
     * says.
     */
    @Test
    public void onlyTypeConstantSpellsOutTheComputeMode() throws IOException {
        List<String> offenders = occurrencesOf("ensureTypeInfo(ErrorListener.PROBE)", TYPE_CONSTANT);

        assertEquals(ALLOWED_IN_TYPE_CONSTANT, countIn(TYPE_CONSTANT, "ensureTypeInfo(ErrorListener.PROBE)"),
                "typeInfo() is where the compute-mode idiom is spelled, exactly once");
        assertTrue(offenders.isEmpty(),
                () -> "call typeInfo() instead of naming the mode:\n  " + String.join("\n  ", offenders));
    }

    /**
     * {@code BLACKHOLE} means "no sink is attached". Building a TypeInfo is a question, so that is
     * never the honest answer - {@code PROBE} inside {@code typeInfo()} is.
     */
    @Test
    public void nothingAsksForATypeInfoWithAnAbsentSink() throws IOException {
        List<String> offenders = occurrencesOf("ensureTypeInfo(ErrorListener.BLACKHOLE)", null);

        assertTrue(offenders.isEmpty(),
                () -> "asking for a TypeInfo is a question; use typeInfo():\n  "
                        + String.join("\n  ", offenders));
    }

    /**
     * @param needle  the source text to find
     * @param exempt  a path suffix whose hits are not offences, or null if there is no exemption
     *
     * @return "file:line" for every occurrence outside the exempt file
     */
    private static List<String> occurrencesOf(String needle, Path exempt) throws IOException {
        List<String> found = new ArrayList<>();
        for (Path src : mainSources()) {
            if (exempt != null && src.endsWith(exempt)) {
                continue;
            }
            List<String> lines = Files.readAllLines(src);
            for (int i = 0; i < lines.size(); ++i) {
                if (lines.get(i).contains(needle)) {
                    found.add(sourceRoot().relativize(src) + ":" + (i + 1));
                }
            }
        }
        return found;
    }

    private static int countIn(Path suffix, String needle) throws IOException {
        int count = 0;
        for (Path src : mainSources()) {
            if (src.endsWith(suffix)) {
                count += (int) Files.readAllLines(src).stream().filter(l -> l.contains(needle)).count();
            }
        }
        return count;
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

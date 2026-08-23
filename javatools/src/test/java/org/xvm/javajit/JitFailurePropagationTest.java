package org.xvm.javajit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards JIT host-boundary failure reporting. Generated XTC exceptions are detected by reflection
 * in {@link JitConnector}; they must become a non-zero connector result, not just console output.
 */
public class JitFailurePropagationTest {
    /**
     * Master printed generated unhandled XTC exceptions and then let {@code join()} return whatever
     * result was already stored. A previous successful invocation could therefore hide the failure.
     */
    @Test
    public void generatedUnhandledExceptionSetsNonZeroResult() throws IOException {
        var source = readString("org/xvm/javajit/JitConnector.java");
        var branch = sourceBetween(source,
                "name.startsWith(TypeSystem.ClassfileShape.Exception.prefix)",
                "} else {");

        assertTrue(branch.contains("this.result = 1;"),
                "generated XTC exceptions must keep/report a non-zero JIT result");
        assertFalse(branch.contains("catch (Throwable"),
                "exception printing fallback must not swallow arbitrary VM failures");
        assertFalse(branch.contains("ignore"),
                "ignored exception variables hide intentional fallback-only catches");
    }

    private static String readString(String source) throws IOException {
        var path = Path.of("src/main/java", source);
        return Files.readString(Files.exists(path)
                ? path
                : Path.of("javatools/src/main/java", source));
    }

    private static String sourceBetween(String source, String start, String end) {
        var ofStart = source.indexOf(start);
        var ofEnd   = source.indexOf(end, ofStart);

        assertTrue(ofStart >= 0, "missing source start marker: " + start);
        assertTrue(ofEnd > ofStart, "missing source end marker: " + end);
        return source.substring(ofStart, ofEnd);
    }
}

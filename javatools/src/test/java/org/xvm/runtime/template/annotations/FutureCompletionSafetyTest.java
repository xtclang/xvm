package org.xvm.runtime.template.annotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards Future.and completion behavior. The native future implementation must not hide async
 * failures behind asserts, and it must combine the two distinct input futures.
 */
public class FutureCompletionSafetyTest {
    /**
     * Master read the first future twice on the already-complete path and used assert-only failure
     * handling on the async path. With assertions disabled, a broken async get could continue with
     * null arguments instead of completing the combined future exceptionally.
     */
    @Test
    public void futureAndUsesBothInputsAndCompletesExceptionalPaths() throws IOException {
        var source = readString("org/xvm/runtime/template/annotations/xFuture.java");
        var and    = sourceBetween(source, "protected int invokeAndFuture", "protected int invokeOrFuture");

        assertTrue(and.contains("extractResult(frame, cfThat)"),
                "Future.and fast path must inspect the second future, not cfThis twice");
        assertFalse(and.contains("assert false"),
                "Future.and async failure handling must not depend on enabled assertions");
        assertTrue(and.contains("Thread.currentThread().interrupt();"),
                "interrupted async completion must restore interrupt status");
        assertTrue(and.contains("hAnd.complete(null, Utils.translate(f_container, e));"),
                "async get failures must complete the combined future with an XTC exception");
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

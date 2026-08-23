package org.xvm.runtime;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Guards runtime entry failure paths that must preserve their Java cause.
 */
public class RuntimeFailurePropagationTest {
    /**
     * MainContainer startup and invocation failures carry module, owner, and stack context in the
     * original exception. Master flattened that to a message string, making same-JVM startup and
     * ownership failures much harder to diagnose.
     */
    @Test
    public void mainContainerInvokePreservesFailureCause() throws IOException {
        var source       = readString("org/xvm/runtime/MainContainer.java");
        var fixedWrapper = "new RuntimeException(\"failed to run: \" + f_idModule, e)";

        assertFalse(source.contains("\". Cause: \" + e.getMessage()"),
                "MainContainer.invoke0 must not flatten the original cause to a message");
        assertTrue(source.contains(fixedWrapper),
                "MainContainer.invoke0 must preserve the original startup/invocation cause");
    }

    private static String readString(String source) throws IOException {
        var path = Path.of("src/main/java", source);
        return Files.readString(Files.exists(path)
                ? path
                : Path.of("javatools/src/main/java", source));
    }
}

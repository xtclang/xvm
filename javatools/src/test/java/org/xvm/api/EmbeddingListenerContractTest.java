package org.xvm.api;

import java.io.File;

import org.junit.jupiter.api.Test;

import org.xvm.tool.LauncherOptions.CompilerOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Null diagnostic sinks are rejected before configuration, I/O or runtime initialization. */
class EmbeddingListenerContractTest {
    @Test
    void compilationRequiresAnExplicitListener() {
        try (var session = new EmbeddingSupport()) {
            assertEquals("errs", assertThrows(NullPointerException.class,
                    () -> session.compile("module Example {}", null, null)).getMessage());
            assertEquals("errs", assertThrows(NullPointerException.class,
                    () -> session.compile(new File("Example.x"), null, null, null)).getMessage());
            assertEquals("errs", assertThrows(NullPointerException.class,
                    () -> session.compile(CompilerOptions.builder().build(), null, null)).getMessage());
        }
    }
}

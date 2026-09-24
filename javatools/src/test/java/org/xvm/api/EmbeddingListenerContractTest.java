package org.xvm.api;

import java.io.File;

import org.junit.jupiter.api.Test;

import org.xvm.asm.FileStructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Null diagnostic sinks are rejected before configuration, I/O or runtime initialization. */
class EmbeddingListenerContractTest {
    @Test
    void compilationRequiresAnExplicitListener() {
        var embedding = new EmbeddingSupport();
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> embedding.compile("module Example {}", null, null)).getMessage());
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> embedding.compile(new File("Example.x"), null, null, null)).getMessage());
    }

    @Test
    void executionRequiresAnExplicitListener() {
        var embedding = new EmbeddingSupport();
        var module = new FileStructure("Example").getModule();
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> embedding.run(module, null, null, null, null)).getMessage());
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> embedding.run(null, "Example", null, null, null, null, null, null)).getMessage());
    }
}

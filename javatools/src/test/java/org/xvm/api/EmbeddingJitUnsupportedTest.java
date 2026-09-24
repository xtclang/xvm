package org.xvm.api;

import java.nio.file.Path;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;

import org.xvm.compiler.BuildRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** JIT API placeholders reject requests before loading libraries or starting a runtime. */
class EmbeddingJitUnsupportedTest {
    @Test
    void explicitTemplateSessionIsDeferred() {
        var failure = assertThrows(UnsupportedOperationException.class,
                () -> EmbeddingSupport.create(new BuildRepository(), Path.of("unused-jitbridge.jar")));
        assertEquals(MESSAGE, failure.getMessage());
    }

    @Test
    void jitConnectorIsDeferred() {
        try (var session = new EmbeddingSupport()) {
            var failure = assertThrows(UnsupportedOperationException.class,
                    () -> session.ensureConnector(RunRequest.Backend.JIT));
            assertEquals(MESSAGE, failure.getMessage());
        }
    }

    @Test
    void jitRequestIsDeferredBeforeModuleLookup() {
        try (var session = new EmbeddingSupport()) {
            var errors = new ErrorList(10);
            var request = new RunRequest(new BuildRepository(), "Unloaded", "run", List.of(),
                    null, null, false, Map.of(), RunRequest.Backend.JIT);
            var failure = assertThrows(UnsupportedOperationException.class,
                    () -> session.run(request, errors));
            assertEquals(MESSAGE, failure.getMessage());
            assertEquals(0, errors.getErrors().size());
        }
    }

    private static final String MESSAGE = "Will be implemented separately in the JIT branch";
}

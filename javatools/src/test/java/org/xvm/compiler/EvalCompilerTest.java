package org.xvm.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that an evaluation compiler owns a usable diagnostic buffer before compilation begins. Callers must
 * not depend on a later pipeline phase to initialize reporting.
 */
class EvalCompilerTest {
    @Test
    void diagnosticsAreAvailableBeforeCompilation() {
        var compiler = new EvalCompiler(null, "return 1;");
        assertTrue(compiler.getErrors().isEmpty());
    }
}

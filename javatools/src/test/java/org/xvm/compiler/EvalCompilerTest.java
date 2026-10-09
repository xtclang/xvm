package org.xvm.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalCompilerTest {
    @Test
    void diagnosticsAreAvailableBeforeCompilation() {
        var compiler = new EvalCompiler(null, "return 1;");
        assertTrue(compiler.getErrors().isEmpty());
    }
}

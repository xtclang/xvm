package org.xvm.compiler;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;

import org.xvm.compiler.ast.TypeCompositionStatement;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cancelling registration is an ordinary incomplete outcome, not an internal compiler failure. */
class CompilerCancellationTest {
    @Test
    void cancelledRegistrationDoesNotThrowOrInventAnError() {
        var errors = new ErrorList();
        var syntax = new Parser(new Source("module Cancelled {}"), errors).parseSource();
        var module = (TypeCompositionStatement) syntax.getStatements().getLast();
        var compiler = new Compiler(module, ErrorListener.cancellable(errors, () -> true));
        assertNull(compiler.generateInitialFileStructure());
        assertTrue(errors.getErrors().isEmpty());
    }
}

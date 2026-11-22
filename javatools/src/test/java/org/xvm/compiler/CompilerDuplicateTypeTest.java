package org.xvm.compiler;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;

import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two classes of one name in one container report a duplicate name when they are registered. */
class CompilerDuplicateTypeTest {
    @Test
    void twoInlineClassesReportDuplicateName() {
        var errors = new ErrorList(20);
        var module = type("module App { class Taken {} class Taken {} }", errors);

        assertDoesNotThrow(() -> new Compiler(module, errors).generateInitialFileStructure());

        assertTrue(errors.getErrors().stream().anyMatch(error -> error.getCode().equals(Compiler.DUPLICATE_NAME)));
    }

    @Test
    void distinctSiblingClassesStillRegister() {
        var errors = new ErrorList(20);
        var module = type("""
                module App {
                    class First { class Nested {} }
                    class Second { class Nested {} }
                }
                """, errors);

        assertDoesNotThrow(() -> new Compiler(module, errors).generateInitialFileStructure());

        assertFalse(errors.hasSeriousErrors(), errors::toString);
    }

    private static StatementBlock parse(String text, ErrorList errors) {
        var block = new Parser(new Source(text), errors).parseSource();
        assertFalse(errors.hasSeriousErrors(), errors::toString);
        return block;
    }

    private static TypeCompositionStatement type(String text, ErrorList errors) {
        return assertInstanceOf(TypeCompositionStatement.class, parse(text, errors).getStatements().getFirst());
    }
}

package org.xvm.compiler;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;

import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Duplicate source types must be rejected before their identities become ambiguous composites. */
class CompilerDuplicateTypeTest {
    @Test
    void inlineAndCompanionFileClassReportDuplicateName() {
        var errors = new ErrorList(20);
        var module = type("module App { util.Taken make() = new util.Taken(); }", errors);
        var pkg = type("package util { class Taken {} }", errors);
        pkg.addEnclosed(parse("class Taken {}", errors));
        module.addEnclosed(pkg);

        assertDoesNotThrow(() -> new Compiler(module, errors).generateInitialFileStructure());

        assertTrue(errors.hasSeriousErrors());
        assertEquals(List.of(Compiler.DUPLICATE_NAME),
                errors.getErrors().stream().map(error -> error.getCode()).toList());
        var error = errors.getErrors().getFirst();
        assertEquals("Taken", error.getParams()[0]);
        assertEquals(0, error.getLine());
        assertEquals(6, error.getOffset());
        assertEquals(11, error.getEndOffset());
    }

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

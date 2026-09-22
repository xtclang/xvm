package org.xvm.compiler;

import java.time.Duration;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;

import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.MethodDeclarationStatement;
import org.xvm.compiler.ast.PropertyDeclarationStatement;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;
import org.xvm.compiler.ast.VariableDeclarationStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recovery retains real syntax without validating or inventing a replacement expression. */
public class ParserRecoveryTest {
    @Test
    public void malformedStatementRetainsItsMethodAndFollowingDeclarations() {
        ErrorList errs = new ErrorList();
        StatementBlock tree = parse("""
                module Recovery {
                    void damaged() { console.; Int kept = 1; }
                    Int after = 2;
                }
                """, errs);
        assertTrue(errs.hasError(Parser.EXPECTED_TOKEN));
        assertEquals(List.of("Recovery", "damaged", "kept", "after"), names(tree));
    }

    @Test
    public void missingBracesAndTrailingDotRetainCompletedHeadersAtActualSourceEnd() {
        for (String suffix : List.of("", "void run() {", "void run() { console.")) {
            String text = "module Recovery {\n    " + suffix;
            ErrorList errs = new ErrorList();
            StatementBlock tree = parse(text, errs);
            assertTrue(errs.hasSeriousErrors());
            assertTrue(names(tree).contains("Recovery"));
            if (!suffix.isEmpty()) {
                assertTrue(names(tree).contains("run"));
            }
            assertEquals(1, Source.calculateLine(tree.getEndPosition()));
            assertEquals(4 + suffix.length(), Source.calculateOffset(tree.getEndPosition()));
            assertEquals(1, errs.getErrors().stream()
                    .filter(err -> err.getCode().equals(Parser.UNEXPECTED_EOF)).count());
        }
    }

    @Test
    public void malformedMemberDoesNotDiscardItsSiblings() {
        ErrorList errs = new ErrorList();
        StatementBlock tree = parse("""
                module Recovery {
                    Int before = 1;
                    Int broken = ;
                    Int after = 2;
                    class Nested { Int value = 3; }
                }
                """, errs);
        assertTrue(errs.hasSeriousErrors());
        assertEquals(List.of("Recovery", "before", "after", "Nested", "value"), names(tree));
    }

    @Test
    public void recoverySkipsBalancedBodiesAndAlwaysMakesProgress() {
        ErrorList errs = new ErrorList(ErrorList.UNLIMITED);
        StatementBlock tree = parse("""
                module Recovery {
                    void run() {
                        return ;
                        Int x = ;
                        if () { Int hidden = 1; }
                        Int kept = 2;
                    }
                    void after() {}
                }
                """, errs);
        assertTrue(errs.hasSeriousErrors());
        assertEquals(List.of("Recovery", "run", "kept", "after"), names(tree));
    }

    @Test
    public void aFailedSpeculationDoesNotRecoverOrPublishItsDiagnostics() {
        ErrorList errs = new ErrorList();
        Parser parser = new Parser(new Source("module Recovery { void run() { console.; } }"), errs);
        assertThrows(CompilerException.class, () -> {
            try (Parser.Attempt ignored = parser.attempt()) {
                parser.parseTypeCompositionStatement();
            }
        });
        assertFalse(errs.hasSeriousErrors());
        assertNotNull(parser.parseSource());
        assertTrue(errs.hasError(Parser.EXPECTED_TOKEN));
    }

    @Test
    public void recoveryHonorsTheErrorBudget() {
        ErrorList errs = new ErrorList(ErrorList.FIRST_ERROR);
        assertThrows(CompilerException.class, () -> parse(
                "module Recovery { Int broken = ; Int alsoBroken = ; }", errs));
        assertEquals(1, errs.getSeriousErrorCount());
    }

    @Test
    public void validClosingBracesDoNotIncludeTrailingCommentsInTheirRanges() {
        ErrorList errs = new ErrorList();
        String text = "module Recovery { void run() {} }\n// trailing café 😀";
        StatementBlock tree = parse(text, errs);
        assertFalse(errs.hasSeriousErrors());
        assertEquals(0, Source.calculateLine(tree.getEndPosition()));
        assertEquals(text.indexOf('\n'), Source.calculateOffset(tree.getEndPosition()));
    }

    @Test
    public void unterminatedStringsFinishAfterOneDiagnosticEvenWithoutAnAbortBudget() {
        for (String text : List.of("\"", "\"café 😀", "$\"", "$\"unfinished")) {
            ErrorList errs = new ErrorList(ErrorList.UNLIMITED);
            AtomicInteger reports = new AtomicInteger();
            Lexer lexer = new Lexer(new Source(text), ErrorListener.collecting(error -> {
                // Fail on a repeat instead of leaving an infinite lexer running after a timeout.
                assertEquals(1, reports.incrementAndGet());
                errs.log(error);
            }));
            Token token = lexer.next();
            assertFalse(lexer.hasNext());
            assertEquals(text.length(), Source.calculateOffset(token.getEndPosition()));
            assertTrue(errs.hasError(Lexer.STRING_NO_TERM));
            assertEquals(1, reports.get());
        }
    }

    private StatementBlock parse(String text, ErrorList errs) {
        return assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> new Parser(new Source(text, "Recovery.x"), errs).parseSource());
    }

    private List<String> names(AstNode node) {
        List<String> names = new ArrayList<>();
        switch (node) {
        case TypeCompositionStatement type -> names.add(type.getName());
        case MethodDeclarationStatement method -> names.add(method.getName());
        case PropertyDeclarationStatement property -> names.add(property.getName());
        case VariableDeclarationStatement variable -> names.add(variable.getNameToken().getValueText());
        default -> {}
        }
        node.children().forEachRemaining(child -> names.addAll(names(child)));
        return names;
    }
}

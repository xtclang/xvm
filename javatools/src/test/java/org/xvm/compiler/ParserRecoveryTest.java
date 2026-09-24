package org.xvm.compiler;

import java.time.Duration;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;

import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.IncompleteStatement;
import org.xvm.compiler.ast.MethodDeclarationStatement;
import org.xvm.compiler.ast.PropertyDeclarationStatement;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;
import org.xvm.compiler.ast.VariableDeclarationStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

    @Test
    public void partialParsingIsOptInAndClonesOwnTheirIncompleteChildren() {
        String text = "module Recovery { void run() { console.print(1, ";
        StatementBlock ordinary = parse(text, new ErrorList());
        assertTrue(nodes(ordinary).stream().noneMatch(IncompleteStatement.class::isInstance));

        ErrorList errs = new ErrorList();
        StatementBlock partial = Parser.forPartialAnalysis(new Source(text), errs).parseSource();
        assertEquals(List.of(Parser.UNEXPECTED_EOF), errs.getErrors().stream().map(ErrorListener.ErrorInfo::getCode).toList());
        IncompleteStatement site = nodes(partial).stream().filter(IncompleteStatement.class::isInstance)
                .map(IncompleteStatement.class::cast).findFirst().orElseThrow();
        IncompleteStatement clone = (IncompleteStatement) site.clone();
        assertNotSame(site.getTarget(), clone.getTarget());
        assertNotSame(site.getReceiver().orElseThrow(), clone.getReceiver().orElseThrow());
        assertNotSame(site.getArguments().getFirst(), clone.getArguments().getFirst());
        assertEquals(site.getEndPosition(), clone.getEndPosition());
        assertEquals(site.getSeparators(), clone.getSeparators());
        assertFalse(clone.getReceiver().orElseThrow().isValidated());
    }

    @Test
    public void partialParserSpeculationCannotPublishAnIncompleteSite() {
        ErrorList errs = new ErrorList();
        Parser parser = Parser.forPartialAnalysis(new Source("module Recovery { void run() { console."), errs);
        assertThrows(CompilerException.class, () -> {
            try (Parser.Attempt ignored = parser.attempt()) {
                parser.parseTypeCompositionStatement();
            }
        });
        assertFalse(errs.hasSeriousErrors());
        assertEquals(1, nodes(parser.parseSource()).stream().filter(IncompleteStatement.class::isInstance).count());
    }

    @Test
    public void missingCursorDelimitersRetainOriginalRangesAndFollowingDeclarations() {
        for (String expression : List.of("((value.si", "work((value.si", "values[value.si")) {
            String prefix = "module Recovery { Int run(String value) { return " + expression;
            String text   = prefix + "; } Int later = 42; }";
            Source source = new Source(text);
            prefix.chars().forEach(_ -> source.next());
            long cursor = source.getPosition();
            source.reset();
            ErrorList errs = new ErrorList();
            StatementBlock tree = Parser.forPartialAnalysis(source, cursor, errs).parseSource();
            assertTrue(names(tree).containsAll(List.of("Recovery", "run", "later")));
            assertTrue(errs.getErrors().stream()
                    .allMatch(error -> error.getCode().equals(Parser.INCOMPLETE_EXPRESSION)));
            IncompleteStatement site = nodes(tree).stream().filter(IncompleteStatement.class::isInstance)
                    .map(IncompleteStatement.class::cast).filter(node -> !node.isCall())
                    .findFirst().orElseThrow();
            assertEquals(cursor, site.getEndPosition());
            assertEquals(prefix.length() - 2, Source.calculateOffset(
                    site.getMemberName().orElseThrow().getStartPosition()));
            assertEquals(text, source.toRawString());

            ErrorList ordinary = new ErrorList();
            assertTrue(nodes(parse(text, ordinary)).stream().noneMatch(IncompleteStatement.class::isInstance));
            assertTrue(ordinary.hasSeriousErrors());
        }
    }

    @Test
    public void missingDelimiterRecoveryHonorsBudgetsCancellationAndSpeculation() {
        String prefix = "module Recovery { Int run(String value) { return ((value.";
        String text = prefix + "; } }";
        Source source = new Source(prefix);
        prefix.chars().forEach(_ -> source.next());
        long cursor = source.getPosition();
        ErrorList budget = new ErrorList(ErrorList.FIRST_ERROR);
        assertThrows(CompilerException.class, () ->
                Parser.forPartialAnalysis(new Source(text), cursor, budget).parseSource());
        assertEquals(1, budget.getSeriousErrorCount());

        ErrorListener cancelled = ErrorListener.cancellable(new ErrorList(), () -> true);
        assertThrows(CompilerException.class, () ->
                Parser.forPartialAnalysis(new Source(text), cursor, cancelled).parseSource());

        ErrorList errs = new ErrorList();
        Parser parser = Parser.forPartialAnalysis(new Source(text), cursor, errs);
        assertThrows(CompilerException.class, () -> {
            try (Parser.Attempt ignored = parser.attempt()) {
                parser.parseTypeCompositionStatement();
            }
        });
        assertFalse(errs.hasSeriousErrors());
        assertEquals(1, nodes(parser.parseSource()).stream().filter(IncompleteStatement.class::isInstance).count());
    }

    private List<AstNode> nodes(AstNode root) {
        List<AstNode> nodes = new ArrayList<>(List.of(root));
        for (int i = 0; i < nodes.size(); ++i) {
            nodes.get(i).children().forEachRemaining(nodes::add);
        }
        return nodes;
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

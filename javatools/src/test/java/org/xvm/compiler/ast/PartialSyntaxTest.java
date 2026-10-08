package org.xvm.compiler.ast;

import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Parser;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.partial.IncompleteExpression;
import org.xvm.compiler.ast.partial.IncompleteStatement;
import org.xvm.compiler.ast.partial.PartialSyntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Syntax searches must preserve scope boundaries and leave both source and trial ownership intact. */
public class PartialSyntaxTest {
    @Test
    public void deferredAnonymousBodiesAreVisibleToSyntaxWithoutBecomingCompilerChildren() {
        var body = new StatementBlock(List.of(new ExpressionStatement(hole(20))));
        var creation = new NewExpression(null, token(Id.NEW), null, List.of(), -1, body, 40);
        var originalParent = body.getParent();
        assertSame(body, creation.getUnregisteredBody().orElseThrow());
        assertEquals(List.of(body), PartialSyntax.children(creation).toList());
        assertFalse(creation.children().hasNext());
        assertTrue(PartialSyntax.containsAt(creation, 20));
        assertSame(originalParent, body.getParent());
        for (NewExpression copy : List.of(creation.copyTree(), (NewExpression) creation.clone())) {
            assertNotSame(body, copy.getUnregisteredBody().orElseThrow());
            assertTrue(PartialSyntax.containsAt(copy, 20));
        }
        assertTrue(PartialSyntax.valueCursor(creation).isEmpty());
    }

    @Test
    public void containmentIncludesTheRootAndNestedSitesAtTheirExactCursor() {
        var value = hole(20);
        var call = call(value);

        assertFalse(PartialSyntax.contains(null));
        assertFalse(PartialSyntax.containsAt(null, 20));
        assertFalse(PartialSyntax.contains(name("complete")));
        assertTrue(PartialSyntax.contains(value.getSite()));
        assertTrue(PartialSyntax.containsAt(value.getSite(), 20));
        assertTrue(PartialSyntax.containsAt(call, 20));
        assertTrue(PartialSyntax.containsAt(call, call.getEndPosition()));
        assertFalse(PartialSyntax.containsAt(call, 19));
        assertFalse(PartialSyntax.containsAt(call, 21));
    }

    @Test
    public void argumentSelectionPreservesTheSlotThroughLabelsGroupsAndCompoundValues() {
        List.<UnaryOperator<Expression>>of(
                UnaryOperator.identity(),
                value -> new LabeledExpression(token("second"), value),
                value -> new ParenthesizedExpression(value, 10, 30),
                value -> new LabeledExpression(token("second"), new ParenthesizedExpression(
                        new RelOpExpression(name("left"), token(Id.ADD), value), 10, 30)))
                .forEach(wrap -> {
                    var value = hole(20);
                    var argument = wrap.apply(value);
                    var call = call(argument);
                    var before = call.toDumpString();
                    var selected = PartialSyntax.argument(call).orElseThrow();

                    assertEquals(1, selected.index());
                    assertSame(value.getSite(), selected.cursor());
                    assertSame(selected.cursor(), PartialSyntax.valueCursor(argument).orElseThrow());
                    assertSame(call, selected.cursor().getArgumentCall().orElseThrow());
                    assertSame(call, argument.getParent());
                    assertSame(value, selected.cursor().getParent());
                    assertEquals(selected, PartialSyntax.argument(call).orElseThrow());
                    assertEquals(before, call.toDumpString());
                    assertFalse(value.isValidated());
                });
    }

    @Test
    public void containmentCrossesBoundariesThatArgumentSelectionMustNotCross() {
        List.<UnaryOperator<Expression>>of(
                value -> new InvocationExpression(name("nested"), false, List.of(value), 40),
                value -> new NewExpression(null, token(Id.NEW), null, List.of(value), -1, null, 40),
                value -> new LambdaExpression(List.of(), token(Id.LAMBDA),
                        new StatementBlock(List.of(new ExpressionStatement(value))), 10))
                .forEach(wrap -> {
                    var value = hole(20);
                    var boundary = wrap.apply(value);
                    var call = call(boundary);

                    assertTrue(PartialSyntax.contains(boundary));
                    assertTrue(PartialSyntax.containsAt(call, 20));
                    assertTrue(PartialSyntax.valueCursor(boundary).isEmpty());
                    assertTrue(PartialSyntax.argument(call).isEmpty());
                    assertTrue(value.getSite().getArgumentCall().isEmpty());
                });
    }

    @Test
    public void aNestedIncompleteCallOwnsItsArgumentCursor() {
        var value = hole(20);
        var inner = call(value);
        var outer = call(new IncompleteExpression(inner));

        assertTrue(PartialSyntax.containsAt(outer, 20));
        assertTrue(PartialSyntax.argument(outer).isEmpty());
        assertSame(inner, value.getSite().getArgumentCall().orElseThrow());
        assertTrue(inner.getArgumentCall().isEmpty());
    }

    @Test
    public void multipleValueCursorsInOneArgumentDoNotInventAnArgumentBinding() {
        var left = hole(20);
        var right = hole(30);
        var ambiguous = new RelOpExpression(left, token(Id.ADD), right);
        var call = call(ambiguous);

        assertTrue(PartialSyntax.containsAt(call, 20));
        assertTrue(PartialSyntax.containsAt(call, 30));
        assertTrue(PartialSyntax.valueCursor(ambiguous).isEmpty());
        assertTrue(PartialSyntax.argument(call).isEmpty());
        assertTrue(left.getSite().getArgumentCall().isEmpty());
        assertTrue(right.getSite().getArgumentCall().isEmpty());
    }

    @Test
    public void dimensionsAreNotOrdinaryArgumentSlots() {
        var value = hole(20);
        var creation = new NewExpression(null, token(Id.NEW), null, List.of(), 0, null, 40);
        var dimensions = new IncompleteStatement(creation, token(Id.L_SQUARE),
                List.of(value), List.of(), 40);
        dimensions.introduceParentage();

        assertTrue(PartialSyntax.containsAt(dimensions, 20));
        assertTrue(PartialSyntax.argument(dimensions).isEmpty());
        assertTrue(value.getSite().getArgumentCall().isEmpty());
    }

    @Test
    public void clonedCallsSelectTheirOwnCursorAndKeepTheOriginalBinding() {
        var value = hole(20);
        var original = call(new ParenthesizedExpression(value, 10, 30));
        var clone = (IncompleteStatement) original.clone();
        var selected = PartialSyntax.argument(clone).orElseThrow();

        assertNotSame(value.getSite(), selected.cursor());
        assertSame(clone, selected.cursor().getArgumentCall().orElseThrow());
        assertSame(original, value.getSite().getArgumentCall().orElseThrow());
        assertSame(value.getSite(), PartialSyntax.argument(original).orElseThrow().cursor());
        assertTrue(PartialSyntax.containsAt(clone, 20));
        assertEquals(original.toDumpString(), clone.toDumpString());
    }

    @Test
    public void trialSubstitutionPreservesOriginalSyntaxAndLaterArguments() {
        var value = hole(20);
        var compound = new RelOpExpression(name("left"), token(Id.ADD), value);
        var label = new LabeledExpression(token("second"), new ParenthesizedExpression(compound, 10, 30));
        var call = call(label);
        var selected = PartialSyntax.argument(call).orElseThrow();
        var before = call.toDumpString();
        var replacement = name("proposed");
        var proposed = PartialArgument.proposed(call, selected, replacement);
        var trialLabel = assertInstanceOf(LabeledExpression.class, proposed.get(1));
        var trialGroup = assertInstanceOf(ParenthesizedExpression.class, trialLabel.getUnderlyingExpression());
        var trialValue = assertInstanceOf(RelOpExpression.class, trialGroup.getUnderlyingExpression());

        assertSame(call.getArguments().getFirst(), proposed.getFirst());
        assertSame(call.getArguments().getLast(), proposed.getLast());
        assertNotSame(label, trialLabel);
        assertEquals("second", trialLabel.getName());
        assertSame(call, trialLabel.getParent());
        assertSame(trialLabel, trialGroup.getParent());
        assertSame(trialGroup, trialValue.getParent());
        assertSame(trialValue, replacement.getParent());
        assertFalse(PartialSyntax.contains(trialLabel));
        assertSame(compound, value.getParent());
        assertSame(call, selected.cursor().getArgumentCall().orElseThrow());
        assertEquals(before, call.toDumpString());
    }

    @Test
    public void unboundTrialsKeepTheWrittenLabelAndDoNotReplaceTheSourceSlot() {
        var value = hole(20);
        var label = new LabeledExpression(token("second"), value);
        var call = call(label);
        var selected = PartialSyntax.argument(call).orElseThrow();
        var unbound = PartialArgument.unbound(call, selected);
        var trial = assertInstanceOf(LabeledExpression.class, unbound.get(1));
        var marker = assertInstanceOf(NonBindingExpression.class, trial.getUnderlyingExpression());

        assertEquals(label.getName(), trial.getName());
        assertEquals(label.getStartPosition(), marker.getStartPosition());
        assertEquals(label.getEndPosition(), marker.getEndPosition());
        assertSame(call, trial.getParent());
        assertSame(trial, marker.getParent());
        assertSame(label, call.getArguments().get(1));
        assertSame(label, value.getParent());
        assertSame(call.getArguments().getFirst(), unbound.getFirst());
        assertSame(call.getArguments().getLast(), unbound.getLast());
        assertSame(selected.cursor(), PartialSyntax.argument(call).orElseThrow().cursor());
    }

    private static IncompleteStatement call(Expression argument) {
        var call = new IncompleteStatement(name("work"), token(Id.L_PAREN),
                List.of(name("first"), argument, name("last")),
                List.of(token(Id.COMMA), token(Id.COMMA)), 100);
        call.introduceParentage();
        return call;
    }

    private static IncompleteExpression hole(long cursor) {
        return new IncompleteExpression(new IncompleteStatement(
                new Token(cursor - 2, cursor, Id.IDENTIFIER, "pr"), cursor, Parser.INCOMPLETE_EXPRESSION));
    }

    private static NameExpression name(String name) {
        return new NameExpression(token(name));
    }

    private static Token token(String name) {
        return new Token(0, name.length(), Id.IDENTIFIER, name);
    }

    private static Token token(Id id) {
        return new Token(0, 1, id, null);
    }
}

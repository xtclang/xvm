package org.xvm.compiler;

import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;

import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.partial.IncompleteDeclarationStatement;
import org.xvm.compiler.ast.partial.IncompleteStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A missing name is a syntax slot, never a fabricated declaration or a type-completion query. */
public class ParserDeclarationNameTest {
    @Test
    public void explicitNameSlotsRetainWrittenTypesAndFollowingDeclarations() {
        List.of("String §;", "List<String> § = [];", "ecstasy.text.StringBuffer §;",
                "void run(String §) {}", "void run(Int count, List<String> § = []) {}",
                "construct(String §) {}", "class Item(String §) {}")
                .forEach(header -> {
                    String marked = "module Names { " + header + " Int later = 1; }";
                    var parsed = parse(marked);
                    var slot = slots(parsed.tree()).findFirst().orElseThrow();
                    assertEquals(header.contains("List") ? "List" : header.contains("StringBuffer")
                            ? "StringBuffer" : "String", slot.getDeclarationNameType().orElseThrow().getValueText());
                    assertTrue(parsed.errors().hasError(Parser.INCOMPLETE_EXPRESSION));
                    assertEquals(1, parsed.errors().getSeriousErrorCount(), header);
                    assertTrue(parsed.tree().toDumpString().contains("later"));
                    assertTrue(slot.getMemberName().isEmpty());
                    assertTrue(slot.getReceiver().isEmpty());
                    assertFalse(slot.isTypeCompletion());
                    assertFalse(slot.isNameCompletion());
                    assertEquals(parsed.cursor(), slot.getEndPosition());
                    assertEquals(marked.replace("§", ""), parsed.source().toRawString());

                    var copy = (StatementBlock) parsed.tree().clone();
                    var copiedSlot = slots(copy).findFirst().orElseThrow();
                    assertNotSame(slot, copiedSlot);
                    assertNotSame(slot.getTarget(), copiedSlot.getTarget());
                    assertSame(copiedSlot, copiedSlot.getTarget().getParent());
                    assertEquals(slot.getDeclarationNameType(), copiedSlot.getDeclarationNameType());
                    assertFalse(copiedSlot.isTypeCompletion());
                    assertTrue(copiedSlot.getEndPosition() <= copiedSlot.getParent().getEndPosition());

                    var ordinary = new ErrorList();
                    var tree = new Parser(new Source(marked.replace("§", "")), ordinary).parseSource();
                    assertTrue(ordinary.hasSeriousErrors());
                    assertEquals(0, slots(tree).count());
                    assertFalse(ordinary.hasError(Parser.INCOMPLETE_EXPRESSION));
                });
    }

    @Test
    public void eofSlotIncludesTrailingWhitespaceWithoutAcquiringANameOrEmission() {
        List.of("module Names { String §", "module Names { void run(String §",
                "module Names { class Item(String §").forEach(marked -> {
            var parsed = parse(marked);
            var slot = slots(parsed.tree()).findFirst().orElseThrow();
            var copy = (StatementBlock) parsed.tree().clone();
            var copiedSlot = slots(copy).findFirst().orElseThrow();
            assertEquals(parsed.cursor(), slot.getEndPosition());
            assertTrue(copiedSlot.getEndPosition() <= copiedSlot.getParent().getEndPosition());
            assertTrue(nodes(copy).filter(IncompleteDeclarationStatement.class::isInstance)
                    .map(IncompleteDeclarationStatement.class::cast)
                    .allMatch(declaration -> declaration.getKind() != IncompleteDeclarationStatement.Kind.PROPERTY
                            || declaration.getNameToken().isEmpty()));
            assertThrows(IllegalStateException.class, () -> copiedSlot.getParent().generateCode(null, new ErrorList()));
        });
    }

    @Test
    public void nonNameContextsDoNotAcquireSlots() {
        List.of("String§;", "String §already;", "List<§> value;", "List<String §;",
                "void run(String §already) {}", "void run() { String §; }",
                "void run(String value) { value §; }", "void run() { call(String §); }",
                "String text = \"String §\";", "String /* § */ value;")
                .forEach(header -> assertEquals(0, slots(parse("module Names { " + header + " }").tree()).count(), header));
    }

    @Test
    public void slotsRespectErrorBudgetsCancellationAndSpeculation() {
        var source = new Source("module Names { String ; }");
        "module Names { String ".chars().forEach(_ -> source.next());
        long cursor = source.getPosition();
        source.reset();
        assertThrows(CompilerException.class, () -> Parser.forPartialAnalysis(source.clone(), cursor,
                new ErrorList(ErrorList.FIRST_ERROR)).parseSource());
        assertThrows(CompilerException.class, () -> Parser.forPartialAnalysis(source.clone(), cursor,
                ErrorListener.cancellable(new ErrorList(), () -> true)).parseSource());
        var errors = new ErrorList();
        var parser = Parser.forPartialAnalysis(source, cursor, errors);
        assertThrows(CompilerException.class, () -> {
            try (var ignored = parser.attempt()) {
                parser.parseTypeCompositionStatement();
            }
        });
        assertFalse(errors.hasSeriousErrors());
        assertEquals(1, slots(parser.parseSource()).count());
    }

    private static Parsed parse(String marked) {
        var source = new Source(marked.replace("§", ""));
        marked.substring(0, marked.indexOf('§')).chars().forEach(_ -> source.next());
        long cursor = source.getPosition();
        source.reset();
        var errors = new ErrorList();
        return new Parsed(Parser.forPartialAnalysis(source, cursor, errors).parseSource(), source, cursor, errors);
    }

    private static Stream<AstNode> nodes(AstNode node) {
        return Stream.concat(Stream.of(node), StreamSupport.stream(node.children().spliterator(), false)
                .flatMap(ParserDeclarationNameTest::nodes));
    }

    private static Stream<IncompleteStatement> slots(AstNode root) {
        return nodes(root).filter(IncompleteStatement.class::isInstance).map(IncompleteStatement.class::cast)
                .filter(site -> site.getDeclarationNameType().isPresent());
    }

    private record Parsed(StatementBlock tree, Source source, long cursor, ErrorList errors) {}
}

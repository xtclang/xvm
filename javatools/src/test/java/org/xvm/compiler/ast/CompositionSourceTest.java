package org.xvm.compiler.ast;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Source lookup also applies to parsed trees that failed before child adoption. */
class CompositionSourceTest {
    @Test
    void defaultCompositionHasNoSourceUntilAdopted() {
        var source = new Source("module Test { enum Kind default(Plain) {Plain, Fancy} }");
        var errors = new ErrorList();
        var tree = new Parser(source, errors).parseSource();
        assertFalse(errors.hasSeriousErrors());
        var defaults = descendants(tree).filter(CompositionNode.Default.class::isInstance).toList();
        assertFalse(defaults.isEmpty());
        defaults.forEach(node -> assertNull(node.getSource()));

        tree.introduceParentage();
        defaults.forEach(node -> assertSame(source, node.getSource()));
    }

    private static Stream<AstNode> descendants(AstNode node) {
        return Stream.concat(Stream.of(node), StreamSupport.stream(node.childNodes().spliterator(), false)
                .flatMap(CompositionSourceTest::descendants));
    }
}

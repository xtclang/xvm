package org.xvm.compiler.ast;


import java.lang.reflect.Modifier;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the sealed parser AST tree (sealed-hierarchy audit, wave E). The tree has exactly one
 * documented hatch: {@code MethodDeclarationStatement} is {@code non-sealed} because
 * {@code EvalCompiler.EvalStatement} extends it from outside the package, which the unnamed
 * module's same-package rule cannot permit. Everything else is sealed or final, so a new node
 * kind must be declared into the audited tree, and every pattern switch over the sealed
 * portions is compile-checked exhaustive.
 */
public class SealedParserAstTest {
    @Test
    public void parserAstTreeIsSealedWithOneDocumentedHatch() {
        assertTrue(AstNode.class.isSealed(), "AstNode must be sealed");

        Set<Class<?>> seen = new HashSet<>();
        var frontier = new ArrayDeque<Class<?>>();
        frontier.add(AstNode.class);
        int cHatches = 0;
        while (!frontier.isEmpty()) {
            var clz = frontier.poll();
            if (!seen.add(clz)) {
                continue;
            }
            if (clz == MethodDeclarationStatement.class) {
                // the one documented non-sealed hatch (EvalCompiler.EvalStatement)
                assertTrue(!clz.isSealed() && !Modifier.isFinal(clz.getModifiers()),
                        "MethodDeclarationStatement is the documented non-sealed hatch");
                ++cHatches;
                continue;
            }
            assertTrue(clz.isSealed() || Modifier.isFinal(clz.getModifiers()),
                    clz.getName() + " must be sealed or final; an open node class reopens"
                            + " the parser AST");
            if (clz.isSealed()) {
                for (var permitted : clz.getPermittedSubclasses()) {
                    frontier.add(permitted);
                }
            }
        }

        assertEquals(1, cHatches);
        // the root plus its permitted closure (EvalStatement is outside the sealed walk,
        // reachable only through the documented hatch)
        assertEquals(97, seen.size(),
                "the parser AST tree drifted; update the sealed-hierarchy audit wave E");
    }
}

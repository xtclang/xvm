package org.xvm.compiler.ast;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.partial.IncompleteDeclarationStatement;
import org.xvm.compiler.ast.partial.IncompleteExpression;
import org.xvm.compiler.ast.partial.IncompleteLocalDeclaration;
import org.xvm.compiler.ast.partial.IncompleteStatement;
import org.xvm.compiler.ast.partial.IncompleteTypeCompositionStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tree copying preserves syntax ownership through both new and compatibility entry points. */
class AstTreeCopyTest {
    @Test
    void recoveryCopiesPreserveMetadataAndOwnChildrenInsideOrdinaryTrees() {
        for (var original : recoveryNodes()) {
            var statement = original instanceof Statement node ? node
                    : new ExpressionStatement((Expression) original);
            var parent = new StatementBlock(List.of(statement));
            parent.introduceParentage();
            original.setStage(Stage.Registered);

            for (var copy : List.of(original.copyTree(), original.clone())) {
                assertTreeCopy(original, copy, original.getParent());
                assertEquals(original.toDumpString(), copy.toDumpString());
            }
            // An ordinary node must dispatch to constructor copies for its recovery descendants.
            for (var copy : List.of(parent.copyTree(), parent.clone())) {
                assertTreeCopy(parent, copy, null);
            }
        }
    }

    @Test
    void callCopiesRetainCursorTokensAndKeepArgumentEditsIndependent() {
        var original = IncompleteStatement.forArgumentPrefix(name("call"), token(Id.L_PAREN),
                List.of(name("argument")), List.of(token(Id.COMMA)), 20, token("label"), token("prefix"));
        ((AstNode) original).introduceParentage();
        for (var node : List.of(original.copyTree(), original.clone())) {
            var copy = (IncompleteStatement) node;
            assertSame(original.getOperator(), copy.getOperator());
            assertEquals(original.getSeparators(), copy.getSeparators());
            assertEquals(original.getPendingArgumentName(), copy.getPendingArgumentName());
            assertEquals(original.getArgumentPrefix(), copy.getArgumentPrefix());
            var replacement = name("replacement");
            copy.replaceChild(copy.getArguments().getFirst(), replacement);
            assertSame(copy, replacement.getParent());
            assertSame(replacement, copy.getArguments().getFirst());
            assertEquals("argument", original.getArguments().getFirst().toString());
        }
    }

    @Test
    void copiedRecoveryHeadersKeepTheirChildListsImmutable() {
        for (var original : recoveryNodes()) {
            if (original instanceof IncompleteDeclarationStatement
                    || original instanceof IncompleteTypeCompositionStatement) {
                for (var copy : List.of(original.copyTree(), original.clone())) {
                    for (String field : List.of("cursors", "formals")) {
                        var children = (List<?>) copy.getDumpChildren().get(field);
                        assertThrows(UnsupportedOperationException.class, children::clear);
                    }
                }
            }
        }
    }

    private static List<AstNode> recoveryNodes() {
        return List.of(site(), new IncompleteExpression(site()),
                new IncompleteLocalDeclaration(site(), name("initializer"), 30),
                new IncompleteDeclarationStatement(IncompleteDeclarationStatement.Kind.METHOD,
                        token("method"), 0, 30, List.of(site()), List.of(parameter("arg"))),
                new IncompleteTypeCompositionStatement(new Source("module Copy {}"), token(Id.MODULE),
                        token("Copy"), null, new StatementBlock(List.of(site())), 0, 30,
                        List.of(site()), List.of(parameter("typeArg"))));
    }

    private static void assertTreeCopy(AstNode original, AstNode copy, AstNode parent) {
        assertNotSame(original, copy);
        assertEquals(original.getClass(), copy.getClass());
        assertEquals(original.getStartPosition(), copy.getStartPosition());
        assertEquals(original.getEndPosition(), copy.getEndPosition());
        assertEquals(original.getStage(), copy.getStage());
        assertSame(parent, copy.getParent());
        var originalChildren = childrenWithoutAdoption(original);
        var copiedChildren = childrenWithoutAdoption(copy);
        assertEquals(originalChildren.size(), copiedChildren.size());
        for (int index = 0; index < originalChildren.size(); ++index) {
            assertSame(original, originalChildren.get(index).getParent());
            assertTreeCopy(originalChildren.get(index), copiedChildren.get(index), copy);
        }
    }

    private static List<AstNode> childrenWithoutAdoption(AstNode node) {
        // The normal child iterator adopts nodes; using it would hide broken copy parentage.
        return node.getDumpChildren().values().stream().flatMap(value -> switch (value) {
            case AstNode child -> Stream.of(child);
            case List<?> children -> children.stream().map(AstNode.class::cast);
            case null, default -> Stream.empty();
        }).toList();
    }

    private static IncompleteStatement site() {
        return new IncompleteStatement(token("prefix"), 20, Parser.INCOMPLETE_EXPRESSION);
    }

    private static Parameter parameter(String name) {
        var type = new NamedTypeExpression(null, List.of(token("String")), null, null, null, 6);
        return new Parameter(type, token(name));
    }

    private static NameExpression name(String name) {
        return new NameExpression(token(name));
    }

    private static Token token(String name) {
        return new Token(0, name.length(), Id.IDENTIFIER, name);
    }

    private static Token token(Id id) {
        return new Token(0, 1, id);
    }
}

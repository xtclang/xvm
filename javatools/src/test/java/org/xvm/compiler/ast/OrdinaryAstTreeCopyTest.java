package org.xvm.compiler.ast;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Exercises tree-copy ownership before any incomplete-source node types are introduced. */
class OrdinaryAstTreeCopyTest {
    @Test
    void typedAndLegacyCopiesKeepMetadataAndOwnTheirChildren() {
        var name = new NameExpression(new Token(0, 5, Id.IDENTIFIER, "value"));
        var statement = new ExpressionStatement(name);
        var original = new StatementBlock(List.of(statement));
        original.introduceParentage();
        original.setStage(Stage.Registered);

        for (StatementBlock copy : List.of(original.copyTree(), (StatementBlock) original.clone())) {
            assertNotSame(original, copy);
            assertEquals(original.getStage(), copy.getStage());
            assertEquals(original.getStartPosition(), copy.getStartPosition());
            assertEquals(original.getEndPosition(), copy.getEndPosition());
            var copiedStatement = assertInstanceOf(ExpressionStatement.class, copy.getStatements().getFirst());
            assertNotSame(statement, copiedStatement);
            assertSame(copy, copiedStatement.getParent());
            assertSame(original, statement.getParent());
            var copiedName = copiedStatement.getExpression();
            assertNotSame(name, copiedName);
            assertSame(copiedStatement, copiedName.getParent());
            assertSame(statement, name.getParent());
            assertEquals(name.toString(), copiedName.toString());
        }
    }
}

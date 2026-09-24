package org.xvm.compiler.ast;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;

import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Callback context and listener state must not survive a failed validation. */
class ValidationScopeTest {
    @Test
    void loopScopesReleaseTheirListenersAfterExceptionalExit() throws Exception {
        var condition = new AssignmentStatement(null, token(Id.COLON), null);
        var statements = List.of(
                new ForStatement(token(Id.FOR), List.of(), List.of(), List.of(), block()),
                new ForEachStatement(token(Id.FOR), condition, block()),
                new WhileStatement(token(Id.WHILE), List.of(), block()));
        for (var statement : statements) {
            var field = scopeField(statement, "m_labelVars");
            var failure = new IllegalStateException("nested validation failed");
            var context = new Context(null, false) {
                @Override
                public Context enter() { return this; }
                @Override
                public Context enterIf() { return fail(); }
                @Override
                public Context enterLoop() { return fail(); }

                private Context fail() {
                    assertNotNull(readScope(field, statement), "failure must occur with an active scope");
                    throw failure;
                }
            };
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> statement.validate(context, new ErrorList())));
            assertNull(readScope(field, statement), statement.getClass().getSimpleName());
            assertThrows(IllegalStateException.class, statement::ensureValidationContext);
        }
    }

    @Test
    void finallyScopeAndStatementContextAreRestoredAfterFailure() throws Exception {
        var failure = new IllegalStateException("finally failed");
        var body = new StatementBlock(List.of()) {
            @Override
            protected Statement validateImpl(Context ctx, ErrorListener errs) { return this; }
        };
        var catchall = new StatementBlock(List.of()) {
            @Override
            protected Statement validateImpl(Context ctx, ErrorListener errs) {
                var owner = (TryStatement) getParent();
                assertNotNull(readScope(scopeField(owner, "m_validatingFinally"), owner));
                throw failure;
            }
        };
        var statement = new TryStatement(token(Id.TRY), null, body, null, catchall);
        statement.adopt(body);
        statement.adopt(catchall);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> statement.validate(new Context(null, false), new ErrorList())));
        assertNull(readScope(scopeField(statement, "m_validatingFinally"), statement));
        assertThrows(IllegalStateException.class, statement::ensureValidationContext);
        assertThrows(IllegalStateException.class, catchall::ensureValidationContext);
    }

    @Test
    void infiniteForEarlyReturnReleasesValidationState() {
        var statement = new ForStatement(token(Id.FOR), List.of(), List.of(), List.of(), block());
        var source = new StatementBlock(List.of(statement), new Source("for (;;) {}"), 0, 1);
        source.adopt(statement);
        var errors = new ErrorList();
        assertNull(statement.validate(new Context(null, false), errors));
        assertTrue(errors.hasSeriousErrors(), "the empty infinite loop is rejected");
        assertNull(readScope(scopeField(statement, "m_labelVars"), statement));
        assertThrows(IllegalStateException.class, statement::ensureValidationContext);
    }

    @Test
    void validationScopeCannotContainMissingContextOrListener() {
        assertThrows(NullPointerException.class, () -> new ValidationScope(null, new ErrorList()));
        assertThrows(NullPointerException.class, () -> new ValidationScope(new Context(null, false), null));
    }

    private static StatementBlock block() { return new StatementBlock(List.of()); }
    private static Token token(Id id) { return new Token(0, 1, id); }

    // Inspect ownership without adding public diagnostic-state accessors to AST nodes.
    private static Field scopeField(Statement statement, String name) {
        try {
            var field = statement.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static ValidationScope readScope(Field field, Statement statement) {
        try {
            return (ValidationScope) field.get(statement);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}

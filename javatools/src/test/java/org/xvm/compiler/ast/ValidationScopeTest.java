package org.xvm.compiler.ast;

import java.util.List;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Callback context and listener state must not survive a failed validation. */
class ValidationScopeTest {
    @ParameterizedTest
    @MethodSource("loopStatements")
    void loopCallbacksReturnToTheOuterContextAndListenerAfterNestedFailure(Statement statement) {
        var pool = new FileStructure("Test").getConstantPool();
        var label = new LabeledStatement(new Token(0, 1, Id.IDENTIFIER, "loop"), statement) {
            @Override
            protected ConstantPool pool() { return pool; }
        };
        label.adopt(statement);
        var outerErrors = new ErrorList();
        var innerErrors = new ErrorList();
        var innerFailure = new IllegalStateException("nested validation failed");
        var outerFailure = new IllegalStateException("outer validation failed");
        var innerContext = new LoopContext() {
            @Override
            void validateBody() {
                assertSame(this, statement.ensureValidationContext());
                throw innerFailure;
            }
        };
        var outerContext = new LoopContext() {
            @Override
            void validateBody() {
                assertSame(this, statement.ensureValidationContext());
                assertSame(innerFailure, assertThrows(IllegalStateException.class,
                        () -> statement.validate(innerContext, innerErrors)));
                assertSame(this, statement.ensureValidationContext());

                // Lazy label creation must register and report against this outer validation,
                // not the failed nested call. Neither callback state nor its field names are
                // inspected: the context records the registration and the listener the report.
                assertNotNull(label.getLabelVar(this, "count"));
                assertEquals(1, registrations);
                assertEquals(0, innerContext.registrations);
                assertEquals(1, outerErrors.getErrors().size());
                assertTrue(innerErrors.getErrors().isEmpty());
                throw outerFailure;
            }
        };
        assertSame(outerFailure, assertThrows(IllegalStateException.class,
                () -> statement.validate(outerContext, outerErrors)));
        assertThrows(IllegalStateException.class, statement::ensureValidationContext);
    }

    @Test
    void finallyScopeAndStatementContextAreRestoredAfterFailure() {
        var failure = new IllegalStateException("finally failed");
        var body = new StatementBlock(List.of()) {
            @Override
            protected Statement validateImpl(Context ctx, ErrorListener errs) { return this; }
        };
        var catchall = new StatementBlock(List.of()) {
            @Override
            protected Statement validateImpl(Context ctx, ErrorListener errs) {
                var owner = (TryStatement) getParent();
                assertTrue(owner.hasLabelVar("exception"));
                assertSame(ctx, ensureValidationContext());
                throw failure;
            }
        };
        var statement = new TryStatement(token(Id.TRY), null, body, null, catchall);
        statement.adopt(body);
        statement.adopt(catchall);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> statement.validate(new Context(null, false), new ErrorList())));
        assertFalse(statement.hasLabelVar("exception"));
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
        assertThrows(IllegalStateException.class, statement::ensureValidationContext);
    }

    @Test
    void validationScopeCannotContainMissingContextOrListener() {
        assertThrows(NullPointerException.class, () -> new ValidationScope(null, new ErrorList()));
        assertThrows(NullPointerException.class, () -> new ValidationScope(new Context(null, false), null));
    }

    private static Stream<Statement> loopStatements() {
        var condition = new AssignmentStatement(null, token(Id.COLON), null);
        return Stream.of(
                new ForStatement(token(Id.FOR), List.of(), List.of(), List.of(), block()),
                new ForEachStatement(token(Id.FOR), condition, block()),
                new WhileStatement(token(Id.WHILE), List.of(), block()));
    }

    private static StatementBlock block() { return new StatementBlock(List.of()); }
    private static Token token(Id id) { return new Token(0, 1, id); }

    /** Inject a failure at a loop callback, and observe lazy-variable registration. */
    private abstract static class LoopContext extends Context {
        LoopContext() { super(null, false); }

        @Override
        public Context enter() { return this; }
        @Override
        public Context enterIf() { validateBody(); return this; }
        @Override
        public Context enterLoop() { validateBody(); return this; }
        @Override
        public Register createRegister(TypeConstant type, String name) {
            return new Register(type, name, 0);
        }
        @Override
        public void registerVar(Token name, Register register, ErrorListener errs) {
            registrations++;
            errs.error("TEST", ErrorListener.NOWHERE);
            errs.merge();
        }

        abstract void validateBody();

        int registrations;
    }
}

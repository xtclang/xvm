package org.xvm.compiler.ast;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards compiler-wide counters that are shared when multiple compilations run in one JVM.
 */
public class CompilerCounterAtomicTest {
    @Test
    public void staticCompilerCountersAreAtomic()
            throws Exception {
        assertAtomicCounter(ConditionalStatement.class, "LABEL_COUNTER");
        assertAtomicCounter(MethodDeclarationStatement.class, "COUNTER");
        assertAtomicCounter(ElseExpression.class, "COUNTER");
        assertAtomicCounter(ElvisExpression.class, "COUNTER");
    }

    @Test
    public void conditionalStatementLabelIdsRemainSequentialAndStable()
            throws Exception {
        withCounter(ConditionalStatement.class, "LABEL_COUNTER", 100, () -> {
            TestConditionalStatement stmt1 = new TestConditionalStatement();
            TestConditionalStatement stmt2 = new TestConditionalStatement();

            assertEquals(101, stmt1.labelId());
            assertEquals(102, stmt2.labelId());
            assertEquals(101, stmt1.labelId());
            assertEquals(102, getAtomicCounter(ConditionalStatement.class, "LABEL_COUNTER").get());
        });
    }

    @Test
    public void conditionalStatementLabelIdsAreUniqueUnderParallelAllocation()
            throws Exception {
        withCounter(ConditionalStatement.class, "LABEL_COUNTER", 0, () -> {
            int             cThreads  = 8;
            int             cPerThread = 250;
            Set<Integer>    labels     = ConcurrentHashMap.newKeySet();
            CountDownLatch  start      = new CountDownLatch(1);
            ExecutorService executor   = Executors.newFixedThreadPool(cThreads);
            List<Future<?>> futures    = new ArrayList<>();

            try {
                for (int i = 0; i < cThreads; ++i) {
                    futures.add(executor.submit(() -> {
                        start.await();
                        for (int j = 0; j < cPerThread; ++j) {
                            labels.add(new TestConditionalStatement().labelId());
                        }
                        return null;
                    }));
                }

                start.countDown();
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                executor.shutdownNow();
            }

            assertEquals(cThreads * cPerThread, labels.size());
            assertEquals(cThreads * cPerThread,
                    getAtomicCounter(ConditionalStatement.class, "LABEL_COUNTER").get());
        });
    }

    @Test
    public void methodDeclarationCodeContainerCountersRemainSequential()
            throws Exception {
        withCounter(MethodDeclarationStatement.class, "COUNTER", 200, () -> {
            TestMethodDeclarationStatement stmt1 = new TestMethodDeclarationStatement("method1");
            TestMethodDeclarationStatement stmt2 = new TestMethodDeclarationStatement("method2");

            assertEquals(200, stmt1.codeContainerCounter());
            assertEquals(201, stmt2.codeContainerCounter());
            assertEquals(202, stmt1.codeContainerCounter());
            assertEquals(203, getAtomicCounter(MethodDeclarationStatement.class, "COUNTER").get());
        });
    }

    @Test
    public void elseExpressionLabelsRemainSequentialAndStable()
            throws Exception {
        withCounter(ElseExpression.class, "COUNTER", 300, () -> {
            TestElseExpression expr1 = new TestElseExpression();
            TestElseExpression expr2 = new TestElseExpression();

            Label label1 = expr1.shortCircuitLabelForLeft();
            Label label2 = expr2.shortCircuitLabelForLeft();

            assertEquals("else_:_301", label1.getName());
            assertEquals("else_:_302", label2.getName());
            assertSame(label1, expr1.shortCircuitLabelForLeft());
            assertEquals(302, getAtomicCounter(ElseExpression.class, "COUNTER").get());
        });
    }

    @Test
    public void elvisExpressionEndLabelsRemainSequentialAndStable()
            throws Exception {
        withCounter(ElvisExpression.class, "COUNTER", 400, () -> {
            TestElvisExpression expr1 = new TestElvisExpression();
            TestElvisExpression expr2 = new TestElvisExpression();

            Label label1 = expr1.endLabel();
            Label label2 = expr2.endLabel();

            assertEquals("end_?:_401", label1.getName());
            assertEquals("end_?:_402", label2.getName());
            assertSame(label1, expr1.endLabel());
            assertEquals(402, getAtomicCounter(ElvisExpression.class, "COUNTER").get());
        });
    }

    private static void assertAtomicCounter(Class<?> clz, String sField)
            throws NoSuchFieldException {
        Field field = clz.getDeclaredField(sField);
        int   mods  = field.getModifiers();

        assertTrue(Modifier.isPrivate(mods));
        assertTrue(Modifier.isStatic(mods));
        assertTrue(Modifier.isFinal(mods));
        assertEquals(AtomicInteger.class, field.getType());
    }

    private static AtomicInteger getAtomicCounter(Class<?> clz, String sField)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = clz.getDeclaredField(sField);
        field.setAccessible(true);
        return (AtomicInteger) field.get(null);
    }

    private static void withCounter(Class<?> clz, String sField, int nStart, CheckedRunnable test)
            throws Exception {
        AtomicInteger counter = getAtomicCounter(clz, sField);
        int           nSaved  = counter.get();

        counter.set(nStart);
        try {
            test.run();
        } finally {
            counter.set(nSaved);
        }
    }

    private static Token token(Id id) {
        return token(id, null);
    }

    private static Token token(Id id, String sValue) {
        return new Token(0, 0, id, sValue);
    }

    private interface CheckedRunnable {
        void run()
                throws Exception;
    }

    private static class TestConditionalStatement
            extends ConditionalStatement {
        TestConditionalStatement() {
            super(token(Id.IF), List.of());
        }

        int labelId() {
            return getLabelId();
        }

        @Override
        public long getEndPosition() {
            return 0;
        }

        @Override
        protected Statement validateImpl(Context ctx, ErrorListener errs) {
            return this;
        }

        @Override
        protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs) {
            return true;
        }

        @Override
        public String toString() {
            return "test conditional";
        }
    }

    private static class TestMethodDeclarationStatement
            extends MethodDeclarationStatement {
        TestMethodDeclarationStatement(String sName) {
            super(0, 0, null, null, null, null, null, List.of(),
                    token(Id.IDENTIFIER, sName), null, List.of(), null, null, null, null);
        }

        int codeContainerCounter() {
            return getCodeContainerCounter();
        }
    }

    private static class TestElseExpression
            extends ElseExpression {
        TestElseExpression() {
            this(new TestExpression(), new TestExpression());
        }

        TestElseExpression(Expression expr1, Expression expr2) {
            super(expr1, token(Id.COLON), expr2);
            adopt(expr1);
            adopt(expr2);
        }

        Label shortCircuitLabelForLeft() {
            return ensureShortCircuitLabel(expr1, new TestContext());
        }
    }

    private static class TestElvisExpression
            extends ElvisExpression {
        TestElvisExpression() {
            super(new TestExpression(), token(Id.COND_ELSE), new TestExpression());
        }

        Label endLabel() {
            return getEndLabel();
        }
    }

    private static class TestExpression
            extends Expression {
        @Override
        public long getStartPosition() {
            return 0;
        }

        @Override
        public long getEndPosition() {
            return 0;
        }

        @Override
        public String toString() {
            return "expr";
        }
    }

    private static class TestContext
            extends Context {
        TestContext() {
            super(null, false);
        }

        @Override
        protected Map<String, Argument> mergeNarrowedElseTypes(Map<String, Argument> map) {
            return map;
        }
    }
}

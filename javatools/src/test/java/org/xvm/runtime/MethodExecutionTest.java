package org.xvm.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.Arrays;
import java.util.List;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.xvm.asm.Constants;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Nop;
import org.xvm.asm.op.Return_0;
import org.xvm.asm.op.Return_1;
import org.xvm.asm.op.Var;

import org.xvm.runtime.ServiceContext.CallLaterRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodExecutionTest {
    private final Runtime runtime = new Runtime();

    @AfterEach
    void shutdown() {
        runtime.shutdownXVM();
    }

    @Test
    void servicesKeepIndependentCompletionAndMetadataClearsRetainIt() {
        var fixture = new Fixture();
        var container = new TestContainer(runtime, fixture.file);
        var first = caller(container);
        var second = caller(container);
        var state = first.f_context.getMethodExecution(fixture.method);
        var other = second.f_context.getMethodExecution(fixture.method);
        assertNotSame(state, other);
        var next = first.createFrame1(fixture.method, null, new ObjectHandle[0], Op.A_IGNORE);
        assertEquals(Op.R_CALL, first.ensureInitialized(next));
        assertTrue(state.isInitialized());
        assertFalse(other.isInitialized());
        container.getTypeContext().clearMetadata();
        assertSame(state, first.f_context.getMethodExecution(fixture.method));
        assertTrue(state.isInitialized());
    }

    @Test
    void initializationInOneContainerDoesNotSkipAnotherContainersSingletons() {
        var fixture = new Fixture();
        var first = new TestContainer(runtime, fixture.file);
        var second = new TestContainer(runtime, fixture.file);
        for (var container : List.of(first, second)) {
            var caller = caller(container);
            var next = caller.createFrame1(fixture.method, null, new ObjectHandle[0], Op.A_IGNORE);
            assertEquals(Op.R_CALL, caller.ensureInitialized(next));
            assertEquals(1, container.main.requests);
            assertSame(container.main.value, container.ensureSingletonState(fixture.singleton).getHandle());
        }
    }

    @Test
    void failedInitializationLeavesTheExecutionRetryable() {
        var fixture = new Fixture();
        var container = new TestContainer(runtime, fixture.file);
        var caller = caller(container);
        var state = caller.f_context.getMethodExecution(fixture.method);
        container.main.failNext = true;
        var next = caller.createFrame1(fixture.method, null, new ObjectHandle[0], Op.A_IGNORE);
        assertEquals(Op.R_EXCEPTION, caller.ensureInitialized(next));
        assertFalse(state.isInitialized());
        assertEquals(Op.R_CALL, caller.ensureInitialized(next));
        assertTrue(state.isInitialized());
        assertEquals(2, container.main.requests);
    }

    @Test
    void decodedOpsLayoutAndConstantsStayOutsideTheColdDefinition() throws Exception {
        var fixture = new Fixture();
        var first = caller(new TestContainer(runtime, fixture.file));
        var second = caller(new TestContainer(runtime, fixture.file));
        var before = definitionState(fixture.method);
        var constants = fixture.file.getConstantPool().getConstants();
        var positions = Arrays.stream(constants).map(c -> c.getPosition()).toList();
        assertNull(before);
        var left = first.createFrame1(fixture.method, null, Utils.OBJECTS_NONE, Op.A_IGNORE);
        var right = second.createFrame1(fixture.method, null,
                new ObjectHandle[second.getMaxVars(fixture.method)], Op.A_IGNORE);
        assertSame(left.f_function, right.f_function);
        assertNotSame(left.f_aOp, right.f_aOp);
        for (int index = 0; index < left.f_aOp.length; index++) {
            assertNotSame(left.f_aOp[index], right.f_aOp[index]);
        }
        assertEquals(1, left.f_ahVar.length);
        assertEquals(2, left.f_anNextVar.length);
        assertEquals(7, left.calculateLineNumber(0));
        assertArrayEquals(fixture.method.getLocalConstants(), left.localConstants());
        assertNotSame(left.localConstants(), right.localConstants());
        var original = right.f_aOp[0];
        left.f_aOp[0] = new Nop(100);
        assertSame(original, right.f_aOp[0]);
        assertEquals(7, left.calculateLineNumber(0));
        assertFalse(fixture.method.isNoOp());
        assertFalse(fixture.method.usesSuper());
        var usesSuper = MethodStructure.class.getDeclaredField("m_FUsesSuper");
        usesSuper.setAccessible(true);
        assertNull(usesSuper.get(fixture.method));
        assertEquals(before, definitionState(fixture.method));
        assertArrayEquals(constants, fixture.file.getConstantPool().getConstants());
        assertEquals(positions, Arrays.stream(constants).map(c -> c.getPosition()).toList());
    }

    @Test
    void debuggerResetInstrumentationIsConfinedToItsService() {
        var fixture = new Fixture(false);
        var container = new TestContainer(runtime, fixture.file);
        var first = caller(container);
        var second = caller(container);
        var left = first.createFrame1(fixture.method, null, new ObjectHandle[1], Op.A_IGNORE);
        var right = second.createFrame1(fixture.method, null, new ObjectHandle[1], Op.A_IGNORE);
        var originalLeft = left.f_aOp[0];
        var originalRight = right.f_aOp[0];
        first.f_context.insertBreakPointOp(left, 0);
        assertNotSame(originalLeft, left.f_aOp[0]);
        assertSame(originalRight, right.f_aOp[0]);
        assertThrows(IllegalArgumentException.class, () -> first.f_context.insertBreakPointOp(right, 0));
        assertEquals(7, left.calculateLineNumber(0));
        assertEquals(0, left.f_aOp[0].process(left, 0));
        assertSame(originalLeft, left.f_aOp[0]);
        assertSame(originalRight, right.f_aOp[0]);
    }

    @Test
    @Timeout(10)
    void concurrentFirstDecodingPublishesOneCompleteBody() throws Exception {
        var fixture = new Fixture();
        var context = caller(new TestContainer(runtime, fixture.file)).f_context;
        var start = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = threads.submit(() -> {
                start.await();
                return context.getMethodExecution(fixture.method).getOps();
            });
            var second = threads.submit(() -> {
                start.await();
                return context.getMethodExecution(fixture.method).getOps();
            });
            start.countDown();
            assertSame(first.get(), second.get());
            assertEquals(1, context.getMethodExecution(fixture.method).getMaxVars());
            assertEquals(2, context.getMethodExecution(fixture.method).getMaxScopes());
        }
    }

    @Test
    void incompleteCodeIsNotPublishedAndCanRetryAfterPreparation() {
        var file = new FileStructure("Preparation");
        var method = file.getModule().createMethod(true, Access.PUBLIC, null, Parameter.NO_PARAMS,
                "run", Parameter.NO_PARAMS, true, false);
        method.createCode().add(new Return_0());
        var execution = new MethodExecution(method);
        assertThrows(IllegalStateException.class, execution::getOps);
        method.forceAssembly(file.getConstantPool());
        file.ensureReadOnly();
        var ops = execution.getOps();
        assertSame(ops, execution.getOps());
        assertEquals(1, ops.length);
        assertEquals(0, execution.getMaxVars());
        assertEquals(1, execution.getMaxScopes());
    }

    @Test
    void nativeLayoutContainsParametersWithoutAnInterpretedBody() {
        var file = new FileStructure("NativeLayout");
        var pool = file.getConstantPool();
        var type = file.getModuleId().getType();
        var method = file.getModule().createMethod(true, Access.PUBLIC, null, Parameter.NO_PARAMS,
                "run", new Parameter[] {
                        new Parameter(pool, type, "first", null, false, 0, false),
                        new Parameter(pool, type, "second", null, false, 1, false)}, true, false);
        method.markNative();
        file.ensureReadOnly();
        var caller = caller(new TestContainer(runtime, file));
        assertEquals(2, caller.getMaxVars(method));
        assertEquals(1, caller.f_context.getMethodExecution(method).getMaxScopes());
        assertThrows(IllegalStateException.class,
                () -> caller.createFrame1(method, null, Utils.OBJECTS_NONE, Op.A_IGNORE));
    }

    private static Object definitionState(MethodStructure method) throws Exception {
        var code = MethodStructure.class.getDeclaredField("m_code");
        code.setAccessible(true);
        for (var field : List.of("m_cVars", "m_cScopes", "m_fInitialized")) {
            assertThrows(NoSuchFieldException.class, () -> MethodStructure.class.getDeclaredField(field));
        }
        return code.get(method);
    }

    private static Fiber fiber(ServiceContext context) {
        return new Fiber(context, new CallLaterRequest(null, null, new ObjectHandle[0], 0));
    }

    private static Frame caller(TestContainer container) {
        var context = new ServiceContext(container, "caller", 1) {
            @Override
            public Debugger getDebugger() {
                return new Debugger() {
                    public int activate(Frame frame, int pc) { return Op.R_NEXT; }
                    public int checkBreakPoint(Frame frame, int pc) { return Op.R_NEXT; }
                    public int checkBreakPoint(Frame frame, ObjectHandle.ExceptionHandle exception) {
                        return Op.R_EXCEPTION;
                    }
                    public void onReturn(Frame frame) {}
                };
            }
        };
        return new Frame(fiber(context), 0,
                Op.NO_OPS, new ObjectHandle[0], Op.A_IGNORE, null) {
            @Override
            public int wait(CompletableFuture<ObjectHandle> future, int result, Continuation continuation) {
                assertTrue(future.isDone());
                return future.isCompletedExceptionally() ? Op.R_EXCEPTION : continuation.proceed(this);
            }
        };
    }

    private static final class Fixture {
        final FileStructure file;
        final SingletonConstant singleton;
        final MethodStructure method;

        Fixture() {
            this(true);
        }

        Fixture(boolean freeze) {
            var source = new FileStructure("MethodExecution");
            source.merge(new FileStructure(Constants.ECSTASY_MODULE).getModule(), false, false);
            var pool = source.getConstantPool();
            var value = pool.ensureSingletonConstConstant(source.getModuleId());
            var original = source.getModule().createMethod(true, Access.PUBLIC, null,
                    new Parameter[] {new Parameter(pool, source.getModuleId().getType(),
                            null, null, true, 0, false)}, "run", Parameter.NO_PARAMS, true, false);
            var code = original.createCode();
            code.add(new Nop(6));
            code.add(new Enter());
            code.add(new Var(code.createRegister(source.getModuleId().getType())));
            code.add(new Exit());
            code.add(new Return_1(value));
            original.forceAssembly(pool);
            try {
                var binary = new ByteArrayOutputStream();
                source.writeTo(binary);
                file = new FileStructure(new ByteArrayInputStream(binary.toByteArray()));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            method = file.getModule().findMethod("run", 0);
            singleton = Arrays.stream(method.getLocalConstants()).filter(SingletonConstant.class::isInstance)
                    .map(SingletonConstant.class::cast).findFirst().orElseThrow();
            if (freeze) {
                file.ensureReadOnly();
            }
        }
    }

    private static final class TestContainer extends Container {
        final RecordingContext main;

        TestContainer(Runtime runtime, FileStructure file) {
            super(runtime, null, file.getModuleId());
            main = new RecordingContext(this);
        }

        @Override
        public ServiceContext ensureServiceContext() {
            return main;
        }

        @Override
        public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type, ObjectHandle options) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingContext extends ServiceContext {
        final ObjectHandle value = new ObjectHandle(null) {};
        int requests;
        boolean failNext;

        RecordingContext(Container container) {
            super(container, "main", 0);
        }

        @Override
        public CompletableFuture<ObjectHandle> sendConstantRequest(Frame frame, List<SingletonConstant> constants) {
            requests++;
            if (failNext) {
                failNext = false;
                return CompletableFuture.failedFuture(new IllegalStateException("injected initialization failure"));
            }
            constants.forEach(constant -> f_container.ensureSingletonState(constant).setHandle(value));
            return CompletableFuture.completedFuture(value);
        }
    }
}

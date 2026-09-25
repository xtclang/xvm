package org.xvm.runtime;

import java.util.List;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Nop;
import org.xvm.asm.op.Return_1;

import org.xvm.runtime.ServiceContext.CallLaterRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    private static Fiber fiber(ServiceContext context) {
        return new Fiber(context, new CallLaterRequest(null, null, new ObjectHandle[0], 0));
    }

    private static Frame caller(TestContainer container) {
        return new Frame(fiber(new ServiceContext(container, "caller", 1)), 0,
                Op.NO_OPS, new ObjectHandle[0], Op.A_IGNORE, null) {
            @Override
            public int wait(CompletableFuture<ObjectHandle> future, int result, Continuation continuation) {
                assertTrue(future.isDone());
                return future.isCompletedExceptionally() ? Op.R_EXCEPTION : continuation.proceed(this);
            }
        };
    }

    private static final class Fixture {
        final FileStructure file = new FileStructure("MethodExecution");
        final SingletonConstant singleton;
        final MethodStructure method;

        Fixture() {
            file.merge(new FileStructure(Constants.ECSTASY_MODULE).getModule(), false, false);
            var pool = file.getConstantPool();
            singleton = pool.ensureSingletonConstConstant(file.getModuleId());
            method = file.getModule().createMethod(true, Access.PUBLIC, null,
                    new Parameter[] {new Parameter(pool, file.getModuleId().getType(),
                            null, null, true, 0, false)}, "run", Parameter.NO_PARAMS, true, false);
            var code = method.createCode();
            code.add(new Nop(6));
            code.add(new Return_1(singleton));
            method.forceAssembly(pool);
            file.ensureReadOnly();
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

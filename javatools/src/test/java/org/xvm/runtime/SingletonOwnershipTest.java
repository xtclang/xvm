package org.xvm.runtime;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.xvm.asm.FileStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ServiceContext.CallLaterRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingletonOwnershipTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void moduleSharingSelectsTheOwnerEvenForALocalAlias(boolean shared) {
        try (var runtime = new Runtime()) {
            var file = new FileStructure("App");
            var parent = new TestContainer(runtime, null, file, false);
            var child = new TestContainer(runtime, parent, new FileStructure(file), shared);
            var original = parent.getConstantPool().ensureSingletonConstConstant(parent.getModule());
            var value = new ObjectHandle(null) {};
            original.setHandle(value);
            var alias = child.getConstantPool().register(original);
            var selected = child.ensureSingletonConstant(alias);
            assertSame(selected, child.ensureSingletonConstant(original));
            if (shared) {
                assertSame(original, selected);
                assertSame(value, selected.getHandle());
            } else {
                assertSame(alias, selected);
                assertNull(selected.getHandle());
            }
            assertSame(value, original.getHandle());
        }
    }

    @Test
    void mixedOwnerInitializationIsDispatchedOneOwnerAtATime() {
        try (var runtime = new Runtime()) {
            var parent = new TestContainer(runtime, null, new FileStructure("Shared"), false);
            var child = new TestContainer(runtime, parent, new FileStructure("Child"), true);
            var shared = parent.getConstantPool().ensureSingletonConstConstant(parent.getModule());
            var alias = child.getConstantPool().register(shared);
            var local = child.getConstantPool().ensureSingletonConstConstant(child.getModule());
            var frame = new ImmediateFrame(new ServiceContext(child, "worker", 1));
            var definitions = List.of(alias, local);

            assertEquals(Op.R_NEXT, Utils.initConstants(frame, definitions, _ -> Op.R_NEXT));
            assertEquals(1, parent.main.requests);
            assertEquals(1, child.main.requests);
            assertSame(parent.main.value, shared.getHandle());
            assertSame(child.main.value, local.getHandle());
            assertNotSame(shared.getHandle(), local.getHandle());
            assertNull(alias.getHandle(), "The alias must not own a separate initialization");

            assertEquals(Op.R_NEXT, Utils.initConstants(frame, definitions, _ -> Op.R_NEXT));
            assertEquals(1, parent.main.requests);
            assertEquals(1, child.main.requests);
        }
    }

    @Test
    void adoptingADefinitionDoesNotShareInitializationWaiters() {
        try (var runtime = new Runtime()) {
            var file = new FileStructure("App");
            var first = new TestContainer(runtime, null, file, false);
            var second = new TestContainer(runtime, first, new FileStructure(file), false);
            var original = first.getConstantPool().ensureSingletonConstConstant(first.getModule());
            var firstInitializer = fiber(first.main);
            var firstWaiter = fiber(first.main);
            assertTrue(original.markInitializing(firstInitializer));
            var originalCompletion = original.getInitializationWaiter(firstWaiter);
            assertSame(original, first.getConstantPool().register(original));
            assertSame(originalCompletion, original.getInitializationWaiter(firstWaiter));

            var copy = second.ensureSingletonConstant(original);
            assertNotSame(original, copy);
            assertTrue(copy.markInitializing(fiber(second.main)));
            var copyCompletion = copy.getInitializationWaiter(fiber(second.main));
            assertNotSame(originalCompletion, copyCompletion);
            original.setHandle(first.main.value);
            assertSame(first.main.value, originalCompletion.join());
            assertFalse(copyCompletion.isDone());
            copy.setHandle(second.main.value);
            assertSame(second.main.value, copyCompletion.join());
            assertSame(first.main.value, original.getHandle());
        }
    }

    @Test
    void failedInitializationReleasesWaitersAndAllowsAFreshAttempt() {
        try (var runtime = new Runtime()) {
            var owner = new TestContainer(runtime, null, new FileStructure("App"), false);
            var singleton = owner.getConstantPool().ensureSingletonConstConstant(owner.getModule());
            assertTrue(singleton.markInitializing(fiber(owner.main)));
            var waiting = singleton.getInitializationWaiter(fiber(owner.main));
            var failure = new IllegalStateException("expected constructor failure");
            singleton.abortInitialization(failure);
            assertSame(failure, assertThrows(CompletionException.class, waiting::join).getCause());
            assertNull(singleton.getHandle());

            assertTrue(singleton.markInitializing(fiber(owner.main)));
            var retry = singleton.getInitializationWaiter(fiber(owner.main));
            assertNotSame(waiting, retry);
            singleton.setHandle(owner.main.value);
            assertSame(owner.main.value, retry.join());
            assertTrue(waiting.isCompletedExceptionally());
        }
    }

    private static Fiber fiber(ServiceContext context) {
        return new Fiber(context, new CallLaterRequest(null, null, new ObjectHandle[0], 0));
    }

    private static final class TestContainer extends Container {
        private final boolean shared;
        private final RecordingContext main;

        private TestContainer(Runtime runtime, Container parent, FileStructure file, boolean shared) {
            super(runtime, parent, file.getModuleId());
            this.shared = shared;
            main = new RecordingContext(this);
        }

        @Override
        public boolean isShared(ModuleConstant module) {
            return shared && f_parent != null && module.equals(f_parent.getModule());
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

    /** Completes a request synchronously so routing assertions do not depend on scheduling. */
    private static final class RecordingContext extends ServiceContext {
        private final ObjectHandle value = new ObjectHandle(null) {};
        private int requests;

        private RecordingContext(Container container) {
            super(container, "main", 0);
        }

        @Override
        public CompletableFuture<ObjectHandle> sendConstantRequest(Frame frame, List<SingletonConstant> constants) {
            assertEquals(1, constants.size());
            var constant = constants.getFirst();
            assertSame(f_container.getConstantPool(), constant.getConstantPool());
            assertTrue(constant.markInitializing(fiber(this)),
                    "The caller must not claim initialization before reaching the owner's service");
            requests++;
            constant.setHandle(value);
            return CompletableFuture.completedFuture(value);
        }
    }

    private static final class ImmediateFrame extends Frame {
        private ImmediateFrame(ServiceContext context) {
            super(fiber(context), 0, new Op[0], new ObjectHandle[0], Op.A_IGNORE, null);
        }

        @Override
        public int wait(CompletableFuture<ObjectHandle> future, int result, Continuation continuation) {
            assertTrue(future.isDone());
            return continuation.proceed(this);
        }
    }
}

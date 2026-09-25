package org.xvm.runtime;

import java.util.List;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.InitializingHandle;
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
    void identicalDefinitionObjectsDoNotGrantValueSharing(boolean shared) {
        var runtime = new Runtime();
        try {
            var file = image();
            var parent = new TestContainer(runtime, null, file, false);
            var child = new TestContainer(runtime, parent, file, shared);
            var definition = file.getConstantPool().ensureSingletonConstConstant(file.getModuleId());
            var parentState = parent.ensureSingletonState(definition);
            var childState = child.ensureSingletonState(definition);
            assertSame(definition, parentState.getDefinition());
            assertSame(definition, childState.getDefinition());
            parentState.setHandle(parent.main.value);
            if (shared) {
                assertSame(parentState, childState);
                assertSame(parent.main.value, childState.getHandle());
            } else {
                assertNotSame(parentState, childState);
                assertNull(childState.getHandle());
                childState.setHandle(child.main.value);
                assertSame(child.main.value, childState.getHandle());
                assertSame(parent.main.value, parentState.getHandle());
            }
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    @Timeout(10)
    void concurrentLookupPublishesOneEntryWithoutAnAmbientPoolDependency() throws Exception {
        var runtime = new Runtime();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var owner = new TestContainer(runtime, null, image(), false);
            var definition = owner.getConstantPool().ensureSingletonConstConstant(owner.getModule());
            var unrelated = new FileStructure("Unrelated").getConstantPool();
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            // Canonicalization is completed above; this checks concurrent entry publication, not
            // unsupported concurrent writes to a mutable pool or parallel initialization.
            var first = threads.submit(() -> {
                try (var scope = ConstantPool.withPool(null)) {
                    ready.countDown();
                    start.await();
                    return owner.ensureSingletonState(definition);
                }
            });
            var second = threads.submit(() -> {
                try (var scope = ConstantPool.withPool(unrelated)) {
                    ready.countDown();
                    start.await();
                    return owner.ensureSingletonState(definition);
                }
            });
            try {
                ready.await();
            } finally {
                start.countDown();
            }
            assertSame(first.get(), second.get());
            assertSame(definition, first.get().getDefinition());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    void recursiveHandleAndMultipleWaitersStayWithTheirSelectedOwner() {
        var runtime = new Runtime();
        try {
            var file = image();
            var owner = new TestContainer(runtime, null, file, false);
            var sibling = new TestContainer(runtime, null, file, false);
            var definition = file.getConstantPool().ensureSingletonConstConstant(file.getModuleId());
            var state = owner.ensureSingletonState(definition);
            var initializer = fiber(owner.main);
            assertTrue(state.markInitializing(initializer));
            assertFalse(state.markInitializing(fiber(owner.main)));
            var waiting = state.getInitializationWaiter(fiber(owner.main));
            assertSame(waiting, state.getInitializationWaiter(fiber(owner.main)));
            var first = waiting.thenApply(value -> value);
            var second = waiting.thenApply(value -> value);
            assertNull(state.getInitializationWaiter(initializer));
            var recursive = (InitializingHandle) state.getHandle();
            assertNull(state.getInitializationWaiter(initializer));
            assertSame(recursive, state.getHandle());
            assertNull(recursive.getInitialized());
            assertThrows(IllegalStateException.class, recursive::assertInitialized);

            sibling.ensureSingletonState(definition).setHandle(sibling.main.value);
            assertNull(recursive.getInitialized());
            assertFalse(waiting.isDone());
            state.setHandle(owner.main.value);
            assertSame(owner.main.value, first.join());
            assertSame(owner.main.value, second.join());
            assertSame(owner.main.value, recursive.getInitialized());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void moduleSharingSelectsTheOwnerEvenForALocalAlias(boolean shared) {
        var runtime = new Runtime();
        try {
            var file = image();
            var parent = new TestContainer(runtime, null, file, false);
            var child = new TestContainer(runtime, parent, new FileStructure(file), shared);
            var original = parent.getConstantPool().ensureSingletonConstConstant(parent.getModule());
            var value = new ObjectHandle(null) {};
            parent.ensureSingletonState(original).setHandle(value);
            var alias = child.getConstantPool().register(original);
            var selected = child.ensureSingletonConstant(alias);
            assertSame(selected, child.ensureSingletonConstant(original));
            if (shared) {
                assertSame(original, selected);
                assertSame(value, child.ensureSingletonState(selected).getHandle());
            } else {
                assertSame(alias, selected);
                assertNull(child.ensureSingletonState(selected).getHandle());
            }
            assertSame(value, parent.ensureSingletonState(original).getHandle());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    void mixedOwnerInitializationIsDispatchedOneOwnerAtATime() {
        var runtime = new Runtime();
        try {
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
            assertSame(parent.main.value, parent.ensureSingletonState(shared).getHandle());
            assertSame(child.main.value, child.ensureSingletonState(local).getHandle());
            assertNotSame(parent.ensureSingletonState(shared).getHandle(), child.ensureSingletonState(local).getHandle());
            assertSame(parent.ensureSingletonState(shared), child.ensureSingletonState(alias));

            assertEquals(Op.R_NEXT, Utils.initConstants(frame, definitions, _ -> Op.R_NEXT));
            assertEquals(1, parent.main.requests);
            assertEquals(1, child.main.requests);
        } finally {
            runtime.shutdownXVM();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unsharedOwnersHaveIndependentInitializationWaiters(boolean copiedDefinition) {
        var runtime = new Runtime();
        try {
            var file = image();
            var first = new TestContainer(runtime, null, file, false);
            var second = new TestContainer(runtime, first,
                    copiedDefinition ? new FileStructure(file) : file, false);
            var original = first.getConstantPool().ensureSingletonConstConstant(first.getModule());
            var firstInitializer = fiber(first.main);
            var firstWaiter = fiber(first.main);
            var originalState = first.ensureSingletonState(original);
            assertTrue(originalState.markInitializing(firstInitializer));
            var originalCompletion = originalState.getInitializationWaiter(firstWaiter);
            assertSame(original, first.getConstantPool().register(original));
            assertSame(originalCompletion, originalState.getInitializationWaiter(firstWaiter));

            var copy = second.ensureSingletonConstant(original);
            assertEquals(copiedDefinition, original != copy);
            var copyState = second.ensureSingletonState(copy);
            assertTrue(copyState.markInitializing(fiber(second.main)));
            var copyCompletion = copyState.getInitializationWaiter(fiber(second.main));
            assertNotSame(originalCompletion, copyCompletion);
            originalState.setHandle(first.main.value);
            assertSame(first.main.value, originalCompletion.join());
            assertFalse(copyCompletion.isDone());
            copyState.setHandle(second.main.value);
            assertSame(second.main.value, copyCompletion.join());
            assertSame(first.main.value, originalState.getHandle());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    void failedInitializationReleasesWaitersAndAllowsAFreshAttempt() {
        var runtime = new Runtime();
        try {
            var owner = new TestContainer(runtime, null, image(), false);
            var singleton = owner.ensureSingletonState(
                    owner.getConstantPool().ensureSingletonConstConstant(owner.getModule()));
            var initializer = fiber(owner.main);
            assertTrue(singleton.markInitializing(initializer));
            var waiting = singleton.getInitializationWaiter(fiber(owner.main));
            assertNull(singleton.getInitializationWaiter(initializer));
            var recursive = (InitializingHandle) singleton.getHandle();
            var failure = new IllegalStateException("expected constructor failure");
            singleton.abortInitialization(failure);
            assertSame(failure, assertThrows(CompletionException.class, waiting::join).getCause());
            assertNull(singleton.getHandle());
            assertNull(recursive.getInitialized());
            assertThrows(IllegalStateException.class, recursive::assertInitialized);

            assertTrue(singleton.markInitializing(fiber(owner.main)));
            var retry = singleton.getInitializationWaiter(fiber(owner.main));
            assertNotSame(waiting, retry);
            singleton.setHandle(owner.main.value);
            assertSame(owner.main.value, retry.join());
            assertTrue(waiting.isCompletedExceptionally());
        } finally {
            runtime.shutdownXVM();
        }
    }

    private static FileStructure image() {
        var file = new FileStructure("App");
        // Runtime descriptors for module names require the system-module identity for String.
        // A bundled stub supplies that graph without compiled XDK artifacts or native bootstrap.
        file.merge(new FileStructure(Constants.ECSTASY_MODULE).getModule(), false, false);
        return file;
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
            var state = f_container.ensureSingletonState(constant);
            assertTrue(state.markInitializing(fiber(this)),
                    "The caller must not claim initialization before reaching the owner's service");
            requests++;
            state.setHandle(value);
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

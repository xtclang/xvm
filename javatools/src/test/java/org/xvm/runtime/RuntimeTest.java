package org.xvm.runtime;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.VersionConstant;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class RuntimeTest {
    /**
     * Registry lookup and registration must use the same monitor. Otherwise same-JVM container
     * startup can race with lookup and observe a partially updated weak registry.
     */
    @Test
    public void findContainerSharesWeakRegistryMonitorWithRegistration()
            throws Exception {
        Runtime        runtime     = new Runtime();
        CountDownLatch lookupStart = new CountDownLatch(1);
        CountDownLatch writerDone  = new CountDownLatch(1);
        AtomicBoolean  signaled    = new AtomicBoolean();

        IntStream.range(0, 8)
                .mapToObj(i -> new TestContainer(
                        runtime, "existing" + i, lookupStart, writerDone, signaled))
                .forEach(runtime::registerContainer);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var lookup = executor.submit(() -> runtime.findContainer(
                    new FileStructure("missing").getConstantPool()));
            var writer = executor.submit(() -> {
                lookupStart.await();
                runtime.registerContainer(new TestContainer(runtime, "added"));
                writerDone.countDown();
                return null;
            });

            assertNull(lookup.get(2, TimeUnit.SECONDS));
            writer.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            runtime.shutdownXVM();
        }
    }

    /**
     * Runtime registration must happen after container construction. The old constructor-time
     * registration let the registry expose a partially constructed container.
     */
    @Test
    public void registerContainerDoesNotObservePartiallyConstructedContainer() {
        var runtime = new ObservingRuntime();
        try {
            var parent    = new TestContainer(runtime, "parent");
            var container = runtime.registerContainer(new TestContainer(runtime, parent, "registered"));

            assertSame(container, runtime.findContainer(container.getConstantPool()));
            assertSame(container, runtime.observed);
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * Runtime op caches hold owner-bearing values such as switch case handles and type constants.
     * The cache key must therefore include the executing container, not only the decoded op object.
     */
    @Test
    public void runtimeOpCacheIsContainerLocalAndTyped() {
        var runtime = new Runtime();
        try {
            var left           = new TestContainer(runtime, "left");
            var right          = new TestContainer(runtime, "right");
            var op             = new TestOp();
            var leftCache      = new TestCache("left");
            var duplicateCache = new TestCache("duplicate");
            var rightCache     = new TestCache("right");

            assertNull(left.getRuntimeOpCache(op, RuntimeCacheCategory.SWITCH, TestCache.class));
            assertSame(leftCache, left.putRuntimeOpCacheIfAbsent(
                    op, RuntimeCacheCategory.SWITCH, leftCache, TestCache.class));
            assertSame(leftCache, left.putRuntimeOpCacheIfAbsent(
                    op, RuntimeCacheCategory.SWITCH, duplicateCache, TestCache.class));
            assertSame(leftCache, left.getRuntimeOpCache(
                    op, RuntimeCacheCategory.SWITCH, TestCache.class));

            assertNull(right.getRuntimeOpCache(op, RuntimeCacheCategory.SWITCH, TestCache.class));
            assertSame(rightCache, right.putRuntimeOpCacheIfAbsent(
                    op, RuntimeCacheCategory.SWITCH, rightCache, TestCache.class));
            assertSame(rightCache, right.getRuntimeOpCache(
                    op, RuntimeCacheCategory.SWITCH, TestCache.class));

            assertThrows(ClassCastException.class,
                    () -> left.getRuntimeOpCache(op, RuntimeCacheCategory.SWITCH, String.class));
        } finally {
            runtime.shutdownXVM();
        }
    }

    private static class TestContainer
            extends Container {
        private final CountDownLatch lookupStart;
        private final CountDownLatch writerDone;
        private final AtomicBoolean signaled;
        private final FileStructure file;

        TestContainer(Runtime runtime, String name) {
            this(runtime, name, null, null, null);
        }

        TestContainer(Runtime runtime, Container parent, String name) {
            this(runtime, parent, new FileStructure(name), null, null, null);
        }

        TestContainer(Runtime runtime, String name, CountDownLatch lookupStart,
                      CountDownLatch writerDone, AtomicBoolean signaled) {
            this(runtime, null, new FileStructure(name), lookupStart, writerDone, signaled);
        }

        TestContainer(Runtime runtime, Container parent, FileStructure file, CountDownLatch lookupStart,
                      CountDownLatch writerDone, AtomicBoolean signaled) {
            super(runtime, parent, file.getModuleId());

            this.file        = file;
            this.lookupStart = lookupStart;
            this.writerDone  = writerDone;
            this.signaled    = signaled;
        }

        @Override
        public ConstantPool getConstantPool() {
            if (file == null) {
                throw new IllegalStateException(
                        "container was observed before subclass construction completed");
            }
            if (signaled != null && signaled.compareAndSet(false, true)) {
                lookupStart.countDown();
                try {
                    writerDone.await(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            return super.getConstantPool();
        }

        @Override
        public boolean isSpecified(String name) {
            return false;
        }

        @Override
        public boolean isPresent(IdentityConstant id) {
            return false;
        }

        @Override
        public boolean isVersionMatch(ModuleConstant module, VersionConstant version) {
            return false;
        }

        @Override
        public boolean isVersion(VersionConstant version) {
            return false;
        }

        @Override
        public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type,
                                          ObjectHandle opts) {
            return null;
        }
    }

    private static class ObservingRuntime
            extends Runtime {
        private Container observed;

        @Override
        public <C extends Container> C registerContainer(C container) {
            C registered = super.registerContainer(container);
            observed = findContainer(container.getConstantPool());
            return registered;
        }
    }

    private record TestCache(String owner) {}

    private enum RuntimeCacheCategory {SWITCH}

    private static class TestOp
            extends Op {
        @Override
        public int getOpCode() {
            return 0;
        }

        @Override
        public int process(Frame frame, int iPC) {
            return iPC + 1;
        }
    }
}

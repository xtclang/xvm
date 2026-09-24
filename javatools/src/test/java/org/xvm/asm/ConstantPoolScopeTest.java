package org.xvm.asm;

import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(30)
class ConstantPoolScopeTest {
    @Test
    void unboundReadsAndClosedScopesRetainNoPoolOrTypedHolder() throws Exception {
        var field = ConstantPool.class.getDeclaredField("s_tloPool");
        field.setAccessible(true);
        var local = (ThreadLocal<?>) field.get(null);
        local.remove();
        try {
            assertNull(ConstantPool.getCurrentPool());
            assertNull(local.get());
            var pool = new FileStructure("test").getConstantPool();
            try (var scope = ConstantPool.withPool(pool)) {
                assertSame(pool, local.get());
            }
            // Inspect the value directly; no GC or class-unloading timing is involved.
            assertNull(local.get());
            assertNull(ConstantPool.getCurrentPool());
            ConstantPool.setCurrentPool(pool);
            ConstantPool.setCurrentPool(null);
            assertNull(local.get());
        } finally {
            local.remove();
        }
    }

    @Test
    void nestedEmptyScopesStillRestoreTheCallerPool() {
        var outer = new FileStructure("outer").getConstantPool();
        var inner = new FileStructure("inner").getConstantPool();
        try (var host = ConstantPool.withPool(outer)) {
            try (var empty = ConstantPool.withPool(null)) {
                try (var request = ConstantPool.withPool(inner)) {
                    assertSame(inner, ConstantPool.getCurrentPool());
                }
                assertNull(ConstantPool.getCurrentPool());
            }
            assertSame(outer, ConstantPool.getCurrentPool());
        }
    }

    @Test
    void aWorkerDoesNotInheritTheCallersBindingButCanBindTheSamePool() throws Exception {
        var pool = new FileStructure("shared").getConstantPool();
        try (var caller = ConstantPool.withPool(pool);
             var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                assertNull(ConstantPool.getCurrentPool());
                // Binding the reference on another thread neither clones nor transfers the pool.
                try (var scope = ConstantPool.withPool(pool)) {
                    assertSame(pool, ConstantPool.getCurrentPool());
                }
                assertNull(ConstantPool.getCurrentPool());
            }).get();
            assertSame(pool, ConstantPool.getCurrentPool());
        }
    }

    @Test
    void reusedWorkerRestoresItsOwnBindingAfterAnException() throws Exception {
        var host = new FileStructure("host").getConstantPool();
        var request = new FileStructure("request").getConstantPool();
        var failure = new IllegalStateException("request failed");
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                assertNull(ConstantPool.getCurrentPool());
                try (var outer = ConstantPool.withPool(host)) {
                    assertSame(failure, assertThrows(IllegalStateException.class, () -> {
                        try (var inner = ConstantPool.withPool(request)) {
                            assertSame(request, ConstantPool.getCurrentPool());
                            throw failure;
                        }
                    }));
                    assertSame(host, ConstantPool.getCurrentPool());
                }
            }).get();
            // A single-thread executor guarantees that the next task checks the same worker.
            worker.submit(() -> assertNull(ConstantPool.getCurrentPool())).get();
        }
    }

    @Test
    void closingAScopeOnAnotherThreadCannotChangeEitherBinding() throws Exception {
        var callerPool = new FileStructure("caller").getConstantPool();
        var workerPool = new FileStructure("worker").getConstantPool();
        try (var caller = ConstantPool.withPool(null);
             var scope = ConstantPool.withPool(callerPool);
             var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                try (var ownScope = ConstantPool.withPool(workerPool)) {
                    assertThrows(IllegalStateException.class, scope::close);
                    assertSame(workerPool, ConstantPool.getCurrentPool());
                }
                assertNull(ConstantPool.getCurrentPool());
            }).get();
            assertSame(callerPool, ConstantPool.getCurrentPool());
        }
    }
}

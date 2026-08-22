package org.xvm.runtime;


import java.nio.file.attribute.FileTime;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constant;
import org.xvm.asm.FileStructure;

import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.FileStoreConstant;
import org.xvm.asm.constants.SingletonConstant;

import org.xvm.runtime.ObjectHandle.InitializingHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for singleton runtime initialization state.
 */
public class SingletonConstantTest {
    /**
     * Singleton initialization must have exactly one owner. The old split fields could let
     * concurrent fibers observe mixed owner/handle state.
     */
    @Test
    public void concurrentInitializationHasSingleOwner() throws Exception {
        SingletonConstant constant = createConstant();
        int               cThreads = 32;
        CountDownLatch    ready    = new CountDownLatch(cThreads);
        CountDownLatch    start    = new CountDownLatch(1);
        ExecutorService   service  = Executors.newFixedThreadPool(cThreads);
        try {
            List<Future<Boolean>> results = new ArrayList<>(cThreads);

            for (int i = 0; i < cThreads; i++) {
                results.add(service.submit(() -> {
                    ready.countDown();
                    start.await();
                    return constant.markInitializing(createFiber());
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int cOwners = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    cOwners++;
                }
            }

            assertEquals(1, cOwners);
        } finally {
            service.shutdownNow();
        }
    }

    /**
     * Concurrent waiters must share one completion path. This proves the atomic state snapshot
     * replacement preserves waiter behavior without split mutable fields.
     */
    @Test
    public void concurrentWaitersShareCompletion() throws Exception {
        SingletonConstant constant = createConstant();

        assertTrue(constant.markInitializing(createFiber()));

        CompletableFuture<ObjectHandle> waiter1 = constant.getInitializationWaiter(createFiber());
        CompletableFuture<ObjectHandle> waiter2 = constant.getInitializationWaiter(createFiber());

        assertSame(waiter1, waiter2);
        assertFalse(waiter1.isDone());

        constant.setHandle(ObjectHandle.DEFAULT);

        assertSame(ObjectHandle.DEFAULT, waiter1.get(5, TimeUnit.SECONDS));
        assertSame(ObjectHandle.DEFAULT, constant.getHandle());
    }

    /**
     * Same-fiber recursion should install a placeholder without creating a waiter. This preserves
     * the legacy recursive initialization semantics while making state transitions atomic.
     */
    @Test
    public void sameFiberRecursionInstallsPlaceholderWithoutWaiter() {
        SingletonConstant constant = createConstant();
        Fiber             fiber    = createFiber();

        assertTrue(constant.markInitializing(fiber));
        assertNull(constant.getInitializationWaiter(fiber));
        assertInstanceOf(InitializingHandle.class, constant.getHandle());
    }

    /**
     * Adopting a SingletonConstant into another pool must create owner-local runtime state. A
     * shallow clone would copy handle/waiter state from the source pool.
     */
    @Test
    public void adoptedSingletonHasOwnerLocalRuntimeState() {
        SingletonConstant source = createConstant();
        FileStructure     target = new FileStructure("target");

        source.setHandle(ObjectHandle.DEFAULT);
        SingletonConstant adopted = target.getConstantPool().register(source);

        assertNotSame(source, adopted);
        assertSame(ObjectHandle.DEFAULT, source.getHandle());
        assertNull(adopted.getHandle());
        assertTrue(adopted.markInitializing(createFiber()));
    }

    /**
     * FS node adoption must clear owner-local handles. Filesystem handles are runtime state, not
     * logical constant data that can be shared across pools.
     */
    @Test
    public void adoptedFsNodeClearsOwnerLocalHandle() {
        FileStructure  sourceFile = new FileStructure("source");
        byte[]         contents   = {1, 2, 3};
        FSNodeConstant source     = new FSNodeConstant(
                sourceFile.getConstantPool(),
                "data.bin",
                FileTime.fromMillis(1),
                FileTime.fromMillis(2),
                contents);
        FileStructure targetFile = new FileStructure("target");

        source.setHandle(ObjectHandle.DEFAULT);
        FSNodeConstant adopted = targetFile.getConstantPool().register(source);

        assertNotSame(source, adopted);
        assertSame(ObjectHandle.DEFAULT, source.getHandle());
        assertNull(adopted.getHandle());
    }

    /**
     * File-store adoption must clear owner-local handles for the same reason as FS nodes: handles
     * belong to the runtime/container owner that created them.
     */
    @Test
    public void adoptedFileStoreClearsOwnerLocalHandles() {
        FileStructure  sourceFile = new FileStructure("source");
        FSNodeConstant sourceRoot = new FSNodeConstant(
                sourceFile.getConstantPool(),
                "root",
                FileTime.fromMillis(1),
                FileTime.fromMillis(2),
                FSNodeConstant.NO_NODES);
        FileStoreConstant source = new FileStoreConstant(
                sourceFile.getConstantPool(), "/tmp/root", sourceRoot);
        FileStructure targetFile = new FileStructure("target");

        sourceRoot.setHandle(ObjectHandle.DEFAULT);
        source.setHandle(ObjectHandle.DEFAULT);
        FileStoreConstant adopted = targetFile.getConstantPool().register(source);

        assertNotSame(source, adopted);
        assertSame(ObjectHandle.DEFAULT, source.getHandle());
        assertNull(adopted.getHandle());
        assertNull(adopted.getValue().getHandle());
    }

    private static SingletonConstant createConstant() {
        FileStructure file = new FileStructure("test");
        return new SingletonConstant(file.getConstantPool(), Constant.Format.SingletonConst,
                file.getModule().getIdentityConstant());
    }

    private static Fiber createFiber() {
        return new Fiber();
    }
}

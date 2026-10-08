package org.xvm.asm;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Token.Id;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Host operations select a working pool at their boundary and restore the caller's context.
 */
public class ConstantPoolAmbientTest {
    @Test
    public void hostScopeRestoresAnUnboundThread() {
        assertNull(ConstantPool.getCurrentPool());
        ConstantPool pool = new FileStructure("host").getConstantPool();

        try (var _ = ConstantPool.withPool(pool)) {
            assertSame(pool, ConstantPool.getCurrentPool());
        }

        assertNull(ConstantPool.getCurrentPool());
    }

    @Test
    public void nestedScopeRestoresTheCallerAfterFailure() {
        ConstantPool caller = new FileStructure("caller").getConstantPool();
        ConstantPool worker = new FileStructure("worker").getConstantPool();
        var failure = new IllegalStateException("host operation failed");

        try (var _ = ConstantPool.withPool(caller)) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> {
                try (var _ = ConstantPool.withPool(worker)) {
                    assertSame(worker, ConstantPool.getCurrentPool());
                    throw failure;
                }
            }));
            assertSame(caller, ConstantPool.getCurrentPool());
        }

        assertNull(ConstantPool.getCurrentPool());
    }

    @Test
    public void crossPoolOperationsUseTheWorkingPoolRatherThanTheConstantOwner() {
        ConstantPool owner = new FileStructure("owner").getConstantPool();
        ConstantPool worker = new FileStructure("worker").getConstantPool();
        var first = owner.ensureByteConstant(Constant.Format.UInt8, 1);
        var last = owner.ensureByteConstant(Constant.Format.UInt8, 3);

        try (var _ = ConstantPool.withPool(worker)) {
            assertSame(worker, first.apply(Id.I_RANGE_I, last).getConstantPool());
            assertSame(owner, first.getConstantPool());
        }

        assertNull(ConstantPool.getCurrentPool());
    }

    @Test
    public void executorWorkBindsItsOwnPoolWithoutLeakingToTheNextTask() throws Exception {
        ConstantPool caller = new FileStructure("caller").getConstantPool();
        ConstantPool worker = new FileStructure("worker").getConstantPool();

        try (var _ = ConstantPool.withPool(caller);
             var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                assertNull(ConstantPool.getCurrentPool(), "pool context is not inherited");
                try (var _ = ConstantPool.withPool(worker)) {
                    assertSame(worker, ConstantPool.getCurrentPool());
                }
                assertNull(ConstantPool.getCurrentPool());
            }).get(10, TimeUnit.SECONDS);
            assertNull(executor.submit(ConstantPool::getCurrentPool).get(10, TimeUnit.SECONDS));
            assertSame(caller, ConstantPool.getCurrentPool());
        }

        assertNull(ConstantPool.getCurrentPool());
    }
}

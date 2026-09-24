package org.xvm.asm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}

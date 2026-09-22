package org.xvm.asm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConstantPoolScopeTest {
    @Test
    void closingAnEmptyHostScopeReleasesTheTypedHolder() throws Exception {
        var field = ConstantPool.class.getDeclaredField("s_tloPool");
        field.setAccessible(true);
        var local = (ThreadLocal<?>) field.get(null);
        local.remove();
        try {
            Object previous = local.get();
            try (var scope = ConstantPool.withPool(new FileStructure("test").getConstantPool())) {
                assertSame(previous, local.get());
            }
            // Inspect the holder directly: relying on GC or class unloading would make this flaky.
            assertNotSame(previous, local.get(), "An empty typed array must not retain the XDK loader");
            assertNull(ConstantPool.getCurrentPool());
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

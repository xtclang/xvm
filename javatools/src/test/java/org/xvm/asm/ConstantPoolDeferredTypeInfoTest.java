package org.xvm.asm;

import java.util.List;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the compiler's deferred TypeInfo queue through its consumer API.
 */
class ConstantPoolDeferredTypeInfoTest {
    @Test
    void emptyProbesDoNotPreventDeferringAndDrainingTypes() {
        var pool = new FileStructure("deferred").getConstantPool();
        var text = pool.typeString();
        var number = pool.typeInt64();

        assertFalse(pool.hasDeferredTypeInfo());
        assertTrue(pool.takeDeferredTypeInfo().isEmpty());
        assertFalse(pool.hasDeferredTypeInfo());

        pool.addDeferredTypeInfo(text);
        pool.addDeferredTypeInfo(number);
        pool.addDeferredTypeInfo(text);
        assertTrue(pool.hasDeferredTypeInfo());
        assertEquals(List.of(text, number), pool.takeDeferredTypeInfo());
        assertFalse(pool.hasDeferredTypeInfo());
        assertTrue(pool.takeDeferredTypeInfo().isEmpty());

        pool.addDeferredTypeInfo(number);
        assertEquals(List.of(number), pool.takeDeferredTypeInfo());
        assertFalse(pool.hasDeferredTypeInfo());
    }

    @Test
    void deferredTypesStayIsolatedAcrossPoolsAndReusedWorkers() throws Exception {
        var pool = new FileStructure("shared").getConstantPool();
        var other = new FileStructure("other").getConstantPool();
        var text = pool.typeString();
        var number = pool.typeInt64();
        pool.addDeferredTypeInfo(text);

        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                assertFalse(pool.hasDeferredTypeInfo());
                pool.addDeferredTypeInfo(number);
                assertFalse(other.hasDeferredTypeInfo());
                other.addDeferredTypeInfo(other.typeBoolean());
                assertEquals(List.of(number), pool.takeDeferredTypeInfo());
                assertTrue(other.hasDeferredTypeInfo());
                assertEquals(List.of(other.typeBoolean()), other.takeDeferredTypeInfo());
            }).get(10, TimeUnit.SECONDS);

            worker.submit(() -> {
                assertFalse(pool.hasDeferredTypeInfo());
                assertFalse(other.hasDeferredTypeInfo());
                pool.addDeferredTypeInfo(text);
                assertEquals(List.of(text), pool.takeDeferredTypeInfo());
            }).get(10, TimeUnit.SECONDS);

            assertEquals(List.of(text), pool.takeDeferredTypeInfo());
            assertFalse(other.hasDeferredTypeInfo());
        } finally {
            pool.takeDeferredTypeInfo();
            other.takeDeferredTypeInfo();
        }
    }
}

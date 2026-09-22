package org.xvm.util;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TransientThreadLocalTest {
    @Test
    void absentValuesDoNotRetainTheirOwner() throws Exception {
        var local = new TransientThreadLocal<Object>();
        var entries = entries();
        assertNull(local.get());
        assertFalse(entries.containsKey(local));
        local.set(new Object());
        local.set(null);
        assertFalse(entries.containsKey(local));
    }

    @Test
    void nestedScopesRestoreValuesAndReleaseAbsentOwners() throws Exception {
        var local = new TransientThreadLocal<String>();
        try (var outer = local.push("outer")) {
            try (var inner = local.push("inner")) {
                assertEquals("inner", local.get());
            }
            assertEquals("outer", local.get());
        }
        assertFalse(entries().containsKey(local));
    }

    @Test
    void nonNullInitialValuesRemainCached() {
        var local = TransientThreadLocal.withInitial(Object::new);
        try {
            Object value = local.get();
            assertSame(value, local.get());
        } finally {
            local.remove();
        }
    }

    private static Map<?, ?> entries() throws Exception {
        var field = TransientThreadLocal.class.getDeclaredField("TRANSIENT_MAP");
        field.setAccessible(true);
        return (Map<?, ?>) ((ThreadLocal<?>) field.get(null)).get();
    }
}

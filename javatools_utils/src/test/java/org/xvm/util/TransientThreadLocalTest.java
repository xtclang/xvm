package org.xvm.util;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransientThreadLocalTest {
    @Test
    void nullReadsDoNotRetainShortLivedLocals() throws ReflectiveOperationException {
        var map    = currentValues();
        int before = map.size();
        var locals = IntStream.range(0, 1_000)
                .mapToObj(index -> new TransientThreadLocal<>())
                .toList();
        try {
            locals.forEach(local -> assertNull(local.get()));
            assertEquals(before, map.size(), "Null reads must not register strong map keys");
        } finally {
            locals.forEach(TransientThreadLocal::remove);
        }
    }

    @Test
    void settingNullReleasesTheEntry() throws ReflectiveOperationException {
        var map   = currentValues();
        var local = new TransientThreadLocal<String>();
        try {
            local.set("value");
            assertTrue(map.containsKey(local));
            local.set(null);
            assertFalse(map.containsKey(local));
            assertNull(local.get());
            assertFalse(map.containsKey(local));
        } finally {
            local.remove();
        }
    }

    @Test
    void initialValuesAndNestedScopesRetainTheirSemantics() {
        var calls = new AtomicInteger();
        var local = TransientThreadLocal.withInitial(() ->
                calls.incrementAndGet() < 3 ? null : "initial");
        try {
            assertNull(local.get());
            assertNull(local.get());
            assertEquals("initial", local.get());
            assertEquals("initial", local.get());
            assertEquals(3, calls.get());
            try (var outer = local.push("outer")) {
                assertEquals("outer", local.get());
                try (var inner = local.push("inner")) {
                    assertEquals("inner", local.get());
                }
                assertEquals("outer", local.get());
            }
            assertEquals("initial", local.get());
            local.set(null);
            assertEquals("initial", local.get());
            assertEquals(4, calls.get());
        } finally {
            local.remove();
        }
    }

    @Test
    void removalAndNullReadsStayIsolatedOnAReusedWorker() throws Exception {
        var local = new TransientThreadLocal<String>();
        try (var worker = Executors.newSingleThreadExecutor()) {
            local.set("caller");
            worker.submit(() -> {
                assertNull(local.get());
                try (var scope = local.push("worker")) {
                    assertEquals("worker", local.get());
                }
                return null;
            }).get(10, TimeUnit.SECONDS);
            worker.submit(() -> {
                assertNull(local.get());
                assertFalse(currentValues().containsKey(local));
                return null;
            }).get(10, TimeUnit.SECONDS);
            assertEquals("caller", local.get());
        } finally {
            local.remove();
        }
    }

    /** Inspect retention deterministically, without GC timing or a production test-access API. */
    private static Map<?, ?> currentValues() throws ReflectiveOperationException {
        var field = TransientThreadLocal.class.getDeclaredField("TRANSIENT_MAP");
        field.setAccessible(true);
        return (Map<?, ?>) ((ThreadLocal<?>) field.get(null)).get();
    }
}

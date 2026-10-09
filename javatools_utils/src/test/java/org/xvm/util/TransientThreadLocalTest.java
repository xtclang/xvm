package org.xvm.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import java.time.Duration;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransientThreadLocalTest {
    @Test
    void nullReadsDoNotRetainShortLivedLocals() throws Exception {
        assertLocalReleased(false);
    }

    @Test
    void settingNullReleasesTheEntry() throws Exception {
        assertLocalReleased(true);
    }

    /**
     * Keep the worker alive while collecting a local that only that worker has used. Inspecting a
     * private map would couple the test to a field name; collection tests the actual lifetime.
     * The wait is bounded so a regression fails instead of hanging the test process.
     */
    private static void assertLocalReleased(boolean setValueFirst) throws Exception {
        var queue = new ReferenceQueue<TransientThreadLocal<String>>();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var reference = worker.submit(() -> releaseLocal(queue, setValueFirst))
                    .get(10, TimeUnit.SECONDS);
            // A subsequent job ensures the creation frame has returned on the persistent worker.
            assertEquals("still alive", worker.submit(() -> "still alive").get(10, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline) {
                System.gc();
                if (queue.remove(50) == reference) {
                    assertNull(reference.get());
                    return;
                }
            }
            assertNull(reference.get(), "The live worker must not retain an empty local");
        }
    }

    private static WeakReference<TransientThreadLocal<String>> releaseLocal(
            ReferenceQueue<TransientThreadLocal<String>> queue, boolean setValueFirst) {
        var local = new TransientThreadLocal<String>();
        if (setValueFirst) {
            local.set("value");
            assertEquals("value", local.get());
            local.set(null);
        } else {
            assertNull(local.get());
        }
        return new WeakReference<>(local, queue);
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
            assertNull(worker.submit(local::get).get(10, TimeUnit.SECONDS));
            assertEquals("caller", local.get());
        } finally {
            local.remove();
        }
    }
}

package org.xvm.util;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Lazy class.
 */
public class LazyTest {

    @Test
    public void testLazyOf() {
        AtomicInteger counter = new AtomicInteger(0);
        Lazy<String> lazy = Lazy.of(() -> {
            counter.incrementAndGet();
            return "hello";
        });

        assertFalse(lazy.isComputed());
        assertEquals(0, counter.get());

        assertEquals("hello", lazy.get());
        assertTrue(lazy.isComputed());
        assertEquals(1, counter.get());

        // Second call should not recompute
        assertEquals("hello", lazy.get());
        assertEquals(1, counter.get());
    }

    @Test
    public void testLazyOfNull() {
        Lazy<String> lazy = Lazy.of(() -> null);

        assertFalse(lazy.isComputed());
        assertNull(lazy.get());
        assertTrue(lazy.isComputed());

        // Should still return null on subsequent calls
        assertNull(lazy.get());
    }

    @Test
    public void testLazyOfValue() {
        Lazy<String> lazy = Lazy.ofValue("precomputed");

        assertTrue(lazy.isComputed());
        assertEquals("precomputed", lazy.get());
    }

    @Test
    public void testLazyOfValueNull() {
        Lazy<String> lazy = Lazy.ofValue(null);

        assertTrue(lazy.isComputed());
        assertNull(lazy.get());
    }

    @Test
    public void testLazyOfUnsynchronized() {
        AtomicInteger counter = new AtomicInteger(0);
        Lazy<String> lazy = Lazy.ofUnsynchronized(() -> {
            counter.incrementAndGet();
            return "unsync";
        });

        assertFalse(lazy.isComputed());
        assertEquals("unsync", lazy.get());
        assertTrue(lazy.isComputed());
        assertEquals(1, counter.get());

        // Should not recompute
        assertEquals("unsync", lazy.get());
        assertEquals(1, counter.get());
    }

    @Test
    public void testLazyOfNullable() {
        Lazy<Optional<String>> withValue = Lazy.ofNullable(() -> "value");
        assertEquals(Optional.of("value"), withValue.get());

        Lazy<Optional<String>> withNull = Lazy.ofNullable(() -> null);
        assertEquals(Optional.empty(), withNull.get());
    }

    @Test
    public void testLazyOfOptional() {
        Lazy<Optional<String>> lazy = Lazy.ofOptional(() -> Optional.of("test"));
        assertEquals(Optional.of("test"), lazy.get());
    }

    @Test
    public void testOrElse() {
        Lazy<String> lazy = Lazy.of(() -> "computed");

        // Before computation, returns default
        assertEquals("default", lazy.orElse("default"));
        assertFalse(lazy.isComputed());

        // After computation, returns computed value
        lazy.get();
        assertEquals("computed", lazy.orElse("default"));
    }

    @Test
    public void testMap() {
        Lazy<String> lazy = Lazy.of(() -> "hello");
        Lazy<Integer> mapped = lazy.map(String::length);

        assertFalse(mapped.isComputed());
        assertEquals(5, mapped.get());
        assertTrue(mapped.isComputed());
    }

    @Test
    public void testSupplierInterface() {
        Supplier<String> supplier = Lazy.of(() -> "supplier");
        assertEquals("supplier", supplier.get());
    }

    @Test
    public void testThreadSafety() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        Lazy<String> lazy = Lazy.of(() -> {
            counter.incrementAndGet();
            try {
                Thread.sleep(10); // Slow computation
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "result";
        });

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    assertEquals("result", lazy.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Should only compute once despite concurrent access
        assertEquals(1, counter.get());
    }

    @Test
    public void testOfExpiringBasic() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<Integer> expiring = Lazy.ofExpiring(counter::incrementAndGet, 50, TimeUnit.MILLISECONDS);

        assertEquals(1, expiring.get());
        assertEquals(1, expiring.get()); // Should return cached value

        Thread.sleep(60); // Wait for expiration

        assertEquals(2, expiring.get()); // Should recompute
        assertEquals(2, counter.get());
    }

    @Test
    public void testOfExpiringInvalidDuration() {
        assertThrows(IllegalArgumentException.class, () ->
                Lazy.ofExpiring(() -> "test", 0, TimeUnit.MILLISECONDS));
        assertThrows(IllegalArgumentException.class, () ->
                Lazy.ofExpiring(() -> "test", -1, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSynchronizedSupplier() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<Integer> sync = Lazy.synchronizedSupplier(counter::incrementAndGet);

        assertEquals(1, sync.get());
        assertEquals(2, sync.get()); // Not memoized, should increment
        assertEquals(3, sync.get());
    }

    @Test
    public void testNullSupplierThrows() {
        assertThrows(NullPointerException.class, () -> Lazy.of(null));
        assertThrows(NullPointerException.class, () -> Lazy.ofUnsynchronized(null));
        assertThrows(NullPointerException.class, () -> Lazy.ofNullable(null));
        assertThrows(NullPointerException.class, () -> Lazy.ofOptional(null));
        assertThrows(NullPointerException.class, () -> Lazy.ofExpiring(null, 1, TimeUnit.SECONDS));
        assertThrows(NullPointerException.class, () -> Lazy.ofExpiring(() -> "x", 1, null));
        assertThrows(NullPointerException.class, () -> Lazy.synchronizedSupplier(null));
    }
}
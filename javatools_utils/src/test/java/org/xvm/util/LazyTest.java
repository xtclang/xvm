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
    public void testLazyOfBound() {
        Lazy.Bound<OwnerState, String> lazy  = Lazy.ofBound(OwnerState::compute);
        OwnerState                     owner = new OwnerState("owner");

        assertFalse(lazy.isComputed());
        assertEquals("owner:1", lazy.get(owner));
        assertTrue(lazy.isComputed());

        // Second call should not recompute.
        assertEquals("owner:1", lazy.get(owner));
        assertEquals(1, owner.counter.get());
    }

    @Test
    public void testLazyOfBoundNull() {
        Lazy.Bound<OwnerState, String> lazy  = Lazy.ofBound(owner -> null);
        OwnerState                     owner = new OwnerState("owner");

        assertFalse(lazy.isComputed());
        assertNull(lazy.get(owner));
        assertTrue(lazy.isComputed());
        assertNull(lazy.get(owner));
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
    public void testBoundThreadSafety() throws InterruptedException {
        Lazy.Bound<OwnerState, String> lazy  = Lazy.ofBound(OwnerState::compute);
        OwnerState                     owner = new OwnerState("owner");

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    assertEquals("owner:1", lazy.get(owner));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, owner.counter.get());
    }

    @Test
    public void testBoundLazyRejectsDifferentOwnerAfterComputation() {
        Lazy.Bound<OwnerState, String> lazy = Lazy.ofBound(OwnerState::compute);

        assertEquals("one:1", lazy.get(new OwnerState("one")));
        assertThrows(IllegalArgumentException.class, () -> lazy.get(new OwnerState("two")));
    }

    @Test
    public void testBoundReset() {
        var computed = new AtomicInteger();
        class Owner {
            final Lazy.Bound<Owner, Integer> value = Lazy.ofBound(Owner::compute);
            Integer compute() {
                return computed.incrementAndGet();
            }
        }

        var owner = new Owner();
        assertEquals(1, owner.value.get(owner));
        assertEquals(1, owner.value.get(owner));

        owner.value.reset();

        assertEquals(2, owner.value.get(owner), "recomputed after reset");
        assertEquals(2, owner.value.get(owner), "and cached again");
        assertEquals(2, computed.get());
    }

    @Test
    public void testBoundResetKeepsTheOwnerBinding() {
        class Owner {
            final Lazy.Bound<Owner, String> value = Lazy.ofBound(o -> "v");
        }

        var first  = new Owner();
        var second = new Owner();
        first.value.get(first);
        first.value.reset();

        assertThrows(IllegalArgumentException.class, () -> first.value.get(second),
                "a reset must not quietly re-bind the holder to a different owner");
    }

    @Test
    public void testBoundResetBeforeComputing() {
        var computed = new AtomicInteger();
        class Owner {
            final Lazy.Bound<Owner, Integer> value = Lazy.ofBound(o -> computed.incrementAndGet());
        }

        var owner = new Owner();
        owner.value.reset();

        assertEquals(1, owner.value.get(owner));
        assertEquals(1, computed.get(), "nothing was discarded, because nothing was computed");
    }

    @Test
    public void testOfResettable() {
        var computed = new AtomicInteger();
        var lazy     = Lazy.ofResettable(computed::incrementAndGet);

        assertFalse(lazy.isComputed());
        assertEquals(1, lazy.get());
        assertTrue(lazy.isComputed());
        assertEquals(1, lazy.get(), "cached");

        lazy.reset();

        assertFalse(lazy.isComputed(), "reset puts it back to uncomputed");
        assertEquals(2, lazy.get(), "and the next get computes again");
        assertEquals(2, computed.get());
    }

    @Test
    public void testOfResettableResetBeforeComputing() {
        var computed = new AtomicInteger();
        var lazy     = Lazy.ofResettable(computed::incrementAndGet);

        lazy.reset();

        assertEquals(1, lazy.get());
        assertEquals(1, computed.get());
    }

    @Test
    public void testOfResettableNullSupplierThrows() {
        assertThrows(NullPointerException.class, () -> Lazy.ofResettable(null));
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
        assertThrows(NullPointerException.class, () -> Lazy.ofBound(null));
        assertThrows(NullPointerException.class, () -> Lazy.ofUnsynchronized(null));
        assertThrows(NullPointerException.class, () -> Lazy.ofNullable(null));
        assertThrows(NullPointerException.class, () -> Lazy.ofOptional(null));
        assertThrows(NullPointerException.class, () -> Lazy.ofExpiring(null, 1, TimeUnit.SECONDS));
        assertThrows(NullPointerException.class, () -> Lazy.ofExpiring(() -> "x", 1, null));
        assertThrows(NullPointerException.class, () -> Lazy.synchronizedSupplier(null));
    }

    private static class OwnerState {
        private final String name;
        private final AtomicInteger counter = new AtomicInteger();

        OwnerState(String name) {
            this.name = name;
        }

        String compute() {
            return name + ':' + counter.incrementAndGet();
        }
    }
}

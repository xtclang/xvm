package org.xvm.util.converter;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AbstractConverterMap}.
 */
public class AbstractConverterMapTest {
    @Test
    void shouldNotCallOverridableViewFactoriesDuringConstruction() {
        ConstructorSensitiveMap map = new ConstructorSensitiveMap();

        Set<String> keys = map.keySet();
        Collection<String> values = map.values();
        Set<Entry<String, String>> entries = map.entrySet();

        // views are created lazily after construction and then cached in private fields, so
        // repeated accessor calls return the same live view without re-invoking the overridable
        // factories
        assertSame(keys, map.keySet());
        assertSame(values, map.values());
        assertSame(entries, map.entrySet());

        map.put("hello", "world");
        assertTrue(keys.contains("hello"));
        assertTrue(values.contains("world"));
        assertEquals("world", entries.iterator().next().getValue());
    }

    @Test
    void shouldComputeEachViewAtMostOnceUnderConcurrentFirstAccess() throws Exception {
        int threadCount = 16;
        // each caller counts down just before calling keySet(), and the factory cannot finish until
        // all of them have, so every caller reaches keySet() before the view exists
        CountDownLatch arrived = new CountDownLatch(threadCount);
        var map = new FactoryCountingMap(arrived);

        // one thread per caller, so the blocked factory cannot starve the callers it waits for
        List<Callable<Set<String>>> callers = Collections.nCopies(threadCount, () -> {
            arrived.countDown();
            return map.keySet();
        });
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            // every racing thread observed the identical view (compared by identity: distinct empty
            // views would still be equal), and the overridable factory ran at most once: the racy
            // duplicate-view caveat of a plain or volatile cache field does not exist for the
            // compute-at-most-once holder
            for (Future<Set<String>> view : executor.invokeAll(callers)) {
                assertSame(map.keySet(), view.get());
            }
        }
        assertEquals(1, map.keySetFactoryCalls.get());
    }

    /**
     * Identity converter map which counts invocations of the overridable key set factory.
     */
    private static final class FactoryCountingMap
            extends AbstractConverterMap<String, String, String, String> {
        private final AtomicInteger keySetFactoryCalls = new AtomicInteger();
        private final CountDownLatch callersArrived;

        private FactoryCountingMap(CountDownLatch callersArrived) {
            super(new HashMap<>());
            this.callersArrived = callersArrived;
        }

        @Override
        protected String keyDown(String key) {
            return key;
        }

        @Override
        protected String keyUp(String key) {
            return key;
        }

        @Override
        protected String valueDown(String value) {
            return value;
        }

        @Override
        protected String valueUp(String value) {
            return value;
        }

        @Override
        protected Set<String> newKeySet() {
            keySetFactoryCalls.incrementAndGet();
            try {
                callersArrived.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return super.newKeySet();
        }
    }

    /**
     * Fails deterministically on the old implementation, because the base constructor calls the
     * overridden view factories before this class initializes {@link #ready}.
     */
    private static final class ConstructorSensitiveMap
            extends AbstractConverterMap<String, String, String, String> {
        private final String ready;

        private ConstructorSensitiveMap() {
            super(new HashMap<>());
            ready = "ready";
        }

        @Override
        protected String keyDown(String key) {
            return key;
        }

        @Override
        protected String keyUp(String key) {
            return key;
        }

        @Override
        protected String valueDown(String value) {
            return value;
        }

        @Override
        protected String valueUp(String value) {
            return value;
        }

        @Override
        protected Set<String> newKeySet() {
            assertReady();
            return super.newKeySet();
        }

        @Override
        protected Collection<String> newValues() {
            assertReady();
            return super.newValues();
        }

        @Override
        protected Set<Entry<String, String>> newEntrySet() {
            assertReady();
            return super.newEntrySet();
        }

        private void assertReady() {
            if (!"ready".equals(ready)) {
                throw new IllegalStateException("view created before subclass initialization");
            }
        }
    }
}

package org.xvm.util.converter;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    void shouldComputeEachViewAtMostOnceUnderConcurrentFirstAccess() throws InterruptedException {
        var map = new FactoryCountingMap();

        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Set<Set<String>> observedKeyViews = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    observedKeyViews.add(map.keySet());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // every racing thread observed the identical view, and the overridable factory ran at
        // most once: the racy duplicate-view caveat of a plain or volatile cache field does not
        // exist for the compute-at-most-once holder
        assertEquals(1, observedKeyViews.size());
        assertEquals(1, map.keySetFactoryCalls.get());
        assertSame(map.keySet(), observedKeyViews.iterator().next());
    }

    /**
     * Identity converter map which counts invocations of the overridable key set factory.
     */
    private static final class FactoryCountingMap
            extends AbstractConverterMap<String, String, String, String> {
        private final AtomicInteger keySetFactoryCalls = new AtomicInteger();

        private FactoryCountingMap() {
            super(new HashMap<>());
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

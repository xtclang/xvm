package org.xvm.util;

import org.junit.jupiter.api.Test;

import org.xvm.util.concurrent.ConcurrentWeakHasherMap;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WeakHasherMap}
 */
public class WeakHasherMapTest {
    @Test
    void shouldPutAndGet() {
        Map<Integer, String> map = new WeakHasherMap<>(Hasher.natural());
        map.put(1, "hello");
        assertEquals("hello", map.get(1));
        assertNull(map.get(2));
    }

    @Test
    void shouldFindInKeys() {
        Map<Integer, String> map = new WeakHasherMap<>(Hasher.natural());
        map.put(1, "hello");
        assertTrue(map.keySet().contains(1));
        assertFalse(map.keySet().contains(2));
    }

    @Test
    void shouldRemoveFromKeys() {
        Map<Integer, String> map = new WeakHasherMap<>(Hasher.natural());
        map.put(1, "hello");
        assertTrue(map.keySet().remove(1));
        assertFalse(map.keySet().contains(1));
        assertFalse(map.containsKey(1));
        assertNull(map.get(1));
    }

    @Test
    void shouldReturnLiveViews() {
        Map<Integer, String> map = new WeakHasherMap<>(Hasher.natural());

        var keys = map.keySet();
        var values = map.values();
        var entries = map.entrySet();

        // assert only that the views are live/backed by the map; whether an
        // implementation returns cached or fresh view instances is its own business

        map.put(1, "hello");
        assertTrue(keys.contains(1));
        assertTrue(values.contains("hello"));
        assertEquals("hello", entries.iterator().next().getValue());
    }

    @Test
    void souldRetainAll() {
        Map<Integer, String> map = new WeakHasherMap<>(Hasher.natural());
        Map<Integer, String> map2 = new HashMap<>();
        map.put(1, "1");
        map.put(2, "2");
        map.put(3, "3");
        map2.put(2, "2");
        map2.put(4, "4");
        map.keySet().retainAll(map2.keySet());
        assertEquals(1, map.size());
        assertFalse(map.containsKey(1));
        assertTrue(map.containsKey(2));
        assertFalse(map.containsKey(3));
        assertFalse(map.containsKey(4));
    }

    @Test
    void shouldGC() {
        Map<Integer, String> map = new WeakHasherMap<>(Hasher.natural());
        int priorSize = map.size();
        for (int i = 0; map.size() >= priorSize; ++i) {
            priorSize = map.size();
            map.put(i, String.valueOf(i));
        }

        // verify we don't see null keys
        for (Integer n : map.keySet()) {
            assertNotNull(n);
        }

        for (var e : map.entrySet()) {
            assertNotNull(e);
            assertNotNull(e.getKey());
            assertEquals(Integer.toString(e.getKey()), e.getValue());
        }
    }

    @Test
    void shouldLeaveCollectedKeysOutOfArrays() throws InterruptedException {
        var map = new WeakHasherMap<Object, String>(Hasher.identity());
        var set = Collections.newSetFromMap(new ConcurrentWeakHasherMap<Object, Boolean>(Hasher.identity()));
        var kept = new Object();
        map.put(kept, "kept");
        set.add(kept);
        var collected = putCollectable(map, set);
        for (int i = 0; i < 100 && collected.get() != null; ++i) {
            System.gc();
            Thread.sleep(10);
        }
        assertNull(collected.get(), "the key was not collected");

        // a key that was collected, but not yet removed, is left out, as the iterators leave it out
        assertArrayEquals(new Object[] {kept}, map.keySet().toArray());
        assertArrayEquals(new Object[] {kept}, map.keySet().toArray(new Object[0]));
        var entries = map.entrySet().toArray();
        assertEquals(1, entries.length);
        assertSame(kept, ((Map.Entry<?, ?>) entries[0]).getKey());
        assertArrayEquals(new Object[] {kept}, set.toArray(Object[]::new));
    }

    /**
     * Put a key that nothing else refers to into the map and the set.
     *
     * @return a reference that is cleared once the key is collected
     */
    private static WeakReference<Object> putCollectable(Map<Object, String> map, Set<Object> set) {
        var key = new Object();
        map.put(key, "collected");
        set.add(key);
        return new WeakReference<>(key);
    }
}

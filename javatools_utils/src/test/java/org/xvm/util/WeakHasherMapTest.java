package org.xvm.util;

import org.junit.jupiter.api.Test;

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
        assertTrue(map.containsKey(1));
        assertFalse(map.containsKey(2));
    }

    @Test
    void shouldRemoveFromKeys() {
        Map<Integer, String> map = new WeakHasherMap<>(Hasher.natural());
        map.put(1, "hello");
        //noinspection RedundantCollectionOperation
        assertTrue(map.keySet().remove(1));
        assertFalse(map.containsKey(1));
        assertFalse(map.containsKey(1));
        assertNull(map.get(1));
    }

    @Test
    void shouldRetainAll() {
        var map = new WeakHasherMap<Integer, String>(Hasher.natural()) {{
            put(1, "1");
            put(2, "2");
            put(3, "3");
        }};
        map.keySet().retainAll(Set.of(2));
        assertAll(
                () -> assertEquals(1, map.size()),
                () -> assertTrue(map.containsKey(2)),
                () -> assertFalse(map.containsKey(1)),
                () -> assertFalse(map.containsKey(3)),
                () -> assertFalse(map.containsKey(4))
        );
    }

    @Test
    void shouldGC() {
        final var map = new WeakHasherMap<Integer, String>(Hasher.natural());
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
}

package org.xvm.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WeakHasherMap}
 */
public class WeakHasherMapTest
    {
    @Test
    void shouldPutAndGet()
        {
        Map<Integer, String> map = new WeakHasherMap<>(Hasher.natural());
        map.put(1, "hello");
        assertEquals("hello", map.get(1));
        assertNull(map.get(2));
        }

    @Test
    void shouldFindInKeys()
        {
        Map<Integer, String> map = new WeakHasherMap<>(Hasher.natural());
        map.put(1, "hello");
        assertTrue(map.keySet().contains(1));
        assertFalse(map.keySet().contains(2));
        }

    @Test
    void shouldRemoveFromKeys()
        {
        Map<Integer, String> map = new WeakHasherMap<>(Hasher.natural());
        map.put(1, "hello");
        assertTrue(map.keySet().remove(1));
        assertFalse(map.keySet().contains(1));
        assertFalse(map.containsKey(1));
        assertNull(map.get(1));
        }

    @Test
    void shouldGC()
        {
        Map<Integer, String> map = new WeakHasherMap<>(Hasher.natural());
        int priorSize = map.size();
        for (int i = 0; map.size() >= priorSize; ++i)
            {
            priorSize = map.size();
            map.put(i, String.valueOf(i));
            }

        // verify we don't see null keys
        for (Integer n : map.keySet())
            {
            assertNotNull(n);
            }

        for (var e : map.entrySet())
            {
            assertNotNull(e);
            assertNotNull(e.getKey());
            assertEquals(Integer.toString(e.getKey()), e.getValue());
            }
        }
    }

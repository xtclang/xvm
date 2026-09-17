package org.xvm.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ListMap}.
 */
public class ListMapTest {
    // ----- equality ------------------------------------------------------------------------------

    /**
     * ListMap declares no equals/hashCode, so it inherits AbstractMap's, which compare key to value
     * pairs through entrySet() rather than comparing the backing list. Equality is therefore
     * independent of how the entries are stored, and of their identity.
     */
    @Test
    public void equalityComparesContentNotTheBackingList() {
        var first = new ListMap<String, Integer>();
        first.put("a", 1);
        first.put("b", 2);

        var second = new ListMap<String, Integer>();
        second.put("a", 1);
        second.put("b", 2);

        assertEquals(first, second, "separately built maps with equal content must be equal");
        assertEquals(first.hashCode(), second.hashCode(), "equal maps must agree on hashCode");
    }

    /**
     * Equality is with any Map of the same content, not just another ListMap, and it does not depend
     * on insertion order - that is AbstractMap's contract and what a ListMap-keyed cache relies on.
     */
    @Test
    public void equalsAnyMapWithTheSameContent() {
        var map = new ListMap<String, Integer>();
        map.put("a", 1);
        map.put("b", 2);

        assertEquals(map, new HashMap<>(Map.of("a", 1, "b", 2)));
        assertEquals(map.hashCode(), new HashMap<>(Map.of("a", 1, "b", 2)).hashCode());

        var reversed = new LinkedHashMap<String, Integer>();
        reversed.put("b", 2);
        reversed.put("a", 1);
        assertEquals(map, reversed, "Map equality ignores order even though ListMap preserves it");
    }

    /**
     * A copy must be equal to its original, which is what keeps a copied map usable as a key.
     */
    @Test
    public void copyEqualsTheOriginal() {
        var original = new ListMap<String, Integer>();
        original.put("a", 1);
        original.put("b", 2);

        var copy = new ListMap<>(original);

        assertEquals(original, copy, "a copy must equal its original");
        assertEquals(original.hashCode(), copy.hashCode(), "a copy must hash like its original");
    }

    // ----- ordering and basic map behaviour -------------------------------------------------------

    @Test
    public void preservesInsertionOrder() {
        var map = new ListMap<String, Integer>();
        map.put("c", 3);
        map.put("a", 1);
        map.put("b", 2);

        assertEquals(List.of("c", "a", "b"), new ArrayList<>(map.keySet()));
        assertEquals("c", map.entryAt(0).getKey());
        assertEquals("b", map.entryAt(2).getKey());
    }

    @Test
    public void putReplacesRatherThanAppendingForAKnownKey() {
        var map = new ListMap<String, Integer>();
        assertNull(map.put("a", 1), "putting a new key returns no previous value");
        assertEquals(1, map.put("a", 2), "putting a known key returns the previous value");

        assertEquals(1, map.size(), "replacing must not append a second entry");
        assertEquals(2, map.get("a"));
    }

    @Test
    public void getMissingKeyIsNull() {
        var map = new ListMap<String, Integer>();
        map.put("a", 1);

        assertNull(map.get("b"));
    }

    // ----- copy isolation ------------------------------------------------------------------------

    /**
     * The copy constructor must isolate: writing an existing key in a copy goes through
     * Entry.setValue(), so sharing the entry objects would write it in the original too.
     */
    @Test
    public void writingAnExistingKeyInACopyLeavesTheOriginalAlone() {
        var original = new ListMap<String, String>();
        original.put("k", "original");

        var copy = new ListMap<>(original);
        copy.put("k", "changed");

        assertEquals("changed", copy.get("k"), "the copy must see its own write");
        assertEquals("original", original.get("k"), "the original must not see the copy's write");
    }

    @Test
    public void addingToACopyLeavesTheOriginalAlone() {
        var original = new ListMap<String, String>();
        original.put("a", "1");

        var copy = new ListMap<>(original);
        copy.put("b", "2");

        assertEquals(1, original.size(), "the original must not gain the copy's new key");
        assertEquals(2, copy.size());
    }

    @Test
    public void copyDoesNotShareEntryObjects() {
        var original = new ListMap<String, String>();
        original.put("k", "v");

        var copy = new ListMap<>(original);

        assertNotSame(original.entryAt(0), copy.entryAt(0),
                "the copy must hold its own entry objects, since Entry is mutable");
    }

    // ----- the empty map -------------------------------------------------------------------------

    @Test
    public void emptyIsSharedAndRejectsWrites() {
        ListMap<String, Integer> empty = ListMap.empty();

        assertTrue(empty.isEmpty());
        assertSame(empty, ListMap.<String, Integer>empty(), "empty() must hand out one instance");
        assertThrows(UnsupportedOperationException.class, () -> empty.put("a", 1));
    }

    @Test
    public void copyingTheEmptyMapYieldsAWritableMap() {
        var copy = new ListMap<>(ListMap.<String, Integer>empty());
        copy.put("a", 1);

        assertEquals(1, copy.size());
        assertTrue(ListMap.empty().isEmpty(), "the shared empty map must stay empty");
    }

    // ----- asList --------------------------------------------------------------------------------

    @Test
    public void asListExposesTheEntriesInOrder() {
        var map = new ListMap<String, Integer>();
        map.put("a", 1);
        map.put("b", 2);

        List<Map.Entry<String, Integer>> list = map.asList();

        assertEquals(2, list.size());
        assertEquals("a", list.getFirst().getKey());
        assertEquals("b", list.get(1).getKey());
    }

    /**
     * asList() is documented read-only, and must be so regardless of whether assertions are enabled.
     */
    @Test
    public void asListIsReadOnly() {
        var map = new ListMap<String, Integer>();
        map.put("a", 1);

        List<Map.Entry<String, Integer>> list = map.asList();

        assertThrows(UnsupportedOperationException.class, list::clear);
    }
}

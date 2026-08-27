package org.xvm.util;


import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * A simple implementation of a Map on top of an ArrayList to maintain order of
 * insertion. This map should work well for small numbers of entries, but will
 * degrade in performance as it grows in size.
 *
 * <p><b>WARNING - LOAD-BEARING IN TYPE COMPUTATION.</b> This class is not just a small-map
 * convenience: it is the <b>insertion-ORDERED</b> map the XVM type system and {@code ConstantPool}
 * resolution depend on for <b>type parameters</b> (order is significant -
 * {@code Array<Int,String> != Array<String,Int>}). Callers and the type system rely on that
 * ordering when they iterate, bind, register, and resolve parameterized types. <b>Do not swap a
 * {@code ListMap} for a {@code HashMap} or a plain {@code Map} anywhere it is stored or iterated for
 * type identity, and do not assume the order can be dropped - doing so breaks type resolution in
 * subtle, wide-ranging ways.</b> It uses {@link AbstractMap}'s standard (order-insensitive,
 * any-{@code Map}) {@code equals}/{@code hashCode}, so narrowing a getter's RETURN to an immutable
 * order-preserving VIEW is safe; changing the stored representation is not. See
 * {@code docs/reentrancy/listmap-issues.md}.</p>
 */
public class ListMap<K,V>
        extends AbstractMap<K,V> {
    /**
     * Construct a new ListMap.
     */
    public ListMap() {
        m_list = new ArrayList<>();
    }

    /**
     * Construct a new ListMap of the specified initial capacity.
     *
     * @param cInitSize  the initial capacity; negative value indicates an immutable empty map
     */
    public ListMap(int cInitSize) {
        m_list = cInitSize >= 0
            ? new ArrayList<>(cInitSize)
            : (ArrayList<SimpleEntry<K,V>>) EMPTY_ARRAY_LIST;
    }

    /**
     * Construct a new ListMap of the same content as the specified ListMap.
     *
     * @param map  the map to clone
     */
    public ListMap(ListMap<K, V> map) {
        m_list = new ArrayList<>(map.m_list);
    }

    /**
     * Construct a ListMap with the contents of another map, preserving that map's iteration
     * order. The keys are assumed unique in the source, so entries are appended directly.
     */
    public ListMap(Map<K, V> map) {
        m_list = new ArrayList<>(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            m_list.add(new SimpleEntry<>(entry.getKey(), entry.getValue()));
        }
    }

    @Override
    public V put(K key, V value) {
        Entry<K,V> entry = getEntry(key);
        if (entry != null) {
            return entry.setValue(value);
        }

        if (m_list == EMPTY_ARRAY_LIST) {
            throw new UnsupportedOperationException();
        }

        m_list.add(new SimpleEntry<>(key, value));
        return null;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return m_setEntries;
    }

    /**
     * Obtain a read-only list of entries.
     *
     * @return the entries of the map in a List
     */
    public List<Entry<K,V>> asList() {
        // unconditional wrapper: the read-only contract must not evaporate under -da; the raw
        // backing list would let a caller mutate the map's entry storage directly
        return Collections.unmodifiableList((List) m_list);
    }

    /**
     * Obtain an entry at the specified index.
     *
     * @param index  the entry index
     *
     * @return an entry
     */
    public Entry<K,V> entryAt(int index) {
        return m_list.get(index);
    }

    /**
     * Internal: Obtain the entry that corresponds to the specified key.
     *
     * @param key  the key
     *
     * @return the entry if it exists; otherwise null
     */
    protected SimpleEntry<K,V> getEntry(Object key) {
        ArrayList<SimpleEntry<K,V>> list = m_list;
        for (int i = 0, c = list.size(); i < c; ++i) { // avoid Iterator creation
            SimpleEntry<K, V> entry = list.get(i);
            if (entry.getKey().equals(key)) {
                return entry;
            }
        }

        return null;
    }

    @Override
    public V get(Object key) {
        SimpleEntry<K, V> entry = getEntry(key);
        return entry == null ? null : entry.getValue();
    }

    /**
     * The contents of the map are stored in an ArrayList of SimpleEntry
     * objects.
     */
    private final ArrayList<SimpleEntry<K, V>> m_list;

    /**
     * The AbstractMap implementation needs an underlying "entry set" to be
     * provided; this is that set, but just sitting on top of {@link #m_list}.
     */
    private final Set<Entry<K, V>> m_setEntries = new AbstractSet<>() {
        @Override
        public Iterator<Entry<K, V>> iterator() {
            return (Iterator) m_list.iterator();
        }

        @Override
        public int size() {
            return m_list.size();
        }
    };

    /**
     * An empty ArrayList.
     */
    private static final ArrayList<?> EMPTY_ARRAY_LIST = new ArrayList<>(0);

    /**
     * An empty ListMap.
     */
    public static final ListMap EMPTY = new ListMap<>(-1);
}

package org.xvm.util;


import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


/**
 * A simple implementation of a Map on top of an ArrayList to maintain order of
 * insertion. This map should work well for small numbers of entries, but will
 * degrade in performance as it grows in size.
 */
public class ListMap<K, V>
        extends AbstractMap<K, V> {
    /**
     * Construct a new ListMap.
     */
    public ListMap() {
        m_list      = new ArrayList<>();
        m_immutable = false;
    }

    /**
     * Construct a new ListMap of the specified initial capacity.
     *
     * @param cInitSize  the initial capacity
     */
    public ListMap(int cInitSize) {
        m_list      = new ArrayList<>(cInitSize);
        m_immutable = false;
    }

    /**
     * Construct a new ListMap of the same content as the specified ListMap.
     *
     * @param map  the map to clone
     */
    public ListMap(ListMap<K, V> map) {
        m_list      = new ArrayList<>(map.m_list);
        m_immutable = false;
    }

    /**
     * Private constructor for the immutable empty singleton.
     */
    private ListMap(boolean immutable) {
        m_list      = new ArrayList<>(0);
        m_immutable = immutable;
    }

    @Override
    public int size() {
        return m_list.size();
    }

    @Override
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    @Override
    public V get(Object key) {
        Entry<K, V> entry = getEntry(key);
        return entry == null ? null : entry.getValue();
    }

    @Override
    public V put(K key, V value) {
        Entry<K, V> entry = getEntry(key);
        if (entry != null) {
            return entry.setValue(value);
        }

        if (m_immutable) {
            throw new UnsupportedOperationException();
        }

        m_list.add(new SimpleEntry<>(key, value));
        return null;
    }

    @Override
    public V remove(Object key) {
        ArrayList<Entry<K, V>> list = m_list;
        for (int i = 0, c = list.size(); i < c; ++i) {
            if (list.get(i).getKey().equals(key)) {
                return list.remove(i).getValue();
            }
        }
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
    public List<Entry<K, V>> asList() {
        return Collections.unmodifiableList(m_list);
    }

    /**
     * Obtain the entry that corresponds to the specified key.
     *
     * @param key  the key
     *
     * @return the entry if it exists; otherwise null
     */
    private Entry<K, V> getEntry(Object key) {
        ArrayList<Entry<K, V>> list = m_list;
        for (int i = 0, c = list.size(); i < c; ++i) {
            Entry<K, V> entry = list.get(i);
            if (entry.getKey().equals(key)) {
                return entry;
            }
        }

        return null;
    }

    /**
     * The contents of the map are stored in an ArrayList of Entry objects.
     */
    private final ArrayList<Entry<K, V>> m_list;

    /**
     * True if this map is immutable.
     */
    private final boolean m_immutable;

    /**
     * The AbstractMap implementation needs an underlying "entry set" to be
     * provided; this is that set, but just sitting on top of {@link #m_list}.
     */
    private final Set<Entry<K, V>> m_setEntries = new AbstractSet<>() {
        @Override
        public Iterator<Entry<K, V>> iterator() {
            return m_list.iterator();
        }

        @Override
        public int size() {
            return m_list.size();
        }
    };

    /**
     * An immutable empty ListMap singleton.
     */
    @SuppressWarnings("rawtypes")
    private static final ListMap EMPTY = new ListMap<>(true);

    /**
     * @return a typed empty ListMap
     */
    @SuppressWarnings("unchecked")
    public static <K, V> ListMap<K, V> empty() {
        return EMPTY;
    }
}

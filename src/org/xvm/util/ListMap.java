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
 *
 * @author cp 2016.10.28
 */
public class ListMap<K,V>
        extends AbstractMap<K,V>
    {
    @Override
    public V put(K key, V value)
        {
        Entry<K,V> entry = getEntry(key);
        if (entry != null)
            {
            return entry.setValue(value);
            }

        m_list.add(new SimpleEntry<K, V>(key, value));
        return null;
        }

    @Override
    public Set<Entry<K, V>> entrySet()
        {
        return m_setEntries;
        }

    /**
     * Obtain a read-only list of entries.
     *
     * @return the entries of the map in a List
     */
    public List<Entry<K,V>> asList()
        {
        return Collections.unmodifiableList(m_list);
        }

    /**
     * Internal: Obtain the entry that corresponds to the specified key.
     *
     * @param key  the key
     *
     * @return the entry if it exists; otherwise null
     */
    protected Entry<K,V> getEntry(K key)
        {
        for (Entry<K,V> entry : m_list)
            {
            if (Handy.equals(key, entry.getKey()))
                {
                return entry;
                }
            }

        return null;
        }

    /**
     * The contents of the map are stored in an ArrayList of SimpleEntry
     * objects.
     */
    private final ArrayList<Entry<K, V>> m_list = new ArrayList<>();

    /**
     * The AbstractMap implementation needs an underlying "entry set" to be
     * provided; this is that set, but just sitting on top of {@link #m_list}.
     */
    private final Set<Entry<K, V>> m_setEntries = new AbstractSet<Entry<K, V>>()
        {
        @Override
        public Iterator<Entry<K, V>> iterator()
            {
            return m_list.iterator();
            }

        @Override
        public int size()
            {
            return m_list.size();
            }
        };
    }

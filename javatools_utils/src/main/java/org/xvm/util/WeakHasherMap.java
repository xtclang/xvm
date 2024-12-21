package org.xvm.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * A {@link Hasher} based {@link Map} which only maintains {@link WeakReference}s to its keys, allowing those entries to
 * be automatically removed their keys become unreachable.
 *
 * @author falcom
 */
public class WeakHasherMap<K, V> extends HasherMap<K, V>
    {
    /**
     * The minimum GC interval.
     */
    private static final int MIN_GC_INTERVAL = Integer.highestOneBit(Runtime.getRuntime().availableProcessors());

    /**
     * Queue of cleared keys.
     */
    final ReferenceQueue<K> garbage = new ReferenceQueue<>();

    /**
     * The interval for full gc.
     */
    int fullGcInterval = MIN_GC_INTERVAL;

    /**
     * Construct a {@link HasherMap} with a given {@link Hasher}.
     *
     * @param hasher the {@link Hasher} to use in determining key equality
     */
    public WeakHasherMap(Hasher<K> hasher)
        {
        super(hasher);
        }

    /**
     * Construct a {@link HasherMap} with a given {@link Hasher} and initial capacity.
     *
     * @param hasher  the {@link Hasher} to use in determining key equality
     * @param storage the non-week backing store allocator
     */
    public WeakHasherMap(Hasher<K> hasher, Supplier<? extends Map<Supplier<K>, V>> storage)
        {
        super(hasher, storage);
        }

    @Override
    protected Supplier<K> keyDown(K key)
        {
        return new WeakHasherReference<>(key, hasher, garbage);
        }

    /**
     * Remove any cleared refs from the map.
     *
     * @param storage the storage map to clean
     * @param compact {@code true} to allow storage compaction
     * @param full    to do a more exhaustive search of the keys
     * @return storage
     */
    protected Map<Supplier<K>, V> gc(Map<Supplier<K>, V> storage, boolean compact, boolean full)
        {
        if (full)
            {
            int c = 0;
            for (Iterator<Supplier<K>> iter = storage.keySet().iterator(); iter.hasNext(); )
                {
                Supplier<K> ref = iter.next();
                if (ref.get() == null)
                    {
                    if (compact)
                        {
                        iter.remove();
                        }
                    else
                        {
                        ((WeakReference<?>) ref).enqueue();
                        ++c; // storage map size didn't change
                        }
                    }
                else
                    {
                    ++c;
                    }
                }
            fullGcInterval = Integer.highestOneBit(Math.max(MIN_GC_INTERVAL, c + c / 2));
            }

        if (compact)
            {
            Reference<? extends K> key;
            while ((key = garbage.poll()) != null)
                {
                storage.remove((WeakHasherReference<?>) key);
                }
            }

        return storage;
        }

    @Override
    protected Map<Supplier<K>, V> read()
        {
        Map<Supplier<K>, V> storage = super.read();
        // periodic full GC
        return ThreadLocalRandom.current().nextInt(fullGcInterval) == 0
                ? gc(storage, storage instanceof ConcurrentMap, true)
                : storage;
        }

    @Override
    protected Map<Supplier<K>, V> write()
        {
        // minor GC on every write; periodic full
        Map<Supplier<K>, V> storage = super.write();
        return gc(storage, ThreadLocalRandom.current().nextInt(MIN_GC_INTERVAL) == 0,
                ThreadLocalRandom.current().nextInt(fullGcInterval) == 0);
        }

    @Override
    protected Set<K> newKeySet()
        {
        return new KeySet()
            {
            @Override
            public Iterator<K> iterator()
                {
                return new FilterIterator<>(super.iterator(), Objects::nonNull);
                }
            };
        }

    @Override
    protected Set<Entry<K, V>> newEntrySet()
        {
        return new EntrySet()
            {
            @Override
            public Iterator<Entry<K, V>> iterator()
                {
                return new FilterIterator<>(super.iterator(), e -> e.getKey() != null);
                }
            };
        }
    }
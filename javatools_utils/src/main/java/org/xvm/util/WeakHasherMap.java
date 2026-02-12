package org.xvm.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * A {@link Hasher} based {@link Map} which only maintains {@link WeakReference}s to its keys, allowing those entries to
 * be automatically removed their keys become unreachable.
 */
public class WeakHasherMap<K, V> extends HasherMap<K, V> {
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
    public WeakHasherMap(Hasher<K> hasher) {
        super(hasher);
        this.keys    = new KeySet();
        this.entries = new EntrySet();
    }

    /**
     * Construct a {@link HasherMap} with a given {@link Hasher} and initial capacity.
     *
     * @param hasher  the {@link Hasher} to use in determining key equality
     * @param storage the non-week backing store allocator
     */
    public WeakHasherMap(Hasher<K> hasher, Supplier<? extends Map<Supplier<K>, V>> storage) {
        super(hasher, storage);
        this.keys    = new KeySet();
        this.entries = new EntrySet();
    }

    @Override
    protected Supplier<K> keyDown(K key) {
        return new WeakHasherReference<>(key, hasher, garbage);
    }

    @Override
    protected Map<Supplier<K>, V> read() {
        Map<Supplier<K>, V> storage = super.read();
        return ThreadLocalRandom.current().nextInt(fullGcInterval) == 0
                ? gc(storage, true, storage instanceof ConcurrentMap)
                : storage;
    }

    @Override
    protected Map<Supplier<K>, V> write() {
        Map<Supplier<K>, V> storage = super.write();
        int rand = ThreadLocalRandom.current().nextInt();
        return gc(storage, (rand & (fullGcInterval - 1)) == 0, (rand & (MIN_GC_INTERVAL - 1)) == 0);
    }

    /**
     * Remove any cleared refs from the map.
     *
     * @param storage the storage map to clean
     * @param mark    to do a more exhaustive search of the keys
     * @param sweep   {@code true} to allow storage compaction (requires write safety)
     * @return storage
     */
    protected Map<Supplier<K>, V> gc(Map<Supplier<K>, V> storage, boolean mark, boolean sweep) {
        if (mark) {
            int c = 0;
            for (Iterator<Supplier<K>> iter = storage.keySet().iterator(); iter.hasNext(); ) {
                Supplier<K> ref = iter.next();
                if (ref.get() == null) {
                    if (sweep) {
                        iter.remove();
                        continue;
                    } else {
                        ((WeakHasherReference<?>) ref).enqueue();
                    }
                }
                ++c;
            }

            // schedule full GCs at rate relative to the size such that we maintain amortized O(1)
            fullGcInterval = Integer.highestOneBit(Math.max(MIN_GC_INTERVAL, c + c / 2));
        }

        if (sweep) {
            Reference<? extends K> key;
            while ((key = garbage.poll()) != null) {
                storage.remove((WeakHasherReference<?>) key);
            }
        }

        return storage;
    }

    /**
     * KeySet which filters out cleared keys during iteration.
     */
    protected class KeySet extends HasherMap<K, V>.KeySet {
        @Override
        public Iterator<K> iterator() {
            return new FilterIterator<>(super.iterator(), Objects::nonNull);
        }
    }

    /**
     * EntrySet which filters out cleared keys during iteration.
     */
    protected class EntrySet extends HasherMap<K, V>.EntrySet {
        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new FilterIterator<>(super.iterator(), e -> e.getKey() != null);
        }
    }
}
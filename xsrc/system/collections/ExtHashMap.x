/**
 * ExtHashMap is the base implementation of a hashed key-to-value data structure. Unlike HashMap,
 * (which despite the name, actually extends ExtHashMap), the ExtHashMap does not require keys to
 * be immutable, nor do the keys need to know how to hash themselves or compare themselves to each
 * other. This is possible because ExtHashMap delegates to an _Ext_ernal {@link Hasher}, hence
 * providing the rationale for the name of the _Ext_HashMap.
 */
class ExtHashMap<KeyType, ValueType>
        implements Map<KeyType, ValueType>
    {
    typedef ExtEntry<KeyType, ValueType> HashEntry;

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the ExtHashMap with the specified hasher and (optional) initial capacity.
     *
     * @param hasher
     * @param initCapacity  the number of expected entries
     */
    construct(Hasher<KeyType> hasher, Int initCapacity = 0)
        {
        this.hasher = hasher;

        // allocate the initial capacity
        (Int bucketCount, this.growAt) = calcBucketCount(initCapacity);
        buckets = new HashEntry?[bucketCount];
        }

    // ----- internal state ------------------------------------------------------------------------

    /**
     * The Hasher is the thing that knows how to take a key and provide a hash code, or compare two
     * keys for equality -- even if the keys don't know how to do that themselves.
     */
    public/private Hasher<KeyType> hasher;

    /**
     * An array of hash buckets.
     */
    private HashEntry?[] buckets;

    /**
     * The size at which the capacity must grow.
     */
    private Int growAt;

    /**
     * The size at which the capacity must shrink.
     */
    private Int shrinkAt = -1;

    /**
     * The number of entries added.
     */
    private Int addCount = 0;

    /**
     * The number of entries removed.
     */
    private Int removeCount = 0;

    // ----- Map interface -------------------------------------------------------------------------

    /**
     * The size of the map is maintained internally.
     */
    @Override
    Int size.get()
        {
        return addCount - removeCount;
        }

    @Override
    conditional HashEntry getEntry(KeyType key)
        {
        Int        keyhash  = hasher.hashOf(key);
        Int        bucketId = keyhash % buckets.size;
        HashEntry? entry    = buckets[bucketId];
        while (entry != null)
            {
            if (entry.keyhash == keyhash && hasher.areEqual(entry.key, key))
                {
                return true, entry;
                }
            entry = entry.next;
            }
        return false;
        }

    @Override
    ExtHashMap<KeyType, ValueType> put(KeyType key, ValueType value)
        {
        Int        keyhash  = hasher.hashOf(key);
        Int        bucketId = keyhash % buckets.size;
        HashEntry? entry    = buckets[bucketId];
        while (entry != null)
            {
            if (entry.keyhash == keyhash && hasher.areEqual(entry.key, key))
                {
                entry.value = value;
                return this;
                }
            entry = entry.next;
            }

        buckets[bucketId] = new HashEntry(key, keyhash, value, buckets[bucketId]);
        ++addCount;
        checkCapacity();
        return this;
        }

    @Override
    Map<KeyType, ValueType> putAll(Map<KeyType, ValueType> that)
        {
        // check the capacity up front (to avoid multiple resizes); the worst case is that we end
        // up a bit bigger than we want
        checkCapacity(size + that.size);

        HashEntry?[] buckets     = this.buckets;
        Int          bucketCount = buckets.size;
        NextPut: for (Entry<KeyType, ValueType> entry : that.entries)
            {
            KeyType    key       = entry.key;
            Int        keyhash   = hasher.hashOf(key);
            Int        bucketId  = keyhash % bucketCount;
            HashEntry? currEntry = buckets[bucketId];
            while (currEntry != null)
                {
                if (currEntry.keyhash == keyhash && hasher.areEqual(currEntry.key, key))
                    {
                    // an entry with the same key already exists in the ExtHashMap
                    currEntry.value = entry.value;
                    continue NextPut;
                    }
                currEntry = currEntry.next;
                }
            // no such entry with the same key in the ExtHashMap; create a new HashEntry
            buckets[bucketId] = new HashEntry(key, keyhash, entry.value, buckets[bucketId]);
            ++addCount;
            }

        return this;
        }

    @Override
    ExtHashMap<KeyType, ValueType> remove(KeyType key)
        {
        Int        keyhash   = hasher.hashOf(key);
        Int        bucketId  = keyhash % buckets.size;
        HashEntry? entry     = buckets[bucketId];
        HashEntry? prevEntry = null;
        while (entry != null)
            {
            if (entry.keyhash == keyhash && hasher.areEqual(entry.key, key))
                {
                // unlink the entry
                if (prevEntry != null)
                    {
                    prevEntry.next = entry.next;
                    }
                else
                    {
                    buckets[bucketId] = entry.next;
                    }
                entry.next = null;

                ++removeCount;
                return this;
                }

            prevEntry = entry;
            entry = entry.next;
            }
        return this;
        }

    @Override
    ExtHashMap<KeyType, ValueType> clear()
        {
        Int entryCount = size;
        if (entryCount > 0)
            {
            (Int bucketCount, this.growAt, this.shrinkAt) = calcBucketCount(0);
            buckets = new HashEntry?[bucketCount];
            removeCount += entryCount;
            assert size == 0;
            }
        return this;
        }

    @Override
    @Lazy public/private Set<KeyType> keys.calc()
        {
        return new EntryBasedKeySet();
        }

    @Override
    public/private HashEntrySet entries = new HashEntrySet();

    @Override
    @Lazy public/private Collection<ValueType> values.calc()
        {
        return new EntryBasedValuesCollection<ValueType>();
        }

    @Override
    <ResultType> ResultType process(KeyType key,
            function ResultType (ProcessableEntry<KeyType, ValueType>) compute)
        {
        return compute(new ProcessableHashEntry(key));
        }

    // ----- HashEntry implementation --------------------------------------------------------------

    /**
     * A representation of all of the HashEntry objects in the Map.
     */
    class HashEntrySet
            extends KeyBasedEntrySet<KeyType, ValueType>
        {
        @Override
        Iterator<HashEntry> iterator()
            {
            return new Iterator<HashEntry>()
                {
                HashEntry?[] buckets     = ExtHashMap.this.buckets;
                Int          nextBucket  = 0;
                HashEntry?   nextEntry   = null;
                Int          addSnapshot = ExtHashMap.this.addCount;

                conditional KeyType next()  // TODO is the return type "EntryType" or "KeyType"???
                    {
                    if (addSnapshot != ExtHashMap.this.addCount)
                        {
                        throw new ConcurrentModificationException();
                        }

                    Int bucketCount = buckets.size;
                    while (nextEntry == null && nextBucket < bucketCount)
                        {
                        nextEntry = buckets[nextBucket++];
                        }

                    if (nextEntry != null)
                        {
                        // this is the entry to return
                        HashEntry entry = nextEntry;

                        // always load next one in the chain to avoid losing the position if/when
                        // the current entry is removed
                        nextEntry = entry.next;

                        return true, entry;
                        }

                    return false;
                    }
                };
            }

        // @Override
        HashEntrySet remove(HashEntry entry)
            {
            HashEntry?[] buckets   = ExtHashMap.this.buckets;
            Int          keyhash   = entry.keyhash;
            Int          bucketId  = keyhash % buckets.size;
            HashEntry?   currEntry = buckets[bucketId];
            HashEntry?   prevEntry = null;

          loop:
            while (currEntry != null)
                {
                // check if we found the entry that we're looking for
                if (currEntry.keyhash == keyhash && hasher.areEqual(currEntry.key, entry.key))
                    {
                    // verify that it is the same entry (i.e. values also have to match!)
                    if (&currEntry == &entry || currEntry.value == entry.value)
                        {
                        // unlink the entry that is being removed
                        if (prevEntry != null)
                            {
                            prevEntry.next = currEntry.next;
                            }
                        else
                            {
                            buckets[loop.counter] = currEntry.next;
                            }

                        ++ExtHashMap.this.removeCount;
                        }

                    // whether or not the entry matched and was removed, it was the one that we
                    // were looking for (i.e. there will be no other entry with that same key)
                    return this;
                    }

                prevEntry = currEntry;
                currEntry = currEntry.next;
                }

            return this;
            }

        // @Override
        conditional HashEntrySet removeIf(function Boolean (HashEntry) shouldRemove)
            {
            Boolean      modified    = false;
            HashEntry?[] buckets     = ExtHashMap.this.buckets;
            Int          bucketCount = buckets.size;
            for (Int i = 0; i < bucketCount; ++i)
                {
                HashEntry? entry     = buckets[i];
                HashEntry? prevEntry = null;
                while (entry != null)
                    {
                    if (shouldRemove(entry))
                        {
                        // move to the next entry (the current one is getting unlinked)
                        entry = entry.next;

                        // unlink the entry that is being removed
                        if (prevEntry != null)
                            {
                            prevEntry.next = entry;
                            }
                        else
                            {
                            buckets[i] = entry;
                            }
                        modified = true;
                        ++ExtHashMap.this.removeCount;
                        }
                    else
                        {
                        prevEntry = entry;
                        entry = entry.next;
                        }
                    }
                }

            return modified ? (true, this) : false;
            }
        }

    // ----- HashEntry implementation --------------------------------------------------------------

    /**
     * This is the Entry implementation used to store the ExtHashMap's keys and values.
     */
    protected static class ExtEntry<KeyType, ValueType>
            implements Entry<KeyType, ValueType>
        {
        construct(KeyType key, Int keyhash, ValueType value, ExtEntry<KeyType, ValueType>? next = null)
            {
            this.key     = key;
            this.keyhash = keyhash;
            this.value   = value;
            this.next    = next;
            }

        @Override
        public/private KeyType key;

        /**
         * The cached hash-code for the key.
         */
        public/private Int keyhash;

        @Override
        public ValueType value;

        /**
         * HashedEntry objects form a linked list within a hash bucket.
         */
        private ExtEntry<KeyType, ValueType>? next;
        }

    /**
     * This is an implementation of the ProcessableEntry interface that is implemented by delegation
     * to the actual map (i.e. it is not a real HashEntry), but it reifies to a real HashEntry.
     */
    protected class ProcessableHashEntry
            extends Map.KeyBasedEntry<KeyType, ValueType>
        {
        construct(KeyType key)
            {
            construct Map.KeyBasedEntry(key);
            }

        @Override
        HashEntry reify()
            {
            if (HashEntry entry : ExtHashMap.this.getEntry(key))
                {
                return entry;
                }

            // the real entry does not actually exist
            throw new BoundsException();
            }
        }

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Check to see if the ExtHashMap needs to grow or shrink based on the current capacity need.
     */
    private void checkCapacity()
        {
        Int size = this.size;
        if (size < shrinkAt)
            {
            // we've lost a lot of weight; shrink the number of buckets
            resize(size);
            }
        else if (size > growAt)
            {
            // we're growing, so make some extra room (assume we need at least double the capacity)
            resize(size * 2);
            }
        }

    /**
     * Check to see if the ExtHashMap needs to grow or shrink based on a planned capacity need.
     *
     * @param plannedSize  the capacity to assume that the map will need
     */
    private void checkCapacity(Int plannedSize)
        {
        // check if a resize is necessary
        if (plannedSize < shrinkAt || plannedSize > growAt)
            {
            // perform the resize of the hash buckets
            resize(plannedSize);
            }
        }

    /**
     * Resize the ExtHashMap based on the assumed capacity need.
     *
     * @param plannedSize  the capacity to assume that the map will need
     */
    private void resize(Int plannedSize)
        {
        (Int bucketCount, Int growAt, Int shrinkAt) = calcBucketCount(plannedSize);
        HashEntry?[] oldBuckets = buckets;
        HashEntry?[] newBuckets = new HashEntry?[bucketCount];

        for (HashEntry? entry : oldBuckets)
            {
            while (entry != null)
                {
                // before we change the "next reference", remember which one is next in the old
                // bucket
                Entry entryNext = entry.next;

                // move the entry to a new hash bucket
                Int newBucket = entry.keyhash % bucketCount;
                entry.next = newBuckets[newBucket];
                newBuckets[newBucket] = entry;

                entry = entryNext;
                }
            }

        this.buckets  = newBuckets;
        this.growAt   = growAt;
        this.shrinkAt = shrinkAt;
        }

    /**
     * Select a desired number of buckets to use for the specified entry capacity.
     *
     * @param capacity  the number of entries to be able to manage efficiently
     *
     * @return the suggested number of buckets to achieve the specified capacity, and the
     *         suggested grow and shrink thresholds
     */
// TODO named return values: protected static (Int bucketCount, Int growAt, Int shrinkAt)
    protected static (Int, Int, Int) calcBucketCount(Int capacity)
        {
        assert capacity >= 0;

        // shoot for 20% empty buckets (i.e. 25% oversize)
        Int target = capacity + (capacity >>> 2) + 1;

        // round up to a prime number by performing a binary search for the target size through an
        // array of prime values
        Int   first = 0;
        Int   last  = PRIMES.size - 1;
        Search: do
            {
            Int midpoint = (first + last) >>> 1;
            switch (capacity <=> midpoint)
                {
                case Lesser:
                    last = midpoint - 1;
                    break;
                case Equal:
                    // exact match; stop searching
                    first = midpoint;
                    break Search;
                case Greater:
                    first = midpoint + 1;
                    break;
                }
            }
        while (first <= last);

        Int bucketCount = first < PRIMES.size ? PRIMES[first] : target;

        // shrink when falls below 20% capacity
        Int shrinkThreshold = first <= 8 ? -1 : ((bucketCount >>> 2) - (bucketCount >>> 5) - (bucketCount >>> 6));

        // grow when around 80% capacity
        Int growThreshold = bucketCount - (bucketCount >>> 2) + (bucketCount >>> 5) + (bucketCount >>> 6);

        return buckets, growThreshold, shrinkThreshold;
        }

    /**
     * Primes used for bucket array sizes (to ensure a prime modulo).
     */
    protected static Int[] PRIMES =
        [
        7, 13, 23, 37, 47, 61, 79, 107, 137, 181, 229, 283, 349, 419, 499, 599, 727, 863, 1013,
        1187, 1399, 1697, 2039, 2503, 3253, 4027, 5113, 6679, 8999, 11987, 16381, 21023, 28351,
        39719, 65521, 99991, 149993, 262139, 524269, 1048571, 2097143, 4194301, 8388593, 16777213,
        33554393, 67108859, 134217689, 268435399, 536870909, 1073741789, 2147483647, 4294967291,
        8589934583, 17179869143, 34359738337, 68719476731, 137438953447, 274877906899, 549755813881
        ];
    }

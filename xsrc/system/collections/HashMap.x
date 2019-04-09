import maps.EntryKeys;
import maps.EntryValues;
import maps.ReifiedEntry;

/**
 * HashMap is a hashed implementation of the Map interface. One of two conditions is required:
 * * If no [Hasher] is provided, then the KeyType must be immutable and must implement Hashable; or
 * * If a [Hasher] is provided, then the KeyType does not have to be immutable and does not have to
 *   implement Hashable.
 */
class HashMap<KeyType, ValueType>
        implements Map<KeyType, ValueType>
        // TODO conditional incorporation of ... HashMap<KeyType extends immutable Hashable, ValueType>
        // TODO VariablyMutable interfaces
        incorporates Stringer
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the HashMap with the specified hasher and (optional) initial capacity.
     *
     * @param hasher
     * @param initCapacity  the number of expected entries
     */
    construct(Int initCapacity = 0)
        {
        assert(KeyType.is(Type<Hashable>));

        this.hasher = new NaturalHasher<KeyType>();

        // allocate the initial capacity
        (Int bucketCount, this.growAt) = calcBucketCount(initCapacity);
        buckets    = new HashEntry?[bucketCount];
        mutability = Mutable;
        }

    /**
     * Construct the HashMap with the specified hasher and (optional) initial capacity.
     *
     * @param hasher
     * @param initCapacity  the number of expected entries
     */
    construct(Hasher<KeyType> hasher, Int initCapacity = 0)
        {
        this.hasher = hasher;

        // allocate the initial capacity
        (Int bucketCount, this.growAt) = calcBucketCount(initCapacity);
        buckets    = new HashEntry?[bucketCount];
        mutability = Mutable;
        }

    // ----- internal state ------------------------------------------------------------------------

    /**
     * The Hasher is the thing that knows how to take a key and provide a hash code, or compare two
     * keys for equality -- even if the keys don't know how to do that themselves.
     */
    public/private Hasher<KeyType> hasher;

    /**
     * This is the Entry implementation used to store the HashMap's keys and values.
     */
    protected static class HashEntry(KeyType key, Int keyhash, ValueType value, HashEntry? next = null);

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

    /**
     * This is the primary means to find a HashEntry in the HashMap.
     *
     * @param key  the key to find in the map
     *
     * @return True iff the key is in the map
     * @return the HashEntry identified by the key
     */
    protected conditional HashEntry find(KeyType key)
        {
        Int        keyhash  = hasher.hashOf(key);
        Int        bucketId = keyhash % buckets.size;
        HashEntry? entry    = buckets[bucketId];
        while (entry != null)
            {
            if (entry.keyhash == keyhash && hasher.areEqual(entry.key, key))
                {
                return True, entry;
                }
            entry = entry.next;
            }
        return False;
        }

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
    Boolean contains(KeyType key)
        {
        return find(key);
        }

    @Override
    conditional ValueType get(KeyType key)
        {
        if (HashEntry entry : find(key))
            {
            return True, entry.value;
            }
        return False;
        }

    @Override
    conditional HashMap put(KeyType key, ValueType value)
        {
        Int        keyhash  = hasher.hashOf(key);
        Int        bucketId = keyhash % buckets.size;
        HashEntry? entry    = buckets[bucketId];
        while (entry != null)
            {
            if (entry.keyhash == keyhash && hasher.areEqual(entry.key, key))
                {
                if (value == entry.value)
                    {
                    return False;
                    }

                entry.value = value;
                return True, this;
                }

            entry = entry.next;
            }

        buckets[bucketId] = new HashEntry(key, keyhash, value, buckets[bucketId]);
        ++addCount;
        checkCapacity();

        return True, this;
        }

    @Override
    conditional HashMap putAll(Map<KeyType, ValueType> that)
        {
        // check the capacity up front (to avoid multiple resizes); the worst case is that we end
        // up a bit bigger than we want
        checkCapacity(size + that.size);

        HashEntry?[] buckets     = this.buckets;
        Int          bucketCount = buckets.size;
        Boolean      modified    = False;
        NextPut: for (Entry entry : that.entries)
            {
            KeyType    key       = entry.key;
            Int        keyhash   = hasher.hashOf(key);
            Int        bucketId  = keyhash % bucketCount;
            HashEntry? currEntry = buckets[bucketId];
            while (currEntry != null)
                {
                if (currEntry.keyhash == keyhash && hasher.areEqual(currEntry.key, key))
                    {
                    // an entry with the same key already exists in the HashMap
                    if (currEntry.value != entry.value)
                        {
                        currEntry.value = entry.value;
                        modified        = True;
                        }
                    continue NextPut;
                    }
                currEntry = currEntry.next;
                }
            // no such entry with the same key in the HashMap; create a new HashEntry
            buckets[bucketId] = new HashEntry(key, keyhash, entry.value, buckets[bucketId]);
            ++addCount;
            }

        return modified, this;
        }

    @Override
    conditional HashMap remove(KeyType key)
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
                return True, this;
                }

            prevEntry = entry;
            entry = entry.next;
            }

        return False;
        }

    @Override
    conditional HashMap clear()
        {
        Int entryCount = size;
        if (entryCount == 0)
            {
            return False;
            }

        (Int bucketCount, this.growAt, this.shrinkAt) = calcBucketCount(0);
        buckets = new HashEntry?[bucketCount];
        removeCount += entryCount;
        assert size == 0;
        return True, this;
        }

    @Override
    @Lazy public/private Set<KeyType> keys.calc()
        {
        return new EntryKeys<KeyType, ValueType>(this);
        }

    @Override
    @Lazy public/private Collection<ValueType> values.calc()
        {
        return new EntryValues<KeyType, ValueType>(this);
        }

    @Override
    @Lazy public/private EntrySet entries.calc()
        {
        return new EntrySet();
        }

    @Override
    <ResultType> ResultType process(KeyType key,
            function ResultType (Entry) compute)
        {
        return compute(new ReifiedEntry(this, key));
        }


    // ----- Entry implementation ------------------------------------------------------------------

    /**
     * An implementation of Entry that can be used as a cursor over any number of keys, and
     * delegates back to the map for its functionality.
     */
    class CursorEntry
            implements Entry
        {
        @Unassigned
        private HashEntry hashEntry;

        // TODO fails to find this if it is protected
        CursorEntry advance(HashEntry hashEntry)
            {
            this.hashEntry = hashEntry;
            this.exists    = true;
            return this;
            }

        @Override
        KeyType key.get()
            {
            return hashEntry.key;
            }

        @Override
        public/protected Boolean exists;

        @Override
        ValueType value
            {
            @Override
            ValueType get()
                {
                if (exists)
                    {
                    return hashEntry.value;
                    }
                else
                    {
                    throw new OutOfBounds("entry does not exist for key=" + key);
                    }
                }

            @Override
            void set(ValueType value)
                {
                verifyNotPersistent();
                if (exists)
                    {
                    hashEntry.value = value;
                    }
                else
                    {
                    HashMap.this.put(key, value);
                    assert hashEntry : HashMap.this.find(key);
                    exists = true;
                    }
                }
            }

        @Override
        void remove()
            {
            if (verifyNotPersistent() & exists)
                {
                assert HashMap.this.keys.remove(key);
                exists = false;
                }
            }

        @Override
        Entry reify()
            {
            return new ReifiedEntry(HashMap.this, key);
            }
        }


    // ----- EntrySet implementation ---------------------------------------------------------------

    /**
     * A representation of all of the HashEntry objects in the Map.
     */
    class EntrySet
            implements Set<Entry>
        {
        @Override
        Mutability mutability.get()
            {
            return Mutable;
            }

        @Override
        Iterator<Entry> iterator()
            {
            return new Iterator()
                {
                HashEntry?[] buckets     = HashMap.this.buckets;
                Int          nextBucket  = 0;
                HashEntry?   nextEntry   = null;
                Int          addSnapshot = HashMap.this.addCount;
                CursorEntry  entry       = new CursorEntry();

                @Override
                conditional Entry next()
                    {
                    if (addSnapshot != HashMap.this.addCount)
                        {
                        throw new ConcurrentModification();
                        }

                    Int bucketCount = buckets.size;
                    while (nextEntry == null && nextBucket < bucketCount)
                        {
                        nextEntry = buckets[nextBucket++];
                        }

                    HashEntry? currEntry = nextEntry;
                    if (currEntry != null)
                        {
                        // this is the entry to return;
                        // always load next one in the chain to avoid losing the position if/when
                        // the current entry is removed
                        nextEntry = currEntry.next;
                        return True, entry.advance(currEntry);
                        }

                    return False;
                    }
                };
            }

        @Override
        conditional EntrySet remove(Entry entry)
            {
            verifyMutable();

            Boolean removed = False;
            if (entry.is(CursorEntry))
                {
                HashEntry    hashEntry = entry.hashEntry;
                HashEntry?[] buckets   = HashMap.this.buckets;
                Int          keyhash   = hashEntry.keyhash;
                Int          bucketId  = keyhash % buckets.size;
                HashEntry?   currEntry = buckets[bucketId];
                HashEntry?   prevEntry = null;

                loop:
                while (currEntry != null)
                    {
                    // check if we found the entry that we're looking for
                    if (currEntry.keyhash == keyhash && hasher.areEqual(currEntry.key, hashEntry.key))
                        {
                        // verify that it is the same entry (i.e. values also have to match!)
                        if (&currEntry == &hashEntry || currEntry.value == hashEntry.value)
                            {
                            // unlink the entry that is being removed
                            if (prevEntry != null)
                                {
                                prevEntry.next = currEntry.next;
                                }
                            else
                                {
                                buckets[loop.count] = currEntry.next;
                                }

                            ++HashMap.this.removeCount;
                            removed = True;
                            }

                        // whether or not the entry matched and was removed, it was the one that we
                        // were looking for (i.e. there will be no other entry with that same key)
                        return removed, this;
                        }

                    prevEntry = currEntry;
                    currEntry = currEntry.next;
                    }
                }
            else
                {
                removed = HashMap.this.remove(entry.key, entry.value);
                }

            return removed, this;
            }

        @Override
        conditional EntrySet removeIf(function Boolean (Entry) shouldRemove)
            {
            Boolean      modified    = False;
            HashEntry?[] buckets     = HashMap.this.buckets;
            Int          bucketCount = buckets.size;
            CursorEntry  entry       = new CursorEntry();
            for (Int i = 0; i < bucketCount; ++i)
                {
                HashEntry? currEntry = buckets[i];
                HashEntry? prevEntry = null;
                while (currEntry != null)
                    {
                    if (shouldRemove(entry.advance(currEntry)))
                        {
                        // move to the next entry (the current one is getting unlinked)
                        currEntry = currEntry.next;

                        // unlink the entry that is being removed
                        if (prevEntry != null)
                            {
                            prevEntry.next = currEntry;
                            }
                        else
                            {
                            buckets[i] = currEntry;
                            }
                        modified = True;
                        ++HashMap.this.removeCount;
                        }
                    else
                        {
                        prevEntry = currEntry;
                        currEntry = currEntry.next;
                        }
                    }
                }

            return modified, this;
            }

        @Override
        Stream<Entry> stream()
            {
            TODO
            }

        @Override
        EntrySet clone()
            {
            TODO
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Some operations require that the Map be Mutable; this method throws an exception if the Map
     * is not Mutable.
     *
     * @return True
     *
     * @throws ReadOnly if the Map is not Mutable
     */
    protected Boolean verifyMutable()
        {
        if (mutability != Mutable)
            {
            throw new ReadOnly("Map operation requires mutability==Mutable");
            }
        return True;
        }

    /**
     * Verify that the Map's mutability is non-persistent.
     *
     * @return True
     *
     * @throws ReadOnly if the Map's mutability is persistent
     */
    protected Boolean verifyNotPersistent()
        {
        if (mutability.persistent)
            {
            throw new ReadOnly("Map operation requires mutability.persistent==False");
            }
        return True;
        }

    /**
     * Check to see if the HashMap needs to grow or shrink based on the current capacity need.
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
     * Check to see if the HashMap needs to grow or shrink based on a planned capacity need.
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
     * Resize the HashMap based on the assumed capacity need.
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
                HashEntry? entryNext = entry.next;

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
    static (Int bucketCount, Int growAt, Int shrinkAt) calcBucketCount(Int capacity)
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

        return bucketCount, growThreshold, shrinkThreshold;
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

import maps.EntryKeys;
import maps.EntryValues;
import maps.KeyEntry;

/**
 * HashMap is a hashed implementation of the Map interface. One of two conditions is required:
 * * If no [Hasher] is provided, then the Key type must be immutable and must implement Hashable; or
 * * If a [Hasher] is provided, then the Key type does not have to be immutable and does not have to
 *   implement Hashable.
 *
 * The iterators provided by this map are stable in presence of structural changes to the map and
 * will not throw [ConcurrentModification], return duplicate entries, or skip entries which remain
 * present over the coarse of iteration. The iterator may return entries which were inserted after
 * the creation of the iterator.
 */
class HashMap<Key, Value>
        implements CopyableMap<Key, Value>
        incorporates conditional MapFreezer<Key extends immutable Object, Value extends ImmutableAble>
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
        assert(Key.is(Type<Hashable>));
        construct HashMap(new NaturalHasher<Key>(), initCapacity);
        }

    /**
     * Construct the HashMap with the specified hasher and (optional) initial capacity.
     *
     * @param hasher
     * @param initCapacity  the number of expected entries
     */
    construct(Hasher<Key> hasher, Int initCapacity = 0)
        {
        this.hasher = hasher;

        // allocate the initial capacity
        (Int bucketCount, this.growAt) = calcBucketCount(initCapacity);
        buckets = new HashEntry?[bucketCount];
        }

    /**
     * Duplicable constructor.
     *
     * @param that  another map to copy the contents from when constructing this HashMap
     */
    construct(HashMap<Key, Value> that)
        {
        this.hasher      = that.hasher;
        this.buckets     = new HashEntry?[that.buckets.size](i -> that.buckets[i]?.duplicate() : Null);
        this.growAt      = that.growAt;
        this.shrinkAt    = that.shrinkAt;
        this.addCount    = that.addCount;
        this.removeCount = that.removeCount;
        }

    /**
     * Copy constructor.
     *
     * @param that  another map to copy the contents from when constructing this HashMap
     */
    construct(Map<Key, Value> that)
        {
        if (that.is(HashMap))
            {
            construct HashMap(that);
            }
        else
            {
            construct HashMap(that.size);
            }
        }
    finally
        {
        if (!that.is(HashMap))
            {
            putAll(that);
            }
        }


    // ----- internal state ------------------------------------------------------------------------

    /**
     * The Hasher is the thing that knows how to take a key and provide a hash code, or compare two
     * keys for equality -- even if the keys don't know how to do that themselves.
     */
    public/private Hasher<Key> hasher;

    /**
     * This is the Entry implementation used to store the HashMap's keys and values.
     */
    protected static class HashEntry(Key key, Int keyhash, Value value, HashEntry? next = Null)
            implements Duplicable
        {
        construct(HashEntry that)
            {
            this.key     = that.key;
            this.keyhash = that.keyhash;
            this.value   = that.value;
            this.next    = that.next?.duplicate() : Null;
            }
        }

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
    protected conditional HashEntry find(Key key)
        {
        Int        keyhash  = hasher.hashOf(key);
        Int        bucketId = keyhash % buckets.size;
        HashEntry? entry    = buckets[bucketId];
        while (entry != Null)
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
    Boolean empty.get()
        {
        return addCount == removeCount;
        }

    @Override
    Boolean contains(Key key)
        {
        return find(key);
        }

    @Override
    conditional Value get(Key key)
        {
        if (HashEntry entry := find(key))
            {
            return True, entry.value;
            }
        return False;
        }

    @Override
    HashMap put(Key key, Value value)
        {
        Int        keyhash  = hasher.hashOf(key);
        Int        bucketId = keyhash % buckets.size;
        HashEntry? entry    = buckets[bucketId];
        while (entry != Null)
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
    HashMap putAll(Map<Key, Value> that)
        {
        // check the capacity up front (to avoid multiple resizes); the worst case is that we end
        // up a bit bigger than we want
        checkCapacity(size + that.size);

        HashEntry?[] buckets     = this.buckets;
        Int          bucketCount = buckets.size;
        NextPut: for (Map<Key, Value>.Entry entry : that.entries)
            {
            Key        key       = entry.key;
            Int        keyhash   = hasher.hashOf(key);
            Int        bucketId  = keyhash % bucketCount;
            HashEntry? currEntry = buckets[bucketId];
            while (currEntry != Null)
                {
                if (currEntry.keyhash == keyhash && hasher.areEqual(currEntry.key, key))
                    {
                    // an entry with the same key already exists in the HashMap
                    currEntry.value = entry.value;
                    continue NextPut;
                    }
                currEntry = currEntry.next;
                }
            // no such entry with the same key in the HashMap; create a new HashEntry
            buckets[bucketId] = new HashEntry(key, keyhash, entry.value, buckets[bucketId]);
            ++addCount;
            }

        return this;
        }

    @Override
    HashMap remove(Key key)
        {
        Int        keyhash   = hasher.hashOf(key);
        Int        bucketId  = keyhash % buckets.size;
        HashEntry? entry     = buckets[bucketId];
        HashEntry? prevEntry = Null;
        while (entry != Null)
            {
            if (entry.keyhash == keyhash && hasher.areEqual(entry.key, key))
                {
                // unlink the entry
                if (prevEntry != Null)
                    {
                    prevEntry.next = entry.next;
                    }
                else
                    {
                    buckets[bucketId] = entry.next;
                    }
                entry.next = Null;

                ++removeCount;
                return this;
                }

            prevEntry = entry;
            entry = entry.next;
            }

        return this;
        }

    @Override
    HashMap clear()
        {
        Int entryCount = size;
        if (entryCount == 0)
            {
            return this;
            }

        // TODO implement Persistent/Constant mutability
        verifyInPlace();

        (Int bucketCount, this.growAt, this.shrinkAt) = selectBucketCount(0);
        buckets = new HashEntry?[bucketCount];
        removeCount += entryCount;
        assert size == 0;
        return this;
        }

    @Override
    @Lazy public/private Set<Key> keys.calc()
        {
        return new EntryKeys<Key, Value>(this);
        }

    @Override
    @Lazy public/private Collection<Value> values.calc()
        {
        return new EntryValues<Key, Value>(this);
        }

    @Override
    @Lazy public/private EntrySet entries.calc()
        {
        return new EntrySet();
        }

    @Override
    <Result> Result process(Key key,
            function Result (Map<Key, Value>.Entry) compute)
        {
        return compute(reifyEntry(key));
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

        protected CursorEntry advance(HashEntry hashEntry)
            {
            this.hashEntry = hashEntry;
            this.exists    = True;
            return this;
            }

        @Override
        Key key.get()
            {
            return hashEntry.key;
            }

        @Override
        public/protected Boolean exists;

        @Override
        Value value
            {
            @Override
            Value get()
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
            void set(Value value)
                {
                verifyInPlace();
                if (exists)
                    {
                    hashEntry.value = value;
                    }
                else
                    {
                    this.HashMap.put(key, value);
                    assert hashEntry := this.HashMap.find(key);
                    exists = True;
                    }
                }
            }

        @Override
        void delete()
            {
            if (verifyInPlace() & exists)
                {
                assert this.HashMap.keys.removeIfPresent(key);
                exists = False;
                }
            }

        @Override
        Map<Key, Value>.Entry reify()
            {
            return reifyEntry(key);
            }
        }


    // ----- EntrySet implementation ---------------------------------------------------------------

    /**
     * A representation of all of the HashEntry objects in the Map.
     */
    class EntrySet
            implements Collection<Entry>
            implements Freezable
        {
        @Override
        Int size.get()
            {
            return this.HashMap.size;
            }

        @Override
        Iterator<Entry> iterator()
            {
            return new StableEntryIterator();
            }

        /**
         * An iterator over the maps entries which is stable in the presence of concurrent modifications.
         */
        class StableEntryIterator
                // TODO: extends CursorEntry
                implements Iterator<Entry>
            {

            private construct()
                {
                }

            private construct(StableEntryIterator that)
                {
                this.bucketCount = that.bucketCount;
                this.bucketNext = that.bucketNext;
                this.entryNext = that.entryNext;
                this.firstEntry = that.firstEntry;
                that.secondEntry = that.secondEntry;

                HashEntry?[]? entries = that.entries;
                if (entries != Null)
                    {
                    this.entries = new HashEntry?[](Array.Mutability.Mutable, entries);
                    }

                Int[]? processed = that.processed;
                if (processed != Null)
                    {
                    this.processed = new Int[](Array.Mutability.Mutable, processed);
                    }
                }

            /**
             * The number of buckets in the map at the time that the current bucket was cached.
             */
            private Int bucketCount = this.HashMap.buckets.size;

            /**
             * The next bucket to process.
             */
            private Int bucketNext;

            /**
             * Pairs of bucketCount and bucketNext which had been fully processed before
             * the map's bucketCount changed.
             */
            private Int[]? processed;

            /**
             * The first unprocessed entry in the currently cached bucket.
             * This is an optimization array allocation for common acse of small buckets.
             */
            private HashEntry? firstEntry; // to avoid entries array inflation

            /**
             * The second unprocessed entry in the currently cached bucket.
             * This is an optimization array allocation for common acse of small buckets.
             */
            private HashEntry? secondEntry; // to avoid entries  array inflation

            /**
             * The cached bucket consisting of unprocessed entries.
             */
            private HashEntry?[]? entries; // unprocessed entries from current bucket

            /**
             * The next entry in the cached bucket to process.
             */
            private Int entryNext;

            /**
             * The re-usable [Entry] to return from [next]
             */
            private CursorEntry cursor = new CursorEntry();

            /**
             * Return the unprocessed entry from the cached bucket at a given index.
             *
             * @param i the entry index
             *
             * @return the entry, or [Null]
             */
            protected HashEntry? entriesOf(Int i)
                {
                switch (i)
                    {
                    case 0:
                        return firstEntry;

                    case 1:
                        return secondEntry;

                    default:
                        HashEntry?[]? entries = this.entries;
                        if (entries == Null || i - 2 >= entries.size)
                            {
                            return Null;
                            }
                        return entries[i - 2];
                    }
                }

            /**
             * Set the entry for a given index in the cached bucket.
             *
             * @param i the index
             * @param entry the entry or [Null]
             */
            protected void entriesOf(Int i, HashEntry? entry)
                {
                switch (i)
                    {
                    case 0:
                        firstEntry = entry;
                        break;

                    case 1:
                        secondEntry = entry;
                        break;

                    default:
                        HashEntry?[]? entries = this.entries;
                        if (entries == Null)
                            {
                            entries = new HashEntry?[](2);
                            this.entries = entries;
                            }
                        entries[i - 2] = entry;
                        break;
                    }
                }

            /**
             * @return the size of the cached bucket
             */
            protected Int entriesSize()
                {
                HashEntry?[]? entries = this.entries;
                if (entries == Null)
                    {
// TODO: GG
//                    return secondEntry == Null
//                        ? firstEntry == Null
//                            ? 0
//                            : 1
//                        : 2;
                    if (secondEntry != Null)
                        {
                        return 2;
                        }
                    else if (firstEntry != Null)
                        {
                        return 1;
                        }

                    return 0;
                    }

                return 2 + entries.size;
                }

            /**
             * Fix the state of the iterator after witnessing a change in the map's bucket count.
             */
            private void fixup()
                {
                // we've re-hashed; record our progress
                HashEntry?[] buckets   = this.HashMap.buckets;
                Int[]?       processed = this.processed;
                if (processed == Null)
                    {
                    if (bucketNext > 0)
                        {
                        processed = new Int[](2);
                        processed[0] = bucketCount;
                        processed[1] = bucketNext;
                        this.processed = processed;
                        }
                    bucketCount = buckets.size;
                    bucketNext = 0;
                    }
                else
                    {
                    for (Int i = 0; ; i += 2)
                        {
                        if (processed[i] == bucketCount)
                            {
                            // update existing processed data for bucket
                            processed[i + 1] = bucketNext;
                            break;
                            }
                        else if (i == processed.size - 2)
                            {
                            // record a new bucketCount and progress buckets
                            processed += [bucketCount, bucketNext];
                            break;
                            }
                        }

                    // determine which bucket to restart from
                    bucketCount = buckets.size;
                    bucketNext = 0;
                    for (Int i = 0; i < processed.size; i += 2)
                        {
                        if (processed[i] == bucketCount)
                            {
                            // we've previously worked at this bucketCount; resume at next bucket
                            bucketNext = processed[i + 1];
                            break;
                            }
                        }
                    }
                }

            /**
             * Advance to the next populated bucket, returning the first unprocessed entry and caching
             * the remainder from its bucket.
             *
             * @return [False] if the iterator is exhausted, else the first unprocessed entry from
             *         the next bucket
             */
            private conditional Entry advanceBucket()
                {
                Int bucketCount = this.bucketCount;
                Int bucketNext = this.bucketNext;
                if (bucketNext == bucketCount)
                    {
                    return False;
                    }

                HashEntry?[] buckets = this.HashMap.buckets;
                if (bucketCount != buckets.size)
                    {
                    fixup();
                    bucketCount = this.bucketCount;
                    bucketNext = this.bucketNext;
                    }

                Int ofEntry = -1;

                for (; bucketNext < buckets.size; ++bucketNext)
                    {
                    // find next populated bucket
                    if (HashEntry next ?= buckets[bucketNext])
                        {
                        HashEntry? entry = Null;
                        // copy bucket entries which we haven't visited yet
                        NEXT_ENTRY: do
                            {
                            // filter out processed entries
                            Int[]? processed = this.processed;
                            if (processed != Null)
                                {
                                for (Int i = 0; i < processed.size; i += 2)
                                    {
                                    Int bucketId = next.keyhash % processed[i];
                                    if (bucketId < processed[i + 1])
                                        {
                                        // we've previously processed this entry
                                        continue NEXT_ENTRY;
                                        }
                                    }
                                }

                            // we've yet to process this entry
                            if (entry == Null)
                                {
                                entry = next;
                                }
                            else
                                {
                                entriesOf(ofEntry, next);
                                }

                            ++ofEntry;
                            }
                        while (next ?= next.next);

                        if (ofEntry >= 0)
                            {
                            this.bucketNext = bucketNext + 1;
                            this.entryNext = 0;
                            return True, cursor.advance(entry);
                            }
                        }
                    }

                return False;
                }

            @Override
            conditional Entry next()
                {
                Int entryNext = this.entryNext;
                HashEntry? next = entriesOf(entryNext);
                if (next == Null)
                    {
                    return advanceBucket();
                    }

                entriesOf(entryNext, Null); // clear for reuse in advanceBucket
                this.entryNext = entryNext + 1;
                return True, cursor.advance(next);
                }

            @Override
            Boolean knownDistinct()
                {
                return True;
                }

            @Override
            conditional Int knownSize()
                {
                return True, this.HashMap.size;
                }

            @Override
            (Iterator<Entry>, Iterator<Entry>) bifurcate()
                {
                return new StableEntryIterator(this), new StableEntryIterator(this);
                }
            }

        @Override
        EntrySet remove(Entry entry)
            {
            verifyInPlace();

            if (entry.is(CursorEntry))
                {
                HashEntry    hashEntry = entry.hashEntry;
                HashEntry?[] buckets   = this.HashMap.buckets;
                Int          keyhash   = hashEntry.keyhash;
                Int          bucketId  = keyhash % buckets.size;
                HashEntry?   currEntry = buckets[bucketId];
                HashEntry?   prevEntry = Null;

                loop: while (currEntry != Null)
                    {
                    // check if we found the entry that we're looking for
                    if (currEntry.keyhash == keyhash && hasher.areEqual(currEntry.key, hashEntry.key))
                        {
                        // verify that it is the same entry (i.e. values also have to match!)
                        if (&currEntry == &hashEntry || currEntry.value == hashEntry.value)
                            {
                            // unlink the entry that is being removed
                            if (prevEntry != Null)
                                {
                                prevEntry.next = currEntry.next;
                                }
                            else
                                {
                                buckets[loop.count] = currEntry.next;
                                }

                            ++this.HashMap.removeCount;
                            }

                        // whether or not the entry matched and was removed, it was the one that we
                        // were looking for (i.e. there will be no other entry with that same key)
                        return this;
                        }

                    prevEntry = currEntry;
                    currEntry = currEntry.next;
                    }
                }
            else
                {
                this.HashMap.remove(entry.key, entry.value);
                }

            return this;
            }

        @Override
        (EntrySet, Int) removeAll(function Boolean (Entry) shouldRemove)
            {
            Int          removed     = 0;
            HashEntry?[] buckets     = this.HashMap.buckets;
            Int          bucketCount = buckets.size;
            CursorEntry  entry       = new CursorEntry();
            for (Int i = 0; i < bucketCount; ++i)
                {
                HashEntry? currEntry = buckets[i];
                HashEntry? prevEntry = Null;
                while (currEntry != Null)
                    {
                    if (shouldRemove(entry.advance(currEntry)))
                        {
                        // move to the next entry (the current one is getting unlinked)
                        currEntry = currEntry.next;

                        // unlink the entry that is being removed
                        if (prevEntry != Null)
                            {
                            prevEntry.next = currEntry;
                            }
                        else
                            {
                            buckets[i] = currEntry;
                            }
                        ++removed;
                        ++this.HashMap.removeCount;
                        }
                    else
                        {
                        prevEntry = currEntry;
                        currEntry = currEntry.next;
                        }
                    }
                }

            return this, removed;
            }

        @Override
        immutable EntrySet freeze(Boolean inPlace = False)
            {
            assert this.HashMap.is(immutable HashMap);
            return makeImmutable();
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Some operations require that the Map be mutable; this method throws an exception if the Map
     * is not mutable.
     *
     * @return True
     *
     * @throws ReadOnly if the Map is not mutable
     */
    protected Boolean verifyInPlace()
        {
        if (!inPlace)
            {
            throw new ReadOnly("Map operation requires inPlace==True");
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
        (Int bucketCount, Int growAt, Int shrinkAt) = selectBucketCount(plannedSize);
        HashEntry?[] oldBuckets = buckets;
        HashEntry?[] newBuckets = new HashEntry?[bucketCount];

        for (HashEntry? entry : oldBuckets)
            {
            while (entry != Null)
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
     * Instantiate a reified entry, which must be a child of the map.
     */
    private Entry reifyEntry(Key key)
        {
        return new @KeyEntry(key) Entry() {};
        }

    /**
     * Select a desired number of buckets to use for the specified entry capacity.
     *
     * @param capacity  the number of entries to be able to manage efficiently
     *
     * @return the suggested number of buckets to achieve the specified capacity, and the
     *         suggested grow and shrink thresholds
     */
    protected (Int bucketCount, Int growAt, Int shrinkAt) selectBucketCount(Int capacity)
        {
        return calcBucketCount(capacity);
        }

    /**
     * Compute a desired number of buckets to use for the specified entry capacity.
     *
     * @param capacity  the number of entries to be able to manage efficiently
     *
     * @return the suggested number of buckets to achieve the specified capacity, and the
     *         suggested grow and shrink thresholds
     */
    static (Int bucketCount, Int growAt, Int shrinkAt) calcBucketCount(Int capacity)
        {
        assert capacity >= 0;

        // shoot for 20% empty buckets (i.e. 50% oversize)
        Int target = capacity + (capacity >>> 1) + 15;

        // round up to a prime number by performing a binary search for the target size through an
        // array of prime values
        Int   first = 0;
        Int   last  = PRIMES.size - 1;
        Search: do
            {
            Int midpoint = (first + last) >>> 1;
            switch (target <=> PRIMES[midpoint])
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

import maps.EntryKeys;
import maps.EntryValues;
import maps.KeyEntry;


/**
 * HasherMap is a hashed implementation of the Map interface. It uses the supplied [Hasher] to
 * support hashing and comparison for the `Key` type.
 *
 * The iterators provided by this map are stable in presence of structural changes to the map and
 * will not throw [ConcurrentModification], return duplicate entries, or skip entries which remain
 * present over the coarse of iteration. The iterator may return entries which were inserted after
 * the creation of the iterator.
 */
class HasherMap<Key, Value>
        implements HasherReplicable<Key>
        implements CopyableMap<Key, Value>
        implements Hashable
        incorporates conditional MapFreezer<Key extends immutable Object, Value extends Shareable>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the HasherMap with the specified hasher and (optional) initial capacity.
     *
     * @param hasher        the [Hasher] to use
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
     * Copy constructor from another [Map].
     *
     * @param hasher  the [Hasher] to use
     * @param that    another map to copy the contents from when constructing this HasherMap
     */
    construct(Hasher<Key> hasher, Map<Key, Value> that)
        {
        if (that.is(HasherMap) && hasher == that.hasher)
            {
            // optimization:
            construct HasherMap(that);
            }
        else
            {
            construct HasherMap(hasher, that.size);
            }
        }
    finally // REVIEW GG+CP - wouldn't it be nice if "finally" could capture from the "construct" block?
        {
        if (!(that.is(HasherMap) && hasher == that.hasher))
            {
            putAll(that);
            }
        }

    /**
     * Duplicable constructor.
     *
     * @param that  another HasherMap to copy the contents from when constructing this HasherMap
     */
    construct(HasherMap<Key, Value> that)
        {
        this.hasher      = that.hasher;
        this.buckets     = new HashEntry?[that.buckets.size](i -> that.buckets[i]?.duplicate() : Null);
        this.growAt      = that.growAt;
        this.shrinkAt    = that.shrinkAt;
        this.addCount    = that.addCount;
        this.removeCount = that.removeCount;
        }


    // ----- internal state ------------------------------------------------------------------------

    /**
     * The Hasher is the thing that knows how to take a key and provide a hash code, or compare two
     * keys for equality -- even if the keys don't know how to do that themselves.
     */
    public/private Hasher<Key> hasher;

    /**
     * This is the Entry implementation used to store the HasherMap's keys and values.
     */
    protected static class HashEntry(Key key, Int hash, Value value, HashEntry? next = Null)
            implements Duplicable
        {
        construct(HashEntry that)
            {
            this.key   = that.key;
            this.hash  = that.hash;
            this.value = that.value;
            this.next  = that.next?.duplicate() : Null;
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
     * This is the primary means to find a HashEntry in the HasherMap.
     *
     * @param key  the key to find in the map
     *
     * @return True iff the key is in the map
     * @return the HashEntry identified by the key
     */
    protected conditional HashEntry find(Key key)
        {
        Int        hash     = hasher.hashOf(key);
        Int        bucketId = hash % buckets.size;
        HashEntry? entry    = buckets[bucketId];
        while (entry != Null)
            {
            if (entry.hash == hash && hasher.areEqual(entry.key, key))
                {
                return True, entry;
                }
            entry = entry.next;
            }
        return False;
        }


    // ----- HasherReplicable interface ------------------------------------------------------------

    /**
     * This is the equivalent of the [Replicable] interface, but specific to the HasherMap, because
     * it requires a Hasher for its construction. It allows a HasherMap to replicate itself without
     * having to resort to reflection, and enforces the contract on sub-classes.
     */
    static interface HasherReplicable<Key>
        {
        /**
         * Construct the HasherMap with the specified hasher and (optional) initial capacity.
         *
         * @param hasher        the [Hasher] to use
         * @param initCapacity  the number of expected entries
         */
        construct(Hasher<Key> hasher, Int initCapacity = 0);
        }


    // ----- Duplicable interface ------------------------------------------------------------------

    @Override
    HasherMap duplicate(function (Key, Value)(Key, Value)? transform = Null)
        {
        if (this.is(immutable HasherMap) && transform == Null)
            {
            return this;
            }

        if (transform == Null)
            {
            return this.new(this);
            }

        HasherMap<Key, Value> that = this.new(hasher);
        for ((Key key, Value value) : this)
            {
            (key, value) = transform(key, value); // TODO GG: inline
            that = that.put(key, value);
            }
        return that;
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
    HasherMap put(Key key, Value value)
        {
        Int        hash     = hasher.hashOf(key);
        Int        bucketId = hash % buckets.size;
        HashEntry? entry    = buckets[bucketId];
        while (entry != Null)
            {
            if (entry.hash == hash && hasher.areEqual(entry.key, key))
                {
                entry.value = value;
                return this;
                }

            entry = entry.next;
            }

        buckets[bucketId] = new HashEntry(key, hash, value, buckets[bucketId]);
        ++addCount;
        checkCapacity();

        return this;
        }

    @Override
    HasherMap putAll(Map<Key, Value> that)
        {
        // check the capacity up front (to avoid multiple resizes); the worst case is that we end
        // up a bit bigger than we want
        checkCapacity(size + that.size);

        HashEntry?[] buckets     = this.buckets;
        Int          bucketCount = buckets.size;
        NextPut: for (Map<Key, Value>.Entry entry : that.entries)
            {
            Key        key       = entry.key;
            Int        hash      = hasher.hashOf(key);
            Int        bucketId  = hash % bucketCount;
            HashEntry? currEntry = buckets[bucketId];
            while (currEntry != Null)
                {
                if (currEntry.hash == hash && hasher.areEqual(currEntry.key, key))
                    {
                    // an entry with the same key already exists in the HasherMap
                    currEntry.value = entry.value;
                    continue NextPut;
                    }
                currEntry = currEntry.next;
                }
            // no such entry with the same key in the HasherMap; create a new HashEntry
            buckets[bucketId] = new HashEntry(key, hash, entry.value, buckets[bucketId]);
            ++addCount;
            }

        return this;
        }

    @Override
    HasherMap remove(Key key)
        {
        Int        hash      = hasher.hashOf(key);
        Int        bucketId  = hash % buckets.size;
        HashEntry? entry     = buckets[bucketId];
        HashEntry? prevEntry = Null;
        while (entry != Null)
            {
            if (entry.hash == hash && hasher.areEqual(entry.key, key))
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
    HasherMap clear()
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
                    this.HasherMap.put(key, value);
                    assert hashEntry := this.HasherMap.find(key);
                    exists = True;
                    }
                }
            }

        @Override
        void delete()
            {
            if (verifyInPlace() & exists)
                {
                assert this.HasherMap.keys.removeIfPresent(key);
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
            incorporates conditional EntrySetFreezer<Key extends immutable Object, Value extends Shareable>
        {
        @Override
        Int size.get()
            {
            return this.HasherMap.size;
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
                // TODO MF: extends CursorEntry
                implements Iterator<Entry>
            {
            /**
             * Construct an iterator over all of the entries in the HasherMap.
             */
            private construct()
                {
                }

            /**
             * Construct a clone of the passed StableEntryIterator.
             */
            private construct(StableEntryIterator that)
                {
                this.bucketCount     = that.bucketCount;
                this.nextBucketIndex = that.nextBucketIndex;
                this.nextEntryIndex  = that.nextEntryIndex;
                this.entry0          = that.entry0;
                that.entry1          = that.entry1;
                this.entry2toN       = that.entry2toN?.clone();
                this.processed       = that.processed?.clone();
                }

            /**
             * The number of buckets in the map at the time that the current bucket was cached.
             */
            private Int bucketCount = this.HasherMap.buckets.size;

            /**
             * The index of the next bucket to process.
             */
            private Int nextBucketIndex;

            /**
             * Pairs of bucketCount and nextBucketIndex which had been fully processed before
             * the map's bucketCount changed.
             */
            private Int[]? processed;

            /**
             * The first unprocessed entry in the currently cached bucket.
             *
             * This optimization avoids array allocation for the common case of small buckets.
             */
            private HashEntry? entry0;

            /**
             * The second unprocessed entry in the currently cached bucket.
             *
             * This optimization avoids array allocation for the common case of small buckets.
             */
            private HashEntry? entry1;

            /**
             * The cached bucket; contains the unprocessed entries from the current bucket.
             */
            private HashEntry?[]? entry2toN;

            /**
             * The next entry index in the cached bucket to process.
             */
            private Int nextEntryIndex;

            /**
             * The re-usable [Entry] to return from [next] call.
             */
            private CursorEntry cursor = new CursorEntry();

            /**
             * Return the unprocessed entry from the cached bucket at a given index.
             *
             * @param i  the entry index
             *
             * @return the entry or Null
             */
            protected HashEntry? getCachedEntry(Int i)
                {
                return switch (i)
                    {
                    case 0: entry0;
                    case 1: entry1;
                    default:
                        {
                        HashEntry?[]? entry2toN = this.entry2toN;
                        return entry2toN == Null || i - 2 >= entry2toN.size
                                ? Null
                                : entry2toN[i - 2];
                        };
                    };
                }

            /**
             * Set the entry for a given index in the cached bucket.
             *
             * @param i      the index
             * @param entry  the entry or [Null]
             */
            protected void setCachedEntry(Int i, HashEntry? entry)
                {
                switch (i)
                    {
                    case 0:
                        entry0 = entry;
                        break;

                    case 1:
                        entry1 = entry;
                        break;

                    default:
                        HashEntry?[]? entry2toN = this.entry2toN;
                        if (entry2toN == Null)
                            {
                            entry2toN      = new HashEntry?[](2);
                            this.entry2toN = entry2toN;
                            }
                        entry2toN[i - 2] = entry;
                        break;
                    }
                }

            /**
             * Fix the state of the iterator after witnessing a change in the map's bucket count.
             */
            private void fixup()
                {
                // we've re-hashed; record our progress
                HashEntry?[] buckets   = this.HasherMap.buckets;
                Int[]?       processed = this.processed;
                if (processed == Null)
                    {
                    if (nextBucketIndex > 0)
                        {
                        processed      = new Int[](Mutable, [bucketCount, nextBucketIndex]);
                        this.processed = processed;
                        }
                    bucketCount      = buckets.size;
                    nextBucketIndex  = 0;
                    }
                else
                    {
                    for (Int i = 0; ; i += 2)
                        {
                        if (processed[i] == bucketCount)
                            {
                            // update existing processed data for bucket
                            processed[i + 1] = nextBucketIndex;
                            break;
                            }
                        else if (i == processed.size - 2)
                            {
                            // record a new bucketCount and progress buckets
                            processed += [bucketCount, nextBucketIndex];
                            break;
                            }
                        }

                    // determine which bucket to restart from
                    bucketCount     = buckets.size;
                    nextBucketIndex = 0;
                    for (Int i = 0; i < processed.size; i += 2)
                        {
                        if (processed[i] == bucketCount)
                            {
                            // we've previously worked at this bucketCount; resume at next bucket
                            nextBucketIndex = processed[i + 1];
                            break;
                            }
                        }
                    }
                }

            /**
             * Advance to the next populated bucket, returning the first unprocessed entry and caching
             * the remainder from its bucket.
             *
             * @return False if the iterator is exhausted, else the first unprocessed entry from
             *         the next bucket
             */
            private conditional Entry advanceBucket()
                {
                Int bucketCount     = this.bucketCount;
                Int nextBucketIndex = this.nextBucketIndex;
                if (nextBucketIndex == bucketCount)
                    {
                    return False;
                    }

                HashEntry?[] buckets = this.HasherMap.buckets;
                if (bucketCount != buckets.size)
                    {
                    fixup();
                    bucketCount     = this.bucketCount;
                    nextBucketIndex = this.nextBucketIndex;
                    }

                for (Int entryIndex = -1; nextBucketIndex < buckets.size; ++nextBucketIndex)
                    {
                    // find next populated bucket
                    if (HashEntry next ?= buckets[nextBucketIndex])
                        {
                        HashEntry? entry = Null;
                        // copy bucket entries which we haven't visited yet
                        ProcessNextEntry: do
                            {
                            // filter out processed entries
                            Int[]? processed = this.processed;
                            if (processed != Null)
                                {
                                for (Int i = 0; i < processed.size; i += 2)
                                    {
                                    Int bucketId = next.hash % processed[i];
                                    if (bucketId < processed[i + 1])
                                        {
                                        // we've previously processed this entry
                                        continue ProcessNextEntry;
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
                                setCachedEntry(entryIndex, next);
                                }

                            ++entryIndex;
                            }
                        while (next ?= next.next);

                        if (entryIndex >= 0)
                            {
                            this.nextBucketIndex = nextBucketIndex + 1;
                            this.nextEntryIndex  = 0;
                            return True, cursor.advance(entry);
                            }
                        }
                    }

                return False;
                }

            @Override
            conditional Entry next()
                {
                Int        nextEntryIndex = this.nextEntryIndex;
                HashEntry? next           = getCachedEntry(nextEntryIndex);
                if (next == Null)
                    {
                    return advanceBucket();
                    }

                setCachedEntry(nextEntryIndex, Null); // clear for reuse in advanceBucket
                this.nextEntryIndex = nextEntryIndex + 1;
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
                return nextBucketIndex == 0 && processed == Null
                    ? (True, this.HasherMap.size) // we've yet to start
                    : nextBucketIndex == bucketCount && getCachedEntry(nextEntryIndex) == Null
                        ? (True, 0) // we've finished
                        : False;
                }

            @Override
            (Iterator<Entry>, Iterator<Entry>) bifurcate()
                {
                return this, new StableEntryIterator(this);
                }
            }

        @Override
        EntrySet remove(Entry entry)
            {
            verifyInPlace();

            if (entry.is(CursorEntry))
                {
                HashEntry    hashEntry = entry.hashEntry;
                HashEntry?[] buckets   = this.HasherMap.buckets;
                Int          hash      = hashEntry.hash;
                Int          bucketId  = hash % buckets.size;
                HashEntry?   currEntry = buckets[bucketId];
                HashEntry?   prevEntry = Null;

                loop: while (currEntry != Null)
                    {
                    // check if we found the entry that we're looking for
                    if (currEntry.hash == hash && hasher.areEqual(currEntry.key, hashEntry.key))
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

                            ++this.HasherMap.removeCount;
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
                this.HasherMap.remove(entry.key, entry.value);
                }

            return this;
            }

        @Override
        (EntrySet, Int) removeAll(function Boolean (Entry) shouldRemove)
            {
            Int          removed     = 0;
            HashEntry?[] buckets     = this.HasherMap.buckets;
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
                        ++this.HasherMap.removeCount;
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
        }

    // TODO GG remove "static"
    static mixin EntrySetFreezer<MapKey extends immutable Object, MapValue extends Shareable>
            into HasherMap<MapKey, MapValue>.EntrySet
            implements Freezable
        {
        @Override
        immutable EntrySetFreezer freeze(Boolean inPlace = False)
            {
            HasherMap<MapKey, MapValue> map = outer.as(HasherMap<MapKey, MapValue>);
            return map.is(immutable HasherMap) // TODO CP: is(immutable)
                    ? makeImmutable()
                    : map.freeze(inPlace).entries.makeImmutable().as(immutable EntrySetFreezer);
            }
        }


    // ----- Hashable functions --------------------------------------------------------------------

    @Override
    static <CompileType extends HasherMap> Int hashCode(CompileType value)
        {
        Int                     hash   = value.size;
        Hasher<CompileType.Key> hasher = value.hasher;
        for (CompileType.Key key : value)
            {
            hash ^= hasher.hashOf(key);
            }
        return hash;
        }

    @Override
    static <CompileType extends HasherMap> Boolean equals(CompileType value1, CompileType value2)
        {
        return Map.equals(value1, value2);
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
     * Check to see if the HasherMap needs to grow or shrink based on the current capacity need.
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
     * Check to see if the HasherMap needs to grow or shrink based on a planned capacity need.
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
     * Resize the HasherMap based on the assumed capacity need.
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
                HashEntry? next = entry.next;

                // move the entry to a new hash bucket
                Int newBucket = entry.hash % bucketCount;
                entry.next = newBuckets[newBucket];
                newBuckets[newBucket] = entry;

                entry = next;
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

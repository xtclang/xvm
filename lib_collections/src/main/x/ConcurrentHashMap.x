import ecstasy.iterators.CompoundIterator;

import ecstasy.collections.maps.EntryKeys;
import ecstasy.collections.maps.EntryValues;

import ecstasy.collections.Hasher;
import ecstasy.collections.HashMap;
import ecstasy.collections.ImmutableAble;
import ecstasy.collections.NaturalHasher;

import ecstasy.collections.maps.KeyEntry;

/**
 * A hash based map which allows for parallel and concurrent access with scalable performance.
 *
 * Parallelism is provided by partitioning the keys into a number of inner [HashMap] based partition.
 * Each partition can be independently accessed without contention.
 *
 * Concurrency is provided within a partition down to the key level, that is if an operation on one
 * key within a partition blocks it will not prevent reads or writes to other keys in the same
 * partition. Furthermore blocking writes such as by [#process] on a key will not block concurrent
 * reads of that same key. Writes to any given key are ordered.
 */
// TODO: GG, if this is a service rather then a const Maps.equals throws an IllegalArgument complaining
//       about a mutable being used for a service call, I don't see what that mutable is
const ConcurrentHashMap<Key extends immutable Object, Value extends ImmutableAble>
        implements Map<Key, Value>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a new [ConcurrentHashMap].
     *
     * @param parallelism  the target parallelism to optimize for
     * @param initCapacity  the number of expected entries
     */
    construct(Int parallelism = 16, Int initCapacity = 0)
        {
        assert(Key.is(Type<Hashable>));
        construct ConcurrentHashMap(new NaturalHasher<Key>(), parallelism, initCapacity);
        }

    /**
     * Construct a new [ConcurrentHashMap].
     *
     * @param parallelism  the target parallelism to optimize for
     * @param initCapacity  the number of expected entries
     */
    construct(Hasher<Key> hasher, Int parallelism = 16, Int initCapacity = 0)
        {
        assert parallelism > 0;
        assert initCapacity >= 0;
        this.hasher = hasher;

        // select a prime partition count greater then the requested concurrency
        Int partCount = parallelism;
        Int capacity = initCapacity / parallelism;
        Int buckets = Partition.calcBucketCount(capacity);
        if (parallelism == 1)
            {
            // user asked for it, allow it; there is still value here as compared to a simple
            // service wrapper around HashMap because we still offer key-level concurrency even it
            // not parallelism
            }
        else if (parallelism <= Partition.PRIMES[0])
            {
            // Explicitly avoid the first prime as our underlying HashMaps will have this as their
            // starting bucket count. See Partition.selectBucketCount for how this is resolved as
            // the HashMaps grow
            partCount = Partition.PRIMES[1];
            if (partCount == buckets)
                {
                partCount = Partition.PRIMES[2];
                }
            }
        else
            {
            for (Int p : Partition.PRIMES)
                {
                partCount = p;
                if (partCount >= parallelism && p != buckets)
                    {
                    break;
                    }
                }
            }

        partitions = new Array(partCount, i -> new Partition<Key, Value>(hasher, partCount, capacity));
        }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The partitions backing this map.
     */
    private Partition<Key, Value>[] partitions;

    /**
     * The key hasher.
     */
    private Hasher<Key> hasher;

    /**
     * The random number generator.
     * TODO: replace with something equivalent of Java's ThreadLocalRandom
     */
     @Inject Random rnd;


    // ----- Map interface -------------------------------------------------------------------------

    @Override
    @Lazy public/private Set<Key> keys.calc()
        {
        return new EntryKeys(this);
        }

    @Override
    @Lazy public/private Collection<Value> values.calc()
        {
        return new EntryValues(this);
        }

    @Override
    @Lazy public/private Collection<Map.Entry> entries.calc()
        {
        return new Entries();
        }

    @Override
    @RO Int size.get()
        {
        Int sum = 0;
        Int step = computeRandomStep();
        Int first = step % partitions.size;
        Int i = first;
        do
            {
            sum += partitions[i].size;
            i = (i + step) % partitions.size;
            }
        while (i != first);

        // TODO: MF return a future?
        return sum;
        }

    @Override
    @RO Boolean empty.get()
        {
        Int step = computeRandomStep();
        Int first = step % partitions.size;
        Int i = first;
        do
            {
            if (!partitions[i].empty)
                {
                return False;
                }

            i = (i + step) % partitions.size;
            }
        while (i != first);

        // TODO: MF return a future?
        return True;
        }

    @Override
    conditional Value get(Key key)
        {
        return partitionOf(key).get^(key);
        }

    @Override
    Boolean contains(Key key)
        {
        return partitionOf(key).contains^(key);
        }

    @Override
    ConcurrentHashMap put(Key key, Value value)
        {
        return partitionOf(key).putOrdered^(this, key, value);
        }

    @Override
    conditional ConcurrentHashMap putIfAbsent(Key key, Value value)
        {
        return partitionOf(key).putIfAbsentOrdered^(this, key, value);
        }

    @Override
    conditional ConcurrentHashMap replace(Key key, Value valueOld, Value valueNew)
        {
        return partitionOf(key).replaceOrdered^(this, key, valueOld, valueNew);
        }

    @Override
    ConcurrentHashMap remove(Key key)
        {
        return partitionOf(key).removeOrdered^(this, key);
        }

    @Override
    conditional ConcurrentHashMap remove(Key key, Value value)
        {
        return partitionOf(key).removeOrdered^(this, key, value);
        }

    @Override
    ConcurrentHashMap clear()
        {
        Int step = computeRandomStep();
        Int first = step % partitions.size;
        Int i = first;
        do
            {
            partitions[i].clearOrdered(this);
            i = (i + step) % partitions.size;
            }
        while (i != first);

        // TODO: MF return a future?
        return this;
        }

    @Override
    <Result> Result process(Key key, function Result(Map<Key, Value>.Entry) compute)
        {
        return partitionOf(key).process^(key, compute);
        }

    @Override
    <Result> conditional Result processIfPresent(Key key,
            function Result(Map<Key, Value>.Entry) compute)
        {
        return partitionOf(key).processIfPresent^(key, compute);
        }

    @Override
    (Value, Boolean) computeIfAbsent(Key key, function Value() compute)
        {
        return partitionOf(key).computeIfAbsent^(key, compute);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Compute the partition which owns the specified key.
     */
    protected Partition<Key, Value> partitionOf(Key key)
        {
        return partitions[hasher.hashOf(key) % partitions.size];
        }

    /**
     * Compute a random prime value to use perform a "random walk" over the partitions.
     * This approach helps to reduce contention when multiple services iterate ove the
     * map a the same time.
     *
     * @return the step
     */
    protected Int computeRandomStep()
        {
        Int step = Partition.PRIMES[rnd.int(Partition.PRIMES.size)];
        return step == partitions.size ? 1 : step;
        }


    // ----- Entries implementation ----------------------------------------------------------------

    /**
     * A collection of the map's entries, backed by the map.
     */
    protected const Entries
            implements Collection<Map.Entry>
        {
        @Override
        @RO Int size.get()
            {
            return this.ConcurrentHashMap.size;
            }

        @Override
        @RO Boolean empty.get()
            {
            return this.ConcurrentHashMap.empty;
            }

        @Override
        Boolean contains(Map.Entry entry)
            {
            if (Value value := this.ConcurrentHashMap.get(entry.key))
                {
                return value == entry.value;
                }

            // TODO: MF future?
            return False;
            }

        @Override
        Boolean containsAll(Collection!<Map.Entry> that)
            {
            for (Map.Entry entry : that)
                {
                if (!contains(entry))
                    {
                    return False;
                    }
                }

            // TODO: MF future?
            return True;
            }

        @Override
        @Op("+")
        Entries add(Map.Entry entry)
            {
            put(entry.key, entry.value);
            return this;
            }

        @Override
        @Op("+")
        Entries addAll(Iterable<Map.Entry> that)
            {
            for (Map.Entry entry : that)
                {
                put(entry.key, entry.value);
                }

            return this;
            }

        @Override
        Entries addAll(Iterator<Map.Entry> iter)
            {
            while (Map.Entry entry := iter.next())
                {
                put(entry.key, entry.value);
                }

            return this;
            }

        @Override
        conditional Entries addIfAbsent(Map.Entry entry)
            {
            return putIfAbsent(entry.key, entry.value) ? (True, this) : False;
            }

        @Override
        @Op("-")
        Entries remove(Map.Entry entry)
            {
            this.ConcurrentHashMap.remove(entry.key, entry.value);
            return this;
            }

        @Override
        conditional Entries removeIfPresent(Map.Entry entryThat)
            {
            return processIfPresent(entryThat.key, entry ->
                {
                if (entry.value == entryThat.value)
                    {
                    entry.delete();
                    }
                })
                ? (True, this) : False;
            }

        @Override
        Entries clear()
            {
            this.ConcurrentHashMap.clear();
            return this;
            }

        @Override
        Iterator<Map.Entry> iterator()
            {
            Partition[] partitions = this.ConcurrentHashMap.partitions;
            if (partitions.size == 1)
                {
                return partitions[0].entries.iterator();
                }

            Int step = computeRandomStep();
            Int first = step % partitions.size;
            Int second = (first + step) % partitions.size;

            GrowableCompoundIterator<Map.Entry> iter = new GrowableCompoundIterator(
                partitions[first].entries.iterator(), partitions[second].entries.iterator());

            for (Int i = (second + step) % partitions.size; i != first; i = (i + step) % partitions.size)
                {
                iter.add(partitions[i].entries.iterator());
                }

            return iter;
            }
        }


    // ----- GrowableCompoundIterator --------------------------------------------------------------

    /**
     * A [CompoundIterator] which supports adding iterators.
     */
    protected static class GrowableCompoundIterator<Element>
            extends CompoundIterator<Element>
        {
        construct (Iterator<Element> iter1, Iterator<Element> iter2)
            {
            construct CompoundIterator(iter1, iter2);
            }
        }

    // ----- Partition implementation --------------------------------------------------------------

    /**
     * A portion of the concurrent map's data.
     */
    protected static service Partition<Key extends immutable Object, Value extends ImmutableAble>
            extends HashMap<Key, Value>
        {
        // ----- constructors ----------------------------------------------------------------------

        construct(Hasher<Key> hasher, Int partitionCount, Int initCapacity)
            {
            this.partitionCount = partitionCount;
            construct HashMap(hasher, initCapacity);
            }


        // ----- properties ------------------------------------------------------------------------

        /**
         * The number of partitions in the [ConcurrentHashMap].
         */
        protected Int partitionCount;

        /**
         * A secondary map of pending operations, null up until the first call to process.
         */
        protected @Lazy HashMap<Key, FutureVar> pendingByKey.calc()
            {
            return new HashMap();
            }


        // ----- Partition methods -----------------------------------------------------------------

        /**
         * Return [true] iff there are pending operations outstanding for the key.
         *
         * @param key the key to check
         *
         * @return [true] iff the key is contended
         */
        protected Boolean isContended(Key key)
            {
            return &pendingByKey.assigned && pendingByKey.contains(key);
            }

        /**
         * Perform an ordered [put] operation.
         *
         * @param key the key
         * @param value the value
         *
         * @return this
         */
        @Concurrent
        protected void putOrdered(Key key, Value value)
            {
            if (isContended(key))
                {
                return process^(key, e -> {e.value = value;});
                }

            put(key, value);
            }

        @Concurrent
        protected <P> P putOrdered(P parent, Key key, Value value)
            {
            if (isContended(key))
                {
                @Future P futureParent;
                process^(key, e -> {e.value = value;}).
                    thenDo(() -> {futureParent = parent;});
                return futureParent;
                }

            put(key, value);
            return parent;
            }

        /**
         * Perform an ordered [putIfAbsent] operation.
         *
         * @param key the key
         * @param value the value
         *
         * @return this
         */
        @Concurrent
        protected <P> conditional P putIfAbsentOrdered(P parent, Key key, Value value)
            {
            if (isContended(key))
                {
                return process(key, e ->
                    {
                    if (e.exists)
                        {
                        return False;
                        }

                    e.value = value;
                    return True;
                    }), parent;
                }
            else
                {
                return putIfAbsent(key, value) ? (True, parent) : False;
                }
            }

        /**
         * Perform an ordered [replace] operation.
         *
         * @param key the key
         * @param valueOld the required old value
         * @param valueNew the new value
         *
         * @return this if the the replace occurred
         */
        @Concurrent
        protected <P> conditional P replaceOrdered(P parent, Key key, Value valueOld, Value valueNew)
            {
            if (isContended(key))
                {
                return process(key, e ->
                    {
                    if (e.exists && e.value == valueOld)
                        {
                        e.value = valueNew;
                        return True;
                        }

                    return False;
                    }), parent;
                }
            else
                {
                return replace(key, valueOld, valueNew) ? (True, parent) : False;
                }
            }

        /**
         * Perform an ordered [remove] operation.
         *
         * @param key the key
         *
         * @return this
         */
        @Concurrent
        protected <P> P removeOrdered(P parent, Key key)
            {
            if (isContended(key))
                {
                process(key, e -> {e.delete();});
                }
            else
                {
                remove(key);
                }

            return parent;
            }

        /**
         * Perform an ordered conditional [remove] operation.
         *
         * @param key the key
         * @param value the required old value
         *
         * @return this if the remove occurred
         */
        @Concurrent
        protected <P> conditional P removeOrdered(P parent, Key key, Value value)
            {
            if (isContended(key))
                {
                return process(key, e ->
                    {
                    if (e.exists && e.value == value)
                        {
                        e.delete();
                        return True;
                        }

                    return False;
                    }), parent;
                }
            else
                {
                return remove(key, value) ? (True, parent) : False;
                }
            }

        /**
         * Perform an ordered [clear] operation.
         *
         * @return this
         */
        @Concurrent
        protected <P> P clearOrdered(P parent)
            {
            if (&pendingByKey.assigned && !pendingByKey.empty)
                {
                // TODO: MF async section?
                for (Key key : keys)
                    {
                    removeOrdered(parent, key);
                    }
                }
            else
                {
                clear();
                }

            return parent;
            }


        // ----- HashMap methods -------------------------------------------------------------------

        @Override
        @Concurrent
        <Result> Result process(Key key, function Result (Map<Key, Value>.Entry) compute)
            {
            Entry entry = new @KeyEntry(key) Entry() {};
            @Future Result result;
            FutureVar<Result> rVar = &result;

            // ensure that when we complete if there are no more pending actions that
            // we clean our entry from the pending map
            rVar.thenDo(() -> pendingByKey.remove(key, rVar));

            if (FutureVar pending := pendingByKey.get(key))
                {
                // there are pending operations, add our action to the end of the list
                // TODO: GG it would be nice to have a callback when an @Concurrent frame yields
                // this would allow me to only do the extra bookkeeping when we actually yield
                pendingByKey.put(key, rVar);
                pending.thenDo(() -> {result = compute(entry);});
                }
            else
                {
                // no contention; register our action and run async
                pendingByKey.put(key, rVar);
                result = compute(entry);
                }

            return result;
            }

// TODO MF
//        @Override
//        <Result> conditional Result processIfPresent(Key key,
//                function Result(Map<Key, Value>.Entry) compute)
//            {
//            if (contains(key))
//                {
//                if (Result r : process(key, e ->
//                    {
//                    if (e.exists)
//                        {
//                        return (True, compute(e));
//                        }
//
//                    return False;
//                    }))
//                    {
//                    return (True, r);
//                    }
//                }
//            return False;
//            }

        @Override
        (Value, Boolean) computeIfAbsent(Key key, function Value() compute)
            {
            if (Value value := get(key))
                {
                return value, False;
                }

            return process(key, e ->
                {
                if (e.exists)
                    {
                    return e.value;
                    }

                e.value = compute();
                return e.value;
                }), True;
            }

        @Override
        protected (Int bucketCount, Int growAt, Int shrinkAt) selectBucketCount(Int capacity)
            {
            // ensure we never have the same partition and bucket count
            Int bucketCount;
            Int growAt;
            Int shrinkAt;
            Int max = PRIMES[PRIMES.size - 1];
            do
                {
                (bucketCount, growAt, shrinkAt) = super(capacity++);
                }
            while (bucketCount == partitionCount && bucketCount != max);

            if (bucketCount == max && bucketCount == partitionCount)
                {
                return super(--capacity);
                }

            return bucketCount, growAt, shrinkAt;
            }
        }
    }
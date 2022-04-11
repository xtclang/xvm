import ecstasy.iterators.CompoundIterator;

import ecstasy.collections.maps.EntryKeys;
import ecstasy.collections.maps.EntryValues;

import ecstasy.collections.Hasher;
import ecstasy.collections.HasherMap;

import ecstasy.collections.maps.KeyEntry;

/**
 * A hash based map which allows for parallel and concurrent access with scalable performance.
 *
 * Parallelism is provided by partitioning the keys into a number of inner [HasherMap] based
 * partitions. Each partition can be independently accessed without contention.
 *
 * Concurrency is provided within a partition down to the key level, such that if an operation on
 * one key within a partition blocks, it will not prevent reads or writes to other keys in the same
 * partition. Furthermore blocking writes such as by [process] on a key will not block concurrent
 * reads of that same key. Writes to any given key are ordered.
 */
const ConcurrentHasherMap<Key extends immutable Object, Value extends Shareable>
        implements Map<Key, Value>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a new [ConcurrentHasherMap].
     *
     * @param hasher        the [Hasher] to use
     * @param initCapacity  the number of expected entries
     * @param parallelism   the target parallelism to optimize for
     */
    construct(Hasher<Key> hasher, Int initCapacity = 0, Int parallelism = 16)
        {
        assert:arg parallelism > 0;
        assert:arg initCapacity >= 0;
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

        partitions = new Array(partCount, i -> new Partition<Key, Value>(hasher, capacity, partCount));
        }

    /**
     * Copy constructor from another [Map].
     *
     * @param hasher        the [Hasher] to use
     * @param that          another map to copy the contents from when constructing this
     *                      ConcurrentHasherMap
     * @param parallelism   the target parallelism to optimize for
     */
    construct(Hasher<Key> hasher, Map<Key, Value> that, Int parallelism = 16)
        {
        if (that.is(ConcurrentHasherMap) && hasher == that.hasher)
            {
            // optimization:
            construct ConcurrentHasherMap(that, parallelism);
            }
        else
            {
            construct ConcurrentHasherMap(hasher, that.size, parallelism);
            }
        }
    finally // REVIEW GG+CP - wouldn't it be nice if "finally" could capture from the "construct" block?
        {
        if (!(that.is(ConcurrentHasherMap) && hasher == that.hasher))
            {
            putAll(that);
            }
        }

    /**
     * Duplicable constructor.
     *
     * @param that          another ConcurrentHasherMap to copy the contents from when constructing
     *                      this ConcurrentHasherMap
     * @param parallelism   the target parallelism to optimize for
     */
    construct(ConcurrentHasherMap<Key, Value> that, Int parallelism = 16)
        {
        this.hasher     = that.hasher;
        this.partitions = TODO copy the partitions
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The key hasher.
     */
    public/private Hasher<Key> hasher;

    /**
     * The partitions backing this map.
     */
    private Partition<Key, Value>[] partitions;

    /**
     * The random number generator.
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
    @Lazy public/private Collection<Map<Key,Value>.Entry> entries.calc()
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

        // TODO MF: return a future?
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

        // TODO MF: return a future?
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
    ConcurrentHasherMap put(Key key, Value value)
        {
        return partitionOf(key).putOrdered^(this, key, value);
        }

    @Override
    conditional ConcurrentHasherMap putIfAbsent(Key key, Value value)
        {
        return partitionOf(key).putIfAbsentOrdered^(this, key, value);
        }

    @Override
    conditional ConcurrentHasherMap replace(Key key, Value valueOld, Value valueNew)
        {
        return partitionOf(key).replaceOrdered^(this, key, valueOld, valueNew);
        }

    @Override
    ConcurrentHasherMap remove(Key key)
        {
        return partitionOf(key).removeOrdered^(this, key);
        }

    @Override
    conditional ConcurrentHasherMap remove(Key key, Value value)
        {
        return partitionOf(key).removeOrdered^(this, key, value);
        }

    @Override
    ConcurrentHasherMap clear()
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

        // TODO MF: return a future?
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
            implements Collection<Map<Key,Value>.Entry>
        {
        @Override
        @RO Int size.get()
            {
            return this.ConcurrentHasherMap.size;
            }

        @Override
        @RO Boolean empty.get()
            {
            return this.ConcurrentHasherMap.empty;
            }

        @Override
        Boolean contains(Element entry)
            {
            if (Value value := this.ConcurrentHasherMap.get(entry.key))
                {
                return value == entry.value;
                }

            // TODO MF: future?
            return False;
            }

        @Override
        Boolean containsAll(Collection<Element> that)
            {
            for (val entry : that)
                {
                if (!contains(entry))
                    {
                    return False;
                    }
                }

            // TODO MF: future?
            return True;
            }

        @Override
        @Op("+")
        Entries add(Element entry)
            {
            put(entry.key, entry.value);
            return this;
            }

        @Override
        @Op("+")
        Entries addAll(Iterable<Element> that)
            {
            for (Element entry : that)
                {
                put(entry.key, entry.value);
                }

            return this;
            }

        @Override
        Entries addAll(Iterator<Element> iter)
            {
            while (val entry := iter.next())
                {
                put(entry.key, entry.value);
                }

            return this;
            }

        @Override
        conditional Entries addIfAbsent(Element entry)
            {
            return putIfAbsent(entry.key, entry.value) ? (True, this) : False;
            }

        @Override
        @Op("-")
        Entries remove(Element entry)
            {
            this.ConcurrentHasherMap.remove(entry.key, entry.value);
            return this;
            }

        @Override
        conditional Entries removeIfPresent(Element entryThat)
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
            this.ConcurrentHasherMap.clear();
            return this;
            }

        @Override
        Iterator<Map<Key, Value>.Entry> iterator()
            {
            Partition<Key,Value>[] partitions = this.ConcurrentHasherMap.partitions;
            if (partitions.size == 1)
                {
                return partitions[0].entries.iterator();
                }

            Int step = computeRandomStep();
            Int first = step % partitions.size;
            Int second = (first + step) % partitions.size;

            GrowableCompoundIterator<Map<Key, Value>.Entry> iter = new GrowableCompoundIterator(
                    partitions[first].entries.iterator(),
                    partitions[second].entries.iterator());

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
            super(iter1, iter2);
            }
        }


    // ----- Partition implementation --------------------------------------------------------------

    /**
     * A portion of the concurrent map's data.
     */
    protected static service Partition<Key extends immutable Object, Value extends Shareable>
            extends HasherMap<Key, Value>
        {
        // ----- constructors ----------------------------------------------------------------------

        /**
         * [HasherReplicable] virtual constructor: Construct the Partition with the specified hasher
         * and (optional) initial capacity.
         *
         * @param hasher          the [Hasher] to use
         * @param initCapacity    the number of expected entries
         * @param partitionCount  the number of partitions (must be specified)
         */
        construct(Hasher<Key> hasher, Int initCapacity = 0, Int partitionCount = 0)
            {
            assert partitionCount > 0 as "Partition count must be specified";

            this.partitionCount = partitionCount;
            super(hasher, initCapacity);
            }

        @Override
        construct(Partition that)
            {
            this.partitionCount = that.partitionCount;
            super(that);
            }


        // ----- properties ------------------------------------------------------------------------

        /**
         * The number of partitions in the [ConcurrentHasherMap].
         */
        public/private Int partitionCount;

        /**
         * A secondary map of pending operations, null up until the first call to process.
         */
        protected/private @Lazy HasherMap<Key, FutureVar> pendingByKey.calc()
            {
            return new HasherMap(hasher);
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
                // TODO MF: async section?
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


        // ----- HasherMap methods -------------------------------------------------------------------

        @Override
        @Concurrent
        <Result> Result process(Key key, function Result (Map<Key, Value>.Entry) compute)
            {
            Entry entry = new @KeyEntry(key) Entry() {};
            @Future Result result;
            FutureVar<Result> rVar = &result;

            // ensure that when we complete if there are no more pending actions that
            // we clean our entry from the pending map
            rVar.whenComplete((_, _) -> pendingByKey.remove(key, rVar));

            if (FutureVar pending := pendingByKey.get(key))
                {
                // there are pending operations, add our action to the end of the list
                // TODO GG: it would be nice to have a callback when an @Concurrent frame yields;
                // this would allow me to only do the extra bookkeeping when we actually yield
                pendingByKey.put(key, rVar);
                pending.whenComplete((_, _) ->
                    {
                    try
                        {
                        result = compute(entry);
                        }
                    catch (Exception e)
                        {
                        &result.completeExceptionally(e);
                        }
                    });
                }
            else
                {
                // no contention; register our action and run async
                pendingByKey.put(key, rVar);
                result = compute(entry);
                }

            return result;
            }

        @Override
        @Concurrent
        <Result> conditional Result processIfPresent(Key key,
                function Result(Map<Key, Value>.Entry) compute)
            {
            if (contains(key))
                {
                enum Exist {Not}
                Result|Exist result = process(key, e -> e.exists ? compute(e) : Exist.Not);
                return result.is(Exist) ? False : (True, result);
                }
            return False;
            }

        @Override
        @Concurrent
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
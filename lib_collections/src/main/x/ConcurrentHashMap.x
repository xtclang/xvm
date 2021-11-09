import ecstasy.iterators.CompoundIterator;

import ecstasy.collections.maps.EntryKeys;
import ecstasy.collections.maps.EntryValues;

import ecstasy.collections.Hasher;
import ecstasy.collections.HashMap;
import ecstasy.collections.ImmutableAble;
import ecstasy.collections.NaturalHasher;

/**
 * A hash based map which allows for concurrent access.
 *
 * As compared to [HashMap] this allows multiple services to access the map in concurrently.
 * Note that this is not lock-free and two keys which hash to the same "partition" will still
 * contend with one another.
 */
const ConcurrentHashMap<Key extends immutable Object, Value extends ImmutableAble>
        implements Map<Key, Value>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a new [ConcurrentHashMap] with default concurrency.
     *
     * @param concurrency the number of services which may concurrent access the map
     */
    construct(Int concurrency = 16)
        {
        assert(Key.is(Type<Hashable>));
        construct ConcurrentHashMap(new NaturalHasher<Key>(), concurrency);
        }

    /**
     * Construct a new [ConcurrentHashMap] with a suggested concurrency.
     *
     * @param concurrency an indicator of how many services may concurrently access this map
     */
    construct(Hasher<Key> hasher, Int concurrency = 16)
        {
        assert concurrency >= 0;
        this.hasher = hasher;

        // select a prime partition count greater then the requested concurrency
        Int c = concurrency;
        if (concurrency <= Partition.PRIMES[0])
            {
            // Explicitly avoid the first prime as our underlying HashMaps will have this as their
            // starting bucket count. See Partition.selectBucketCount for how this is resolved as
            // the HashMaps grow
            c = Partition.PRIMES[1];
            }
        else
            {
            for (Int p : Partition.PRIMES)
                {
                if (p >= concurrency)
                    {
                    c = p;
                    break;
                    }
                }
            }

        partitions = new Array(c, i -> new Partition<Key, Value>(hasher, c));
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

        return True;
        }

    @Override
    conditional Value get(Key key)
        {
        return partitionOf(key).get(key);
        }

    @Override
    Boolean contains(Key key)
        {
        return partitionOf(key).contains(key);
        }

    @Override
    ConcurrentHashMap put(Key key, Value value)
        {
        partitionOf(key).put(key, value);
        return this;
        }

    @Override
    conditional ConcurrentHashMap putIfAbsent(Key key, Value value)
        {
        return partitionOf(key).putIfAbsent(key, value) ? (True, this) : False;
        }

    @Override
    conditional ConcurrentHashMap replace(Key key, Value valueOld, Value valueNew)
        {
        return partitionOf(key).replace(key, valueOld, valueNew) ? (True, this) : False;
        }

    @Override
    ConcurrentHashMap remove(Key key)
        {
        partitionOf(key).remove(key);
        return this;
        }

    @Override
    conditional ConcurrentHashMap remove(Key key, Value value)
        {
        return partitionOf(key).remove(key, value) ? (True, this) : False;
        }

    @Override
    ConcurrentHashMap clear()
        {
        Int step = computeRandomStep();
        Int first = step % partitions.size;
        Int i = first;
        do
            {
            partitions[i].clear();
            i = (i + step) % partitions.size;
            }
        while (i != first);

        return this;
        }

    @Override
    public
    <Result> Result process(Key key, function Result(Entry) compute)
        {
        return partitionOf(key).process(key, compute);
        }

    @Override
    <Result> conditional Result processIfPresent(Key key, function Result(Entry) compute)
        {
        return partitionOf(key).processIfPresent(key, compute);
        }

    @Override
    (Value, Boolean) computeIfAbsent(Key key, function Value() compute)
        {
        return partitionOf(key).computeIfAbsent(key, compute);
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
        Boolean contains(Element entry)
            {
            if (Value value := this.ConcurrentHashMap.get(entry.key))
                {
                return value == entry.value;
                }

            return False;
            }

        @Override
        Boolean containsAll(Collection!<Element> that)
            {
            for (Map.Entry entry : that)
                {
                if (!contains(entry))
                    {
                    return False;
                    }
                }

            return True;
            }

        @Override
        @Op("+")
        Entries add(Element entry)
            {
            this.ConcurrentHashMap.put(entry.key, entry.value);
            return this;
            }

        @Override
        @Op("+")
        Entries addAll(Iterable<Element> that)
            {
            for (Map.Entry entry : that)
                {
                this.ConcurrentHashMap.put(entry.key, entry.value);
                }

            return this;
            }

        @Override
        Entries addAll(Iterator<Element> iter)
            {
            while (Map.Entry entry := iter.next())
                {
                this.ConcurrentHashMap.put(entry.key, entry.value);
                }

            return this;
            }

        @Override
        conditional Entries addIfAbsent(Element entry)
            {
            return this.ConcurrentHashMap.putIfAbsent(entry.key, entry.value) ? (True, this) : False;
            }

        @Override
        @Op("-")
        Entries remove(Element entry)
            {
            this.ConcurrentHashMap.remove(entry.key, entry.value);
            return this;
            }

        @Override
        conditional Entries removeIfPresent(Element entryThat)
            {
            return this.ConcurrentHashMap.processIfPresent(entryThat.key, entry ->
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

        construct(Hasher<Key> hasher, Int partitionCount)
            {
            this.partitionCount = partitionCount;
            construct HashMap(hasher, 0);
            }


        // ----- properties ------------------------------------------------------------------------

        /**
         * The number of partitions in the [ConcurrentHashMap].
         */
        protected Int partitionCount;


        // ----- HashMap methods -------------------------------------------------------------------

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
import ecstasy.collections.Hasher;


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
const ConcurrentHashMap<Key extends immutable Hashable, Value extends Shareable>
        extends ConcurrentHasherMap<Key, Value>
        implements Replicable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the HashMap with the specified hasher and (optional) initial capacity.
     *
     * @param initCapacity  the number of expected entries
     * @param parallelism   the target parallelism to optimize for
     */
    construct(Int initCapacity = 0, Int parallelism = 16)
        {
        assert Hasher<Key> hasher := Key.hashed();
        super(hasher, initCapacity, parallelism);
        }

    /**
     * Copy constructor.
     *
     * @param that          another map to copy the contents from when constructing this
     *                      ConcurrentHashMap
     * @param parallelism   the target parallelism to optimize for
     */
    construct(Map<Key, Value> that, Int parallelism = 16)
        {
        assert Hasher<Key> hasher := Key.hashed();
        super(hasher, that, parallelism);
        }

    /**
     * Duplicable constructor.
     *
     * @param that          the ConcurrentHashMap to duplicate
     * @param parallelism   the target parallelism to optimize for
     */
    construct(ConcurrentHashMap<Key, Value> that, Int parallelism = 16)
        {
        super(that, parallelism);
        }
    }

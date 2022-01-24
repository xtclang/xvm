import maps.EntryKeys;
import maps.EntryValues;
import maps.KeyEntry;


/**
 * HashMap is a hashed implementation of the Map interface. It uses the natural [Hasher] of the
 * `Key` type for hashing and comparing keys.
 */
class HashMap<Key extends Hashable, Value>
        extends HasherMap<Key, Value>
        implements Replicable
        incorporates CopyableMap.ReplicableCopier<Key, Value>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the HashMap with the specified hasher and (optional) initial capacity.
     *
     * @param initCapacity  the number of expected entries
     */
    construct(Int initCapacity = 0)
        {
        assert Hasher<Key> hasher := Key.hashed() as $"Type \"{Key}\" doesn't have a natural hasher";
        construct HashMap(hasher, initCapacity);
        }

    /**
     * Copy constructor.
     *
     * @param that  another map to copy the contents from when constructing this HashMap
     */
    construct(Map<Key, Value> that)
        {
        assert Hasher<Key> hasher := Key.hashed();
        super(hasher, that);
        }

    /**
     * Duplicable constructor.
     *
     * @param that  the HashMap to duplicate
     */
    construct(HashMap<Key, Value> that)
        {
        super(that);
        }

    /**
     * [HasherMap] virtual constructor: Construct the HashMap with the specified hasher and
     * (optional) initial capacity.
     *
     * @param hasher        the [Hasher] to use
     * @param initCapacity  the number of expected entries
     */
    construct(Hasher<Key> hasher, Int initCapacity = 0)
        {
        super(hasher, initCapacity);
        }
    }

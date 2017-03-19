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
    // ----- constructors --------------------------------------------------------------------------

    construct ExtHashMap(Hasher<KeyType> hasher, Int initCapacity = 0)
        {
        this.hasher = hasher;
        buckets = new Entry<KeyType, ValueType>?[calcBucketCount(initCapacity)];
        }

    // ----- internal state ------------------------------------------------------------------------

    /**
     * The Hasher is the thing that knows how to take a key and provide a hash code, or compare two
     * keys for equality -- even if the keys don't know how to do that themselves.
     */
    protected/private Hasher<KeyType> hasher;

    /**
     * The size of the map is maintained internally.
     */
    public/private Int size = 0;

    /**
     * An array of hash buckets.
     */
    private Entry<KeyType, ValueType>?[] buckets;

    /**
     * The size at which the capacity must grow.
     */
    private Int threshold = 0;

    // ----- Map interface -------------------------------------------------------------------------

    @Override
    conditional Entry<KeyType, ValueType> getEntry(KeyType key)
        {
        }

    @lazy public/private Set<Entry<KeyType, ValueType>> entries.calc()
        {
        }

    @lazy public/private Set<KeyType> keys.calc()
        {
        }

    @lazy public/private Collection<ValueType> values.calc()
        {
        }

    // ----- UniformedIndex interface --------------------------------------------------------------

    // ----- helpers -------------------------------------------------------------------------------

    protected static Int calcBucketCount(Int capacity)
        {
        assert capacity >= 0;
        // TODO should be a prime number
        return (capacity.maxOf(1) + (capacity >>> 2)).maxOf(7);
        }
    }

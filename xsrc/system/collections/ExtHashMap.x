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
    @ro Int size;

    conditional ValueType get(KeyType key)
        {
        if (Entry entry = getEntry(key)
            {
            return true, entry.value;
            }

        return false;
        }

    HashMap<KeyType, ValueType> put(KeyType key, ValueType value)
        {

        }

    // ----- UniformedIndex ---

    /**
     * Obtain the value of the specified element.
     */
    @op ElementType get(IndexType index);

    /**
     * Modify the value in the specified element.
     */
    @op Void set(IndexType index, ElementType value)
        {
        throw new ReadOnlyException();
        }

    /**
     * Obtain a Ref for the specified element.
     */
    @op Ref<ElementType> elementAt(IndexType index)


    // ...
    }

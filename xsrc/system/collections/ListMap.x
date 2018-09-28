/**
 * ListMap is an implementation of a Map on top of an Array to maintain the order of
 * insertion.
 */
class ListMap<KeyType extends immutable Hashable, ValueType>
         implements Map<KeyType, ValueType>
    {
    private ListEntry<KeyType, ValueType>[] array;

    // ----- constructors --------------------------------------------------------------------------

    construct(Int initCapacity = 0)
        {
        array = new Array(initCapacity);
        }

    @Override
    Int size.get()
        {
        return array.size;
        }

    @Override
    conditional Entry<KeyType, ValueType> getEntry(KeyType key)
        {
        if (Int index : indexOf(key))
            {
            return true, array[index];
            }
        return false;
        }

    @Override
    @RO Set<KeyType> keys.get()
        {
        TODO
        }

    @Override
    @RO Set<Entry<KeyType, ValueType>> entries.get()
        {
        TODO
        }

    @Override
    @RO Collection<ValueType> values.get()
        {
        TODO
        }

    @Override
    ListMap<KeyType, ValueType> put(KeyType key, ValueType value)
        {
        if (Int index : indexOf(key))
            {
            array[index].value = value;
            }
        else
            {
            array = array.addElement(new ListEntry(key, value));
            }
        return this;
        }

    @Override
    ListMap<KeyType, ValueType> remove(KeyType key)
        {
        TODO
        }

    @Override
    ListMap<KeyType, ValueType> clear()
        {
        array = new Array(1);
        return this;
        }

    @Override
    <ResultType> ResultType process(KeyType key,
            function ResultType (ProcessableEntry<KeyType, ValueType>) compute)
        {
        TODO
        }

    // ----- helper methods ------------------------------------------------------------------------

    protected conditional Int indexOf(KeyType key)
        {
        AllEntries:
        for (ListEntry<KeyType, ValueType> entry : array)
            {
            if (entry[0] == key)
                {
                return true, AllEntries.count;
                }
            }
        return false;
        }

    /**
     * The Entry implementation used to store the ListMap's keys and values.
     */
    protected static class ListEntry<KeyType, ValueType>
            implements Entry<KeyType, ValueType>
        {
        construct(KeyType key, ValueType value)
            {
            this.key   = key;
            this.value = value;
            }

        @Override
        public/private KeyType key;

        @Override
        public ValueType value;
        }
    }

/**
 * ListMap is an implementation of a Map on top of an Array to maintain the order of
 * insertion.
 */
class ListMap<KeyType extends immutable Hashable, ValueType>
         implements Map<KeyType, ValueType>
    {
    private Entry<KeyType, ValueType>[] array;

    // ----- constructors --------------------------------------------------------------------------

    construct(Int initCapacity = 0)
        {
        array = new Array(initCapacity);
        }

    // ----- Map properties and methods ------------------------------------------------------------

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
            array = array.addElement(new SimpleEntry(key, value));
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
        for (Entry<KeyType, ValueType> entry : array)
            {
            if (entry[0] == key)
                {
                return true, AllEntries.count;
                }
            }
        return false;
        }
    }

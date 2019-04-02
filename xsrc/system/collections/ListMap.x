/**
 * ListMap is an implementation of a Map on top of an Array to maintain the order of
 * insertion.
 */
class ListMap<KeyType extends immutable Hashable, ValueType>
         extends IterableKeysMap<KeyType, ValueType>
    {
    private Entry[] array;

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
    conditional Entry find(KeyType key)
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
    @RO Set<Entry> entries.get()
        {
        TODO
        }

    @Override
    @RO Collection<ValueType> values.get()
        {
        TODO
        }

    @Override
    ListMap put(KeyType key, ValueType value)
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
    ListMap remove(KeyType key)
        {
        TODO
        }

    @Override
    ListMap clear()
        {
        array = new Array(1);
        return this;
        }

    @Override
    <ResultType> ResultType process(KeyType key,
            function ResultType (Entry) compute)
        {
        TODO
        }

    // ----- helper methods ------------------------------------------------------------------------

    protected conditional Int indexOf(KeyType key)
        {
        AllEntries:
        for (Entry entry : array)
            {
            if (entry.key == key)
                {
                return true, AllEntries.count;
                }
            }
        return false;
        }
    }

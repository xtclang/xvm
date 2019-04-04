/**
 * ListMap is an implementation of a Map on top of an Array to maintain the order of insertion.
 */
class ListMap<KeyType extends immutable Hashable, ValueType>
         implements Map<KeyType, ValueType>
         implements MutableAble, FixedSizeAble, PersistentAble, ConstAble
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Int initCapacity = 0)
        {
        listKeys = new Array(initCapacity);
        listVals = new Array(initCapacity);
        }
        

    // ----- internal ------------------------------------------------------------------------------

    private KeyType[]   listKeys;
    private ValueType[] listVals;

    protected conditional Int indexOf(KeyType key)
        {
        AllKeys:
        for (KeyType eachKey : listKeys)
            {
            if (eachKey == key)
                {
                return True, AllKeys.count;
                }
            }
        return False;
        }


    // ----- Map properties and methods ------------------------------------------------------------

    @Override
    public/private MutabilityConstraint mutability;

    @Override
    Int size.get()
        {
        return listKeys.size;
        }

    @Override
    Boolean contains(KeyType key)
        {
        return indexOf(key);
        }

    @Override
    conditional ValueType get(KeyType key)
        {
        if (Int index : indexOf(key))
            {
            return True, listVals[index];
            }
        return False;
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
    conditional ListMap put(KeyType key, ValueType value)
        {
        if (Int index : indexOf(key))
            {
            if (listVals[index] == value)
                {
                return False;
                }

            listVals[index] = value;
            }
        else
            {
            listKeys.addElement(key);
            listVals.addElement(value);
            }

        return True, this;
        }

    @Override
    conditional ListMap remove(KeyType key)
        {
        TODO
        }

    @Override
    conditional ListMap clear()
        {
        if (empty)
            {
            return False;
            }

        listKeys = new Array(1);
        listVals = new Array(1);
        return True, this;
        }

    @Override
    <ResultType> ResultType process(KeyType key, function ResultType (Map<KeyType, ValueType>.Entry) compute)
        {
        TODO
        }
        
    @Override
    ListMap ensureMutable()
        {
        TODO
        }

    @Override
    ListMap ensureFixedSize(Boolean inPlace = False)
        {
        TODO
        }

    @Override
    ListMap ensurePersistent(Boolean inPlace = False)
        {
        TODO
        }

    @Override
    immutable ListMap ensureConst(Boolean inPlace = False)
        {
        TODO
        }
    }

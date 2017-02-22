const NaturalHasher<ValueType extends Hashable>
    {
    Int hashOf(ValueType value)
        {
        return value.hash;
        }
    
    Boolean areEqual(ValueType value1, ValueType value2)
        {
        return value1 == value2;
        }
    }
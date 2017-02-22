interface Hasher<ValueType>
    {
    Int hashOf(ValueType value);
    
    Boolean areEqual(ValueType value1, ValueType value2);
    }
/**
 * A natural hasher uses the object's own hash code and the relational equality operator associated
 * with the object's type to determine equality.
 *
 * The purpose of the NaturalHasher class is to allow an implementation of a hashed data structure
 * to easily switch between working with objects that provide their own hashing and equality, and
 * with objects whose hashing and equality is known (or overloaded) by a separate implementation.
 */
const NaturalHasher<ValueType extends Hashable>
        implements Hasher<ValueType>
    {
    @Override
    Int hashOf(ValueType value)
        {
        return ValueType.hashCode(value);
        }

    @Override
    Boolean areEqual(ValueType value1, ValueType value2)
        {
        return ValueType.equals(value1, value2);
        }
    }
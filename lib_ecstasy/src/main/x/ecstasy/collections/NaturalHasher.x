/**
 * A natural hasher uses the object's own hash code and the relational equality operator associated
 * with the object's type to determine equality.
 *
 * The purpose of the NaturalHasher class is to allow an implementation of a hashed data structure
 * to easily switch between working with objects that provide their own hashing and equality, and
 * with objects whose hashing and equality is known (or overloaded) by a separate implementation.
 */
const NaturalHasher<Value extends Hashable>
        implements Hasher<Value>
    {
    @Override
    Int64 hashOf(Value value)
        {
        return Value.hashCode(value);
        }

    @Override
    Boolean areEqual(Value value1, Value value2)
        {
        return Value.equals(value1, value2);
        }
    }
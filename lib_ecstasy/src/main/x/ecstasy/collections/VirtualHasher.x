/**
 * A VirtualHasher is similar to the [NaturalHasher] except it uses the object's own hash code and
 * the relational equality operator associated with the object's **actual type** rather than the
 * `Value` type to determine equality.
 *
 * The purpose of the VirtualHasher class is to allow an implementation of a hashed data structure
 * that allows keys from arbitrary subclasses to coexist.
 */
const VirtualHasher<Value extends Hashable>
        implements Hasher<Value> {
    @Override
    Int64 hashOf(Value value) {
        Type actualType = &value.actualType;

        return virtualHash(value.as(actualType.DataType));

        <ActualType extends Hashable> Int64 virtualHash(ActualType value) {
            return ActualType.hashCode(value);
        }
    }

    @Override
    Boolean areEqual(Value value1, Value value2) {
        return value1.equals(value2);
    }
}
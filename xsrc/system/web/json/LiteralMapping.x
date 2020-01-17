/**
 * A "straight-through" [Mapping] implementation for Ecstasy types that have exact JSON analogues.
 */
const LiteralMapping<Serializable extends Doc>
        implements Mapping<Serializable>
    {
    @Override
    <ObjectType extends Serializable> ObjectType read<ObjectType>(ElementInput in)
        {
        Doc value = in.readDoc();
        if (value.is(ObjectType))
            {
            return value;
            }

        throw new IllegalJSON($"Type implementation={Serializable}; expected={ObjectType}; actual={&value.actualType}");
        }

    @Override
    <ObjectType extends Serializable> void write(ElementOutput out, ObjectType value)
        {
        out.add(value);
        }
    }

/**
 * A "straight-through" [Mapping] implementation for Ecstasy types that have exact JSON analogues.
 */
const LiteralMapping<Serializable extends Doc>
        implements Mapping<Serializable>
    {
    @Override
    Serializable read(ElementInput in)
        {
        Doc value = in.readDoc();
        if (value.is(Serializable))
            {
            return value;
            }

        throw new IllegalJSON($"Type expected={Serializable}; actual={&value.actualType}");
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value);
        }
    }

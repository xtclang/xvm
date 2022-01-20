/**
 * A "straight-through" [Mapping] implementation for Ecstasy types that have exact JSON analogues.
 */
const LiteralMapping<Serializable extends Doc>
        implements Mapping<Serializable>
    {
    @Override
    String typeName.get()
        {
        return Serializable.toString();
        }

    @Override
    Serializable read(ElementInput in)
        {
        Doc value = in.readDoc();
        if (value.is(Serializable))
            {
            return value;
            }

        if (value.is(IntLiteral) && Serializable.is(Type<FPLiteral>))
            {
            return value.toFPLiteral().as(Serializable);
            }

        throw new IllegalJSON($"Type expected={Serializable}; actual={&value.actualType}");
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value);
        }
    }

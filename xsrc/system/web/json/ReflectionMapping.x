/**
 * A reflection-based [Mapping] implementation.
 */
const ReflectionMapping<Serializable>
        implements Mapping<Serializable>
    {
    construct()
        {
        TODO
        }

    @Override
    Serializable read(ElementInput in)
        {
        TODO
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        if (value.is(Nullable))
            {
            out.add(Null);
            return;
            }

        Type type = &value.actualType;
        if (type.is(Type<Number>))
            {
            TODO
            }
        else if (type.is(Type<Boolean>))
            {
            TODO
            }
        else
            {
            TODO
            }

        // TODO
        }
    }

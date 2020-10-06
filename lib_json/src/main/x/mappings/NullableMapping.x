/**
 * A mapping for Nullable values.
 */
const NullableMapping<NotNullable>(Mapping<NotNullable> underlying)
        implements Mapping<NotNullable?>
    {
    assert()
        {
        assert !underlying.is(NullableMapping);
        }

    @Override
    String typeName.get()
        {
        return underlying.typeName + '?';
        }

    @Override
    Serializable read(ElementInput in)
        {
        return in.isNull()
                ? Null
//TODO GG       : underlying.read(in);
                : underlying.read(in).as(Serializable);
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        if (value == Null)
            {
            out.add(Null);
            }
        else
            {
// TODO GG  underlying.write(out, value);
            underlying.write(out, value.as(NotNullable));
            }
        }
    }

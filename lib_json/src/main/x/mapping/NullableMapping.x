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
                : underlying.read(in);
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
            underlying.write(out, value);
            }
        }
    }

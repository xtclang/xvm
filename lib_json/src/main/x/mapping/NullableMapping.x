/**
 * A mapping for Nullable values.
 */
const NullableMapping<NotNullable>(Mapping<NotNullable> underlying)
        implements Mapping<Nullable | NotNullable>
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

    @Override
    <SubType extends Serializable> conditional Mapping<SubType> narrow(Schema schema, Type<SubType> type)
        {
        // TODO GG: if (type.is(Type<Nullable>) && type.form == Intersection,
        if (type.is(Type<Nullable>) && type.as(Type).form == Intersection,
                (Type left, Type right) := type.relational(),
                left == Nullable,
                right != underlying.Serializable,
                Mapping<NotNullable> narrowedUnderlying := underlying.narrow(schema, right.as(Type<NotNullable>)))
            {
            // TODO GG: Mapping<right.Serializable> narrowedNullable = new NullableMapping<right.Serializable>(narrowedUnderling);
            val narrowedNullable = new NullableMapping<NotNullable>(narrowedUnderlying);

            return True, narrowedNullable.as(Mapping<SubType>);
            }

        return False;
        }
    }

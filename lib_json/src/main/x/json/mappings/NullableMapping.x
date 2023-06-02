/**
 * A mapping for Nullable values.
 */
const NullableMapping<Serializable>(Mapping<NonNullable> underlying)
        implements Mapping<Serializable> {

    typedef (Serializable-Nullable) as NonNullable;

    construct(Mapping<NonNullable> underlying) {
        assert !underlying.is(NullableMapping);
        assert !Null.is(underlying.Serializable);

        this.underlying = underlying;
        this.typeName   = underlying.typeName + '?';
    }

    @Override
    Serializable read(ElementInput in) {
        return in.isNull()
                ? Null.as(Serializable)
                : underlying.read(in);
    }

    @Override
    void write(ElementOutput out, Serializable value) {
        if (value == Null) {
            out.add(Null);
        } else {
            underlying.write(out, value.as(NonNullable));
        }
    }

    @Override
    <SubType extends Serializable> conditional Mapping<SubType> narrow(Schema schema, Type<SubType> type) {
        if (type.form == Union,
                (Type left, Type right) := type.relational(),
                left == Nullable, right != NonNullable,
                val narrowedUnderlying := underlying.narrow(schema, right.as(Type<NonNullable>))) {
            return True, new NullableMapping(narrowedUnderlying.as(Mapping<right.DataType>))
                    .as(Mapping<SubType>);
        }
        return False;
    }
}
/**
 * A mapping for immutable values.
 */
const ImmutableMapping<Serializable>(Mapping<Mutable> underlying)
        implements Mapping<Serializable>
    {
    typedef (Serializable-immutable) as Mutable;

    /**
     * Construct the ImmutableMapping.
     *
     * @param underlying  the mapping to use for the underlying (non-immutable) type
     */
    construct(Mapping<Mutable> underlying)
        {
        assert !underlying.Serializable.is(Type<immutable>);

        this.underlying = underlying;
        this.typeName   = $"immutable {underlying.typeName}";
        }

    @Override
    Serializable read(ElementInput in)
        {
        Mutable value = in.readUsing(underlying);
        return value.is(Freezable)
                ? value.freeze(inPlace=True)
                : value.makeImmutable().as(Serializable); // TODO GG: as() should not be necessary
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.addUsing(underlying, value.as(Mutable));
        }
    }
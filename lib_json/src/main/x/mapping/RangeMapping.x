import Type.Constructor;

/**
 * A mapping for Range values.
 */
const RangeMapping<Element extends Orderable>(Mapping<Element> underlying)
        implements Mapping<Range<Element>>
    {
    /**
     * Construct the RangeMapping.
     *
     * @param underlying  the mapping to use for the elements of the `Range`
     */
    construct(Mapping<Element> underlying)
        {
        this.underlying = underlying;
        this.typeName   = $"Range<{underlying.typeName}>";
        }

    @Override
    Serializable read(ElementInput in)
        {
        using (val obj = in.openObject())
            {
            Element first          = underlying.read(obj.openField("first"));
            Boolean firstExclusive = obj.readBoolean("firstExclusive", False);
            Element last           = underlying.read(obj.openField("last"));
            Boolean lastExclusive  = obj.readBoolean("lastExclusive", False);
            return new Range<Element>(first, last, firstExclusive, lastExclusive);
            }
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        using (val obj = out.openObject())
            {
            Mapping<Element> underlying = this.underlying;
// TODO CP remove this - should be handled by Narrowable
//            if (value.Element != this.Element)
//                {
//                if (schema.enableMetadata)
//                    {
//                    obj.add(schema.typeKey, $"Range<{value.Element}>");
//                    }
//                }

            underlying.write(obj.openField("first"), value.first);
            if (value.firstExclusive)
                {
                obj.add("firstExclusive", True);
                }

            underlying.write(obj.openField("last"), value.last);
            if (value.lastExclusive)
                {
                obj.add("lastExclusive", True);
                }
            }
        }

    @Override
    <SubType extends Serializable> conditional Mapping<SubType> narrow(Schema schema, Type<SubType> type)
        {
// TODO GG
//        if (SubType.Element != Element,
//                val narrowedUnderlying := schema.findMapping(SubType.Element),
//                &narrowedUnderlying != &underlying)
//            {
//            return True, new RangeMapping<SubType.Element>(narrowedUnderlying);
//            }

        return False;
        }
    }

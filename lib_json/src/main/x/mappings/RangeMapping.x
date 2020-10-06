/**
 * A mapping for Range values.
 */
const RangeMapping<Element extends Orderable>(Mapping<Element> underlying)
        implements Mapping<Range<Element>>
    {
    @Override
    String typeName.get()
        {
        String element = underlying.typeName;
        return element + ".." + element;
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
    // TODO GG void write(ElementOutput out, Serializable value)
    void write(ElementOutput out, Range<Element> value)
        {
        using (val obj = out.openObject())
            {
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
    }

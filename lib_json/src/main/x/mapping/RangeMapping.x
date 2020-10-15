import Type.Constructor;

/**
 * A mapping for Range values.
 */
const RangeMapping<Element extends Orderable>(Schema schema, Mapping<Element> underlying)
        implements Mapping<Range<Element>>
    {
    /**
     * Construct the RangeMapping.
     *
     * @param schema      the enclosing schema
     * @param underlying  the mapping to use for the elements of the `Range`
     */
    construct(Schema schema, Mapping<Element> underlying)
        {
        this.schema     = schema;
        this.underlying = underlying;
        this.typeName   = $"Range<{underlying.typeName}>";
        }

    @Override
    Serializable read(ElementInput in)
        {
        using (val obj = in.openObject())
            {
            RangeMapping<Element> mapping = this;
            if (schema.enableMetadata, Doc overrideTypeName ?= obj.metadataFor(schema.typeKey),
                    overrideTypeName.is(String) && overrideTypeName != typeName)
                {
                mapping = schema.ensureMappingByName(overrideTypeName, Serializable).as(RangeMapping<Element>);
                }

            return mapping.read(obj);
            }
        }

    /**
     * Reads the `Range` object fields and creates a new `Range` object.
     */
    private Serializable read(FieldInput in)
        {
        Element first          = underlying.read(in.openField("first"));
        Boolean firstExclusive = in.readBoolean("firstExclusive", False);
        Element last           = underlying.read(in.openField("last"));
        Boolean lastExclusive  = in.readBoolean("lastExclusive", False);
        return new Range<Element>(first, last, firstExclusive, lastExclusive);
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        using (val obj = out.openObject())
            {
            Mapping<Element> underlying = this.underlying;
            if (value.Element != this.Element)
                {
                if (schema.enableMetadata)
                    {
                    obj.add(schema.typeKey, $"Range<{value.Element}>");
                    }

                underlying = schema.ensureMapping(value.Element);
                }

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

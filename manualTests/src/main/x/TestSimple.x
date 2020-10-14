module TestSimple
    {
    @Inject Console console;

    void run(    )
        {
        console.println();
        }

    import Type.Constructor;

    package json import json.xtclang.org;

    import json.Schema;
    import json.Doc;
    import json.Mapping;
    import json.ElementInput;
    import json.ElementOutput;

    const RangeMapping<Element extends Orderable>(Schema schema, Mapping<Element> underlying)
            implements Mapping<Range<Element>>
        {
        typedef function Range<Element>(Element, Element, Boolean, Boolean) RangeConstructor;

        /**
         * The constructor that will create ranges of the specified element type.
         */
        RangeConstructor constructor;

        @Override
        Serializable read(ElementInput in)
            {
            using (val obj = in.openObject())
                {
                RangeConstructor? constructor = Null;
                if (schema.enableMetadata, Doc overrideTypeName ?= obj.metadataFor(schema.typeKey),
                        overrideTypeName.is(String) && overrideTypeName != typeName)
                    {
                    Mapping<Serializable> overrideMapping = schema.ensureMappingByName(overrideTypeName, Serializable);
                    assert overrideMapping.is(RangeMapping);
                    constructor = overrideMapping.constructor;
                    }

                Element first          = underlying.read(obj.openField("first"));
                Boolean firstExclusive = obj.readBoolean("firstExclusive", False);
                Element last           = underlying.read(obj.openField("last"));
                Boolean lastExclusive  = obj.readBoolean("lastExclusive", False);
                return constructor?(first, last, firstExclusive, lastExclusive)
                        :  new Range<Element>(first, last, firstExclusive, lastExclusive);
                }
            }

        @Override
        void write(ElementOutput out, Serializable value)
            {
            }
        }
    }

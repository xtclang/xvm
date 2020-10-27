/**
 * A place-holder mapping for generic type values.
 */
@Narrowable const GenericMapping<Serializable>
        implements Mapping<Serializable>
    {
    @Override
    String typeName.get()
        {
        return "Object";
        }

    @Override
    Serializable read(ElementInput in)
        {
        TODO("GenericMapping must be replaced with an actual Mapping");
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        TODO("GenericMapping must be replaced with an actual Mapping");
        }

    @Override
    <SubType extends Serializable> conditional Mapping<SubType> narrow(Schema schema, Type<SubType> type)
        {
        // yes, this will throw an exception if a mapping does not exist for the type, which seems
        // wrong at first for this particular conditional method, until you consider that the entire
        // purpose of this mapping is to exist as a placeholder for some other mapping that **must**
        // exist
        return True, schema.ensureMapping(type);
        }
    }

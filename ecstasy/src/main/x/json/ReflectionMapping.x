/**
 * A reflection-based [Mapping] implementation that works for exactly one type, `Serializable`.
 *
 * TODO verify other type traits come through e.g. immutable
 */
const ReflectionMapping<Serializable, StructType extends Struct>(
                String typeName,
                Class<Serializable, Serializable, Serializable, StructType> target,
                PropertyMapping<StructType>[] fields)
        implements Mapping<Serializable>
    {
    // ----- local types ---------------------------------------------------------------------------

    static const PropertyMapping<StructType, Value>
            (String name, Mapping<Value> mapping, Property<StructType, Value> property);


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The `Class` information about the class of objects that are being read and written.
     */
    protected Class<Serializable, Serializable, Serializable, StructType> target;

    /**
     * The information about the fields of the structure to read and write.
     */
    protected PropertyMapping<StructType>[] fields;


    // ----- Mapping interface ---------------------------------------------------------------------

    @Override
    String typeName;

    @Override
    Serializable read(ElementInput in)
        {
        assert StructType structure := target.allocate();

        using (FieldInput inputAsFields = in.openObject())
            {
            for (PropertyMapping<StructType> field : fields)
                {
                field.Value value;
                if (inputAsFields.isNull(field.name))
                    {
                    value = Null; // TODO GG .as(field.Value);   (and how is it legal without it?)
                    }
                else
                    {
                    using (ElementInput inputOneField = inputAsFields.openField(field.name))
                        {
                        value = field.mapping.read(inputOneField);
                        }
                    }
                field.property.set(structure, value);
                }
            }

        return target.instantiate(structure);
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        if (value.is(Nullable))
            {
            out.add(Null);
            return;
            }

        assert StructType structure := &value.revealAs(StructType);
        using (FieldOutput outputAsFields = out.openObject())
            {
            for (PropertyMapping<StructType> field : fields)
                {
                field.Value propValue = field.property.get(structure);
                if (propValue == Null)
                    {
                    if (out.schema.retainNulls)
                        {
                        outputAsFields.add(field.name, Null);
                        }
                    }
                else
                    {
                    using (ElementOutput outputOneField = outputAsFields.openField(field.name))
                        {
                        field.mapping.write(outputOneField, propValue);
                        }
                    }
                }
            }
        }
    }

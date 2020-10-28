/**
 * A reflection-based [Mapping] implementation that works for exactly one type, `Serializable`.
 */
const ReflectionMapping<Serializable, StructType extends Struct>(
                String                                                      typeName,
                Class<Serializable, Serializable, Serializable, StructType> clazz,
                PropertyMapping<StructType>[]                               fields)
        implements Mapping<Serializable>
    {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * The `Class` information about the class of objects that are being read and written.
     */
    protected Class<Serializable, Serializable, Serializable, StructType> clazz;

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
        Schema schema = in.schema;

        assert StructType structure := clazz.allocate();
        using (FieldInput fieldInput = in.openObject())
            {
            for (PropertyMapping<StructType> field : fields)
                {
                field.Value value;
                if (fieldInput.isNull(field.name))
                    {
                    // TODO GG: value = field.defaultValue.as(field.Value);
                    value = field.defaultValue;
                    }
                else
                    {
                    using (ElementInput elementInput = fieldInput.openField(field.name))
                        {
                        var mapping = field.mapping;
                        if (field.subclassable && schema.enableMetadata,
                                Doc typeName ?= in.peekMetadata(schema.typeKey),
                                typeName.is(String))
                            {
                            mapping = schema.ensureMapping(schema.typeForName(typeName));
                            }

                        value = elementInput.readUsing(mapping, field.defaultValue);
                        }
                    }
                field.property.set(structure, value);
                }
            }

        return clazz.instantiate(structure);
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        Schema schema = out.schema;

        assert StructType structure := &value.revealAs(StructType);
        using (FieldOutput fieldOutput = out.openObject())
            {
            for (PropertyMapping<StructType> field : fields)
                {
                field.Value fieldValue = field.property.get(structure);
                if (fieldValue == Null)
                    {
                    if (out.schema.retainNulls)
                        {
                        fieldOutput.add(field.name, Null);
                        }
                    }
                else
                    {
                    val type    = &fieldValue.actualType;
                    var mapping = field.mapping;
                    if (field.subclassable && type != mapping.Serializable,
                            mapping := mapping.narrow(schema, type),
                            schema.enableMetadata)
                        {
                        fieldOutput.prepareMetadata(schema.typeKey, schema.nameForType(type));
                        }

                    fieldOutput.addUsing(mapping, field.name, fieldValue);
                    }
                }
            }
        }

    @Override
    <SubType extends Serializable> conditional Mapping<SubType> narrow(Schema schema, Type<SubType> type)
        {
        // disassemble traits (immutable, :private/:protected/:public etc.) and '&'/'|' types
        // TODO GG: switch (type.form)
        switch (type.as(Type).form)
            {
            case Intersection:
                if (type.is(Type<Nullable>) && type.as(Type).form == Intersection,
                        (Type left, Type right) := type.relational(),
                        left == Nullable)
                    {
// TODO GG
//                    if (val underlying := schema.findMapping(right))
//                        {
//                        return True, new @Narrowable NullableMapping<right.DataType>(underlyingk);
//                        }
                    }
                TODO

            case Union:
                TODO

            case Immutable:
                TODO

            case Access:
                TODO

            case Class:
                TODO
//        if (Class clazz := type.fromClass());
//        Type<clazz.StructType> struct = clazz.StructType;
//        Property<DataType>
//        .properties
//    @RO Array<Property<DataType>> properties;
//        PropertyMapping
//        new ReflectionMapping<type, clazz.StructType>(...)

            // TODO could theoretically handle child classes
            // TODO check the "annotation" form ... is that possible to occur here?
            default:
                return False;
            }
        }


    // ----- local types ---------------------------------------------------------------------------

    static const PropertyMapping<StructType, Value>(
            String                      name,
            Mapping<Value>              mapping,
            Boolean                     subclassable,
            Property<StructType, Value> property,
            Value?                      defaultValue = Null);
    }

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
                    value = field.defaultValue;
                    }
                else
                    {
                    using (ElementInput elementInput = fieldInput.openField(field.name))
                        {
                        Mapping<field.Value> mapping = field.mapping;
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
                    Mapping<field.Value> mapping = field.mapping;
                    fieldOutput.addUsing(mapping, field.name, fieldValue);
                    }
                }
            }
        }

    @Override
    <SubType extends Serializable> conditional Mapping<SubType> narrow(Schema schema, Type<SubType> type)
        {
        // disassemble traits (immutable, private/protected/public etc.) and '&'/'|' types
        switch (type.form)
            {
            case Union:
                if ((Type left, Type right) := type.relational(), left == Nullable)
                    {
                    if (val underlying := schema.findMapping(right.DataType))
                        {
                        return True, new @Narrowable NullableMapping<SubType>(underlying.as(Mapping<SubType>));
                        }
                    }
                TODO

            case Intersection:
                TODO

            case Immutable:
                // the actual type could be narrower than the compile time provided SubType;
                // make sure it is passed to "findMapping()" call
                assert Type baseType := type.modifying();
                assert baseType.is(Type<SubType>);
                if (val underlying := schema.findMapping(baseType))
                    {
                    return True, new ImmutableMapping<SubType>(underlying.as(Mapping<SubType-immutable>));
                    }
                return False;

            case Access:
                TODO

            case Class:
                if (type.is(Type<Array>))
                    {
                    assert Type elementType := type.resolveFormalType("Element");
                    if (val elementMapping := schema.findMapping(elementType.DataType))
                        {
                        return True, new ArrayMapping<elementType.DataType>(elementMapping).as(Mapping<SubType>);
                        }
                    return False;
                    }

                if (type.is(Type<Map>))
                    {
                    assert Type keyType   := type.resolveFormalType("Key");
                    assert Type valueType := type.resolveFormalType("Value");
                    if (val keyMapping   := schema.findMapping(keyType.DataType),
                        val valueMapping := schema.findMapping(valueType.DataType))
                        {
                        return True, new @Narrowable MapMapping<keyType.DataType, valueType.DataType>
                                (keyMapping, valueMapping).as(Mapping<SubType>);
                        }
                    return False;
                    }

                assert val clazz := type.fromClass();

                if (clazz.is(Enumeration))
                    {
                    return True, new EnumMapping<clazz.Value>().as(Mapping<SubType>);
                    }

                // TODO CP other singletons
                // TODO CP disallow services

                val structType = clazz.StructType;

                PropertyMapping<structType.DataType>[] fields = new PropertyMapping[];
                for (Property<structType.DataType> prop : structType.properties)
                    {
                    if (prop.hasField)
                        {
                        assert !prop.isConstant() && !prop.abstract && !prop.injected;

                        // TODO CP what if the referent type is the same as "type"? (linked list example)
                        if (Mapping<prop.Referent> valueMapping := schema.findMapping(prop.Referent))
                            {
                            // TODO CP - name has to be unique
                            fields += new PropertyMapping<structType.DataType, prop.Referent>
                                            (prop.name, valueMapping, prop);
                            }
                        }
                    }
                return True, new ReflectionMapping<type.DataType, structType.DataType>
                                    (schema.nameForType(type), clazz, fields);

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
            Property<StructType, Value> property,
            Boolean                     subclassable = True,
            Value?                      defaultValue = Null);
    }
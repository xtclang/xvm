/**
 * A mapping for an immutable Map object.
 *
 * TODO GG: if Key is String and Value is Doc we could optimize the json representation as a
 *          regular json object
 */
const MapMapping<Key, Value>
        (Mapping<Key> keyMapping, Mapping<Value> valueMapping)
        implements Mapping<Map<Key, Value>>
    {
    /**
     * Construct the MapMapping.
     *
     * @param keyMapping    the mapping to use for the keys of the `Map`
     * @param valueMapping  the mapping to use for the values of the `Map`
     */
    construct(Mapping<Key> keyMapping, Mapping<Value> valueMapping)
        {
        this.keyMapping   = keyMapping;
        this.valueMapping = valueMapping;
        this.typeName     = $"Map<{keyMapping.typeName}, {valueMapping.typeName}>";
        }

    @Override
    Serializable read(ElementInput in)
        {
        Schema schema = in.schema;

        Map<Key, Value> map;
        if (schema.enableMetadata,
                Doc typeName ?= in.peekMetadata(schema.typeKey),
                typeName.is(String),
                val typeMap     := schema.typeForName(typeName).is(Type<Map<Key, Value>>),
                val constructor := typeMap.defaultConstructor())
            {
            map = constructor();
            }
        else
            {
            map = new ListMap();
            }

        using (FieldInput mapInput = in.openObject())
            {
            using (ElementInput entriesInput = mapInput.openArray("e"))
                {
                while (entriesInput.canRead)
                    {
                    using (FieldInput entryInput = entriesInput.openObject())
                        {
                        Key   key;
                        Value value;

                        using (ElementInput keyInput = entryInput.openField("k"))
                            {
                            key = keyInput.readUsing(keyMapping);
                            }

                        using (ElementInput valueInput = entryInput.openField("v"))
                            {
                            value = valueInput.readUsing(valueMapping);
                            }
                        map.put(key, value);
                        }
                    }
                }
            }
        return map.is(Freezable) ? map.freeze(inPlace=True) : map.makeImmutable();
        }

    @Override
    void write(ElementOutput out, Serializable map)
        {
        Schema schema = out.schema;

        using (FieldOutput mapOutput = out.openObject())
            {
            using (ElementOutput entriesOutput = mapOutput.openArray("e"))
                {
                for ((Key key, Value value) : map.as(Map<Key, Value>))
                    {
                    using (FieldOutput entryOutput = entriesOutput.openObject())
                        {
                        entryOutput.addUsing(keyMapping,   "k", key);
                        entryOutput.addUsing(valueMapping, "v", value);
                        }
                    }
                }
            }
        }

    @Override
    <SubType extends  Map<Key, Value>> conditional Mapping<SubType> narrow(Schema schema, Type<SubType> type)
        {
        if (SubType.Key != Key, SubType.Value != Value,
                val narrowedKey   := schema.findMapping(SubType.Key),
                val narrowedValue := schema.findMapping(SubType.Value),
                &narrowedKey != &keyMapping, &narrowedValue != &valueMapping)
            {
            return True, new MapMapping<SubType.Key, SubType.Value>(narrowedKey, narrowedValue)
                    .as(Mapping<SubType>);
            }

        return False;
        }
    }
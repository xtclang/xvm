module TestSimple
    {
    @Inject Console console;

    package json import json.xtclang.org;

    import json.Mapping;
    import json.Schema;

    void run()
        {
        new Token(Identifier, "hello");

        Int[] ints = [1, 2];

        assertElementType(ints[0], ints);

        checkElementType(ints);

        RMapping<IntNumber> m = new RMapping();
        Type<Int> type = Int;
        m.narrow(Schema.DEFAULT, type);
        }

    const Token (Id id, Object value)
        {
        assert()
            {
            assert value.is(id.Value);
            }
        }

    enum Id<Value>(String? text)
        {
        Colon     <Object>(":"),
        Identifier<String>(Null)
        }

    private void assertElementType(Object e, Array array)
        {
        assert e.is(array.Element);

        array.Element e1 = array[0];
        array.Element e2 = array[1];

        assert e2 != e1;
        }

    private static <Value> Boolean checkElementType(Value o)
        {
        return Value.is(Type<Array>) && Value.Element.is(Type<Int>);
        }

    class RMapping<Serializable>
        {
        <SubType extends Serializable> Mapping<SubType> narrow(Schema schema, Type<SubType> type)
            {
            switch (type.form)
                {
                case Intersection:
                    {
                    TODO Intersection
                    }

                case Class:
                    assert val clazz := type.fromClass();
                    Type<Struct> structType = clazz.StructType;

                    if (Mapping valueMapping := schema.findMapping(type))
                        {
                        TODO return new PropertyMapping<structType.DataType>("");
                        }
                    TODO Class

                default:
                    {
                    TODO other
                    }
                }
            }
        }

    const PropertyMapping<StructType>(String name);
    }

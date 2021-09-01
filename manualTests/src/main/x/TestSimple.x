module TestSimple.test.org
    {
    package json import json.xtclang.org;

    import ecstasy.io.ByteArrayInputStream;
    import ecstasy.io.ByteArrayOutputStream;
    import ecstasy.io.CharArrayReader;
    import ecstasy.io.DataInputStream;
    import ecstasy.io.InputStream;
    import ecstasy.io.JavaDataInput;
    import ecstasy.io.ObjectInput;
    import ecstasy.io.ObjectOutput;
    import ecstasy.io.PackedDataInput;
    import ecstasy.io.PackedDataOutput;
    import ecstasy.io.Reader;
    import ecstasy.io.TextPosition;
    import ecstasy.io.Writer;
    import ecstasy.io.UTF8Reader;

    import json.Doc;
    import json.ElementInput;
    import json.ElementOutput;
    import json.FieldInput;
    import json.FieldOutput;
    import json.Lexer;
    import json.Lexer.Token;
    import json.Mapping;
    import json.ObjectInputStream;
    import json.ObjectInputStream.ElementInputStream;
    import json.ObjectInputStream.FieldInputStream;
    import json.ObjectOutputStream;
    import json.ObjectOutputStream.ElementOutputStream;
    import json.Parser;
    import json.Printer;
    import json.Schema;

    @Inject Console console;

    void run()
        {
        Range<Int> r = 1..4;

        assert:debug;

        Type<Range<Int>> type = &r.actualType;
        assert Mapping mapping := Schema.DEFAULT.mappingByType.values.any(m -> m.is(json.mapping.RangeMapping));
        Type typeDataType = type.DataType;
        Type mappingSerializable = mapping.Serializable;
        Boolean isA1 = (type.DataType.is(Type<mapping.Serializable>));
        Boolean isA2 = (Int.PublicType.is(Type<Orderable.PublicType>));
        Boolean isA3 = (Range<Int>.PublicType.is(Type<Range>));
        Boolean isA4 = (Range<Int>.PublicType.is(Type<Range<Orderable>>));

        Schema schema = Schema.DEFAULT;
        StringBuffer writer = new StringBuffer();
        ObjectOutputStream  o_out = schema.createObjectOutput(writer).as(ObjectOutputStream);
        ElementOutputStream e_out = o_out.createElementOutput();

        e_out.addObject(r);

        String s = writer.toString();
        console.println($"result={s}");
        }

    static Ordered orderLogFiles(File file1, File file2, DateTime? dt1, DateTime? dt2)
        {
//        assert DateTime? dt1 := isLogFile(file1);
//        assert DateTime? dt2 := isLogFile(file2);

        // sort the null datetime to the end, because it represents the "current" log file
        return dt1? <=> dt2? : switch (dt1, dt2)
            {
            case (Null, _): Greater;
            case (_, Null): Lesser;
            default       : Equal;
            };
        }
    }
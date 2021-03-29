
module db
    {
    @Inject Console console;

    package json import json.xtclang.org;
    package oodb import oodb.xtclang.org;
    package jsondb import jsondb.xtclang.org;

    import json.Schema;
    import ecstasy.io.CharArrayReader;
    import jsondb.model.SysInfo;

    void run()
        {
        console.println("*** db test ***\n");

        @Inject FileStore storage;
        @Inject Directory curDir;
        Directory dataDir = curDir.dirFor("build").ensure().dirFor("scratch").ensure();

        console.println($"directory={dataDir}");
        console.println(dataDir.emitListing(new StringBuffer()));

        import jsondb.Catalog;
        Catalog cat = new Catalog(dataDir);
        console.println($"catalog={cat}");
        console.println($"glance={cat.glance()}");

//        {
//        SysInfo info = new SysInfo(cat);
//        console.println($"Testing SysInfo serialization for SysInfo={info}");
//        StringBuffer buf = new StringBuffer();
//        Schema.DEFAULT.createObjectOutput(buf).write(info);
//        String json = buf.toString();
//        console.println($"SysInfo JSON={json}");
//        SysInfo info2 = Schema.DEFAULT.createObjectInput(new CharArrayReader(json)).read();
//        console.println($"SysInfo from JSON={info2}");
//        }

//        if (cat.statusFile.exists)
//            {
//            console.println($"Reading {cat.statusFile.path} ...");
//            Byte[]  bytes = cat.statusFile.contents;
//            jsondb.dump("- bytes", bytes);
//            SysInfo info  = jsondb.fromBytes(SysInfo, bytes);
//            jsondb.dump("- info", info);
//            dump("- bytes", bytes);
//            SysInfo info  = fromBytes(SysInfo, bytes);
//            dump("- info", info);
//            }

        try
            {
            Tuple t1 = cat.create("test");
            }
        catch (Exception e)
            {
            console.println($"Exception occurred creating db: {e}");
            console.println("Recoving...");
            Tuple t2 = cat.recover();
            console.println("Configuring...");
            Tuple t3 = cat.edit();
            }

//        using (val section = new ecstasy.AsyncSection(e ->
//                {
//                console.println($"Exception occurred creating db: {e}");
//                console.println("Recoving...");
//                cat.recover();
//                console.println("Configuring...");
//                cat.edit();
//                }))
//            {
//            cat.create("test");
//            }

//        @Inject Clock clock;
//        SysInfo info = new SysInfo(cat.status, clock.now, clock.now, v:1.0);
//        console.println($"info={info}");
//
//        Schema schemaD = Schema.DEFAULT;
//
//        String sD = ser(schemaD, info);
//        console.println($"ser info={sD}");
//
//        SysInfo infoD = deser(schemaD, sD);
//        console.println($"deser info={infoD}");

        Tuple t4 = cat.close();
        }

    String ser(Schema schema, SysInfo info)
        {
        StringBuffer buf = new StringBuffer();
        schema.createObjectOutput(buf).write(info);
        return buf.toString();
        }

    SysInfo deser(Schema schema, String s)
        {
        return schema.createObjectInput(new CharArrayReader(s)).read<SysInfo>();
        }

    static <Serializable> immutable Byte[] toBytes(Serializable value)
        {
        import ecstasy.io.*;
        val raw = new ByteArrayOutputStream();
        json.Schema.DEFAULT.createObjectOutput(new UTF8Writer(raw)).write(value);
        return raw.bytes.freeze(True);
        }

    static <Serializable> Serializable fromBytes(Type<Serializable> type, Byte[] bytes)
        {
        import ecstasy.io.*;
        dump("reading XML", bytes);
        return json.Schema.DEFAULT.createObjectInput(new UTF8Reader(new ByteArrayInputStream(bytes))).read();
        }

    static void dump(String desc, Object o)
        {
        @Inject Console console;
        String s = switch ()
            {
            case o.is(Byte[]): o.all(b -> b >= 32 && b <= 127 || new Char(b).isWhitespace())
                    ? new String(new Char[o.size](i -> new Char(o[i])))
                    : o.toString();

            default: o.toString();
            };

        console.println($"{desc}={s}");
        }
    }

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

        console.println($"status={cat.status}");
//        cat.create("test");

        @Inject Clock clock;
        SysInfo info = new SysInfo(cat.status, clock.now, v:1.0);
        console.println($"info={info}");

        Schema schemaC = new Schema([new jsondb.model.SysInfoMapping()], randomAccess = True);
        Schema schemaD = Schema.DEFAULT;

        String sC = ser(schemaC, info);
        String sD = ser(schemaD, info);
        console.println($"custom JSON={sC}, default JSON={sD}");

        SysInfo infoC = deser(schemaC, sC);
        SysInfo infoD = deser(schemaD, sD);
        console.println($"deser info custom={infoC} default={infoD}");
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
    }
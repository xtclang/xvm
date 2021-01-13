module TestSimple
    {
    @Inject Console console;

    void run()
        {
        @Inject Directory curDir;
        @Inject Directory rootDir;

        File fileXtc;
        if (fileXtc := curDir.findFile("build/AddressBookApp.xtc"))
            {
            }
        else
            {
            console.println("not found");
            return;
            }
        console.println(fileXtc);

        // simulate the code gen logic
        Path?     buildPath = fileXtc.path.parent;
        Directory buildDir  = rootDir.dirFor(buildPath?.toString()) : curDir;

        Directory moduleDir = buildDir.dirFor("AddressBookApp_imdb");
        if (moduleDir.exists)
            {
            moduleDir.deleteRecursively();
            }

        moduleDir.create();

        console.println($"moduleDir={moduleDir}");

        File   moduleFile   = moduleDir.fileFor("module.x");
        String moduleSource = "module AddressBookApp_imdb{}";

        moduleFile.create();

        // the line below should be:
        // moduleSource.contents = moduleSource.utfBytes();
        writeUtf(moduleFile, moduleSource);

        console.println(moduleSource);
        }

    void writeUtf(File file, String content)
        {
        import ecstasy.io.ByteArrayOutputStream as Stream;
        import ecstasy.io.UTF8Writer;
        import ecstasy.io.Writer;

        Stream out    = new Stream(content.size);
        Writer writer = new UTF8Writer(out);
        writer.addAll(content);
        file.contents = out.bytes.freeze(True);
        }
    }
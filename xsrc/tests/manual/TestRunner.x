module TestRunner.xtclang.org
    {
    import ecstasy.io.IOException;

    import ecstasy.mgmt.Container;
    import ecstasy.mgmt.Container.ApplicationControl;
    import ecstasy.mgmt.InstantRepository;
    import ecstasy.mgmt.ResourceProvider;

    @Inject Clock clock;
    @Inject Console console;

    void run()
        {
        @Inject Directory curDir;

        console.println($"Current directory is {curDir}");

        while (true)
            {
            // console.print("Module path: ");
            // String path = console.readLine(); // e.g. "tests/manual/TestSimple.xtc"
            String path = "tests/manual/TestSimple.xtc";
            if (path.size == 0)
                {
                break;
                }

            if (File|Directory node := curDir.find(path))
                {
                if (node.is(File))
                    {
                    loadAndRun(node);
                    }
                else
                    {
                    console.println($"'{path}' - not a file");
                    }
                }
            else
                {
                console.println($"'{path}' - not found");
                }
            break;
            }
        }

    void loadAndRun(File fileXtc)
        {
        immutable Byte[] bytes;
        try
            {
            bytes = fileXtc.contents;
            console.println($"module={fileXtc}; size={bytes.size}");
            }
        catch (IOException e)
            {
            console.println($"Failed to read the module: {fileXtc}");
            return;
            }

        InstantRepository repo      = new InstantRepository(bytes);
        Container         container = new Container(repo.moduleName, repo, new Injector());

        console.println($"Invoking {repo.moduleName}.run()\n");
        container.appControl.invoke("run", Tuple:());
        }

    class Injector
            implements ResourceProvider
        {
        @Override
        <Resource> Resource getResource(Type<Resource> type, String name)
            {
            Boolean wrongName = False;

            switch (Resource)
                {
                case Console:
                    if (name == "console")
                        {
                        return console.as(Resource);
                        }
                    wrongName = True;
                    break;

                case Clock:
                    if (name == "clock")
                        {
                        return clock.as(Resource);
                        }
                    wrongName = True;
                    break;
                }

            throw wrongName
                ? new Exception($"Invalid resource type: {type}")
                : new Exception($"Invalid resource name: {name}");
            }
        }
    }

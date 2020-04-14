module TestRunner.xtclang.org
    {
    import ecstasy.io.IOException;

    import ecstasy.mgmt.Container;
    import ecstasy.mgmt.Container.ApplicationControl;
    import ecstasy.mgmt.InstantRepository;
    import ecstasy.mgmt.ResourceProvider;

    @Inject Clock     clock;
    @Inject Console   console;
    @Inject Directory curDir;
    @Inject Directory tmpDir;

    void run(String[] modules=[])
        {
        if (modules.empty)
            {
            console.println($"Current directory is {curDir}");
            while (true)
                {
                console.print("\nEnter module path: ");

                String path = console.readLine(); // e.g. "tests/manual/TestSimple.xtc"
                if (path.size == 0)
                    {
                    break;
                    }
                loadAndRun(path);
                }
            }
        else
            {
            for (Int i : [0..modules.size))
                {
                loadAndRun(modules[i]);
                }
            }
        }

    void loadAndRun(String path)
        {
        File fileXtc;
        if (File|Directory node := curDir.find(path))
            {
            if (node.is(File))
                {
                fileXtc = node;
                }
            else
                {
                console.println($"'{path}' - not a file");
                return;
                }
            }
        else
            {
            console.println($"'{path}' - not found");
            return;
            }

        immutable Byte[] bytes;
        try
            {
            bytes = fileXtc.contents;
            }
        catch (IOException e)
            {
            console.println($"Failed to read the module: {fileXtc}");
            return;
            }

        InstantRepository repo = new InstantRepository(bytes);

        console.println($"\n++++++ Loading module: {repo.moduleName} +++++++\n");

        Container container = new Container(repo.moduleName, repo, new Injector());
        container.appControl.invoke("run", Tuple:());
        }

    class Injector
            implements ResourceProvider
        {
        @Override
        Object getResource(Type type, String name)
            {
            Boolean wrongName = False;

            switch (type)
                {
                case Console:
                    if (name == "console")
                        {
                        return console;
                        }
                    wrongName = True;
                    break;

                case Clock:
                    if (name == "clock")
                        {
                        return clock;
                        }
                    wrongName = True;
                    break;

                case Directory:
                    switch (name)
                        {
                        case "tmpDir":
                            return tmpDir;

                        case "curDir":
                            return curDir;

                        default:
                            wrongName = True;
                            break;
                        }
                    break;
                }

            throw wrongName
                ? new Exception($"Invalid resource type: {type}")
                : new Exception($"Invalid resource name: {name}");
            }
        }
    }

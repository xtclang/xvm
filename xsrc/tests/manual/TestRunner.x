module TestRunner.xtclang.org
    {
    import ecstasy.io.IOException;

    import ecstasy.mgmt.Container;
    import ecstasy.mgmt.Container.ApplicationControl;
    import ecstasy.mgmt.InstantRepository;
    import ecstasy.mgmt.ResourceProvider;

    import Injector.ConsoleBuffer as Buffer;

    @Inject Console   console;
    @Inject Directory curDir;

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
                Tuple<FutureVar, Buffer>? result = loadAndRun(path);
                if (result != null)
                    {
                    result[0].get(); // block until done
                    console.println(result[1].flush());
                    }
                }
            }
        else
            {
            Tuple<FutureVar, Buffer>?[] results =
                new Array(modules.size, i -> loadAndRun(modules[i]));
            reportResults(results, 0);
            }
        }

    void reportResults(Tuple<FutureVar, Buffer>?[] results, Int index)
        {
        while (index < results.size)
            {
            Tuple<FutureVar, Buffer>? resultTuple = results[index++];
            if (resultTuple != Null)
                {
                resultTuple[0].whenComplete((_, e) ->
                    {
                    if (e == null)
                        {
                        console.println(resultTuple[1].flush());
                        }
                    else
                        {
                        console.println(e);
                        }
                    reportResults(results, index);
                    });
                return;
                }
            }
        }

    Tuple<FutureVar, Buffer>? loadAndRun(String path)
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
                return Null;
                }
            }
        else
            {
            console.println($"'{path}' - not found");
            return Null;
            }

        immutable Byte[] bytes;
        try
            {
            bytes = fileXtc.contents;
            }
        catch (IOException e)
            {
            console.println($"Failed to read the module: {fileXtc}");
            return Null;
            }

        InstantRepository repo = new InstantRepository(bytes);

        Injector  injector  = new Injector();
        Container container = new Container(repo.moduleName, repo, injector);
        Buffer    buffer    = injector.consoleBuffer;

        buffer.println($"++++++ Loading module: {repo.moduleName} +++++++\n");

        @Future Tuple result = container.appControl.invoke("run", Tuple:());
        return (&result, buffer);
        }

    class Injector
            implements ResourceProvider
        {
        static service ConsoleBuffer
                implements Console
            {
            private StringBuffer buffer = new StringBuffer();

            @Override
            void print(Object o)
                {
                buffer.append(o.toString());
                }

            @Override
            void println(Object o = "")
                {
                buffer.append(o.toString()).append('\n');
                }

            @Override
            String readLine()
                {
                throw new UnsupportedOperation();
                }

            @Override
            Boolean echo(Boolean flag)
                {
                throw new UnsupportedOperation();
                }

            String flush()
                {
                String s = buffer.toString();
                buffer = new StringBuffer();
                return s;
                }
            }

        ConsoleBuffer consoleBuffer = new ConsoleBuffer();

        @Override
        Object getResource(Type type, String name)
            {
            import Container.Linker;

            @Inject Clock     clock;
            @Inject Timer     timer;
            @Inject FileStore storage;
            @Inject Directory rootDir;
            @Inject Directory homeDir;
            @Inject Directory tmpDir;
            @Inject Linker    linker;

            Boolean wrongName = False;
            switch (type)
                {
                case Console:
                    if (name == "console")
                        {
                        return &consoleBuffer.maskAs<Console>();
                        // return console;
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

                case Timer:
                    if (name == "timer")
                        {
                        return timer;
                        }
                    wrongName = True;
                    break;

                case FileStore:
                    if (name == "storage")
                        {
                        return storage;
                        }
                    wrongName = True;
                    break;

                case Directory:
                    switch (name)
                        {
                        case "rootDir":
                            return rootDir;

                        case "homeDir":
                            return homeDir;

                        case "curDir":
                            return curDir;

                        case "tmpDir":
                            return tmpDir;

                        default:
                            wrongName = True;
                            break;
                        }
                    break;

                case Linker:
                    if (name == "linker")
                        {
                        return linker;
                        }
                    wrongName = True;
                    break;
                }

            throw wrongName
                ? new Exception($"Invalid resource name: \"{name}\"")
                : new Exception($"Invalid resource type: \"{type}\"");
            }
        }
    }

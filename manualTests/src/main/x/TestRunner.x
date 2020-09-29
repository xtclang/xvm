module TestRunner.xtclang.org
    {
    import ecstasy.io.IOException;

    import ecstasy.mgmt.Container;
    import ecstasy.mgmt.InstantRepository;
    import ecstasy.mgmt.ModuleRepository;
    import ecstasy.mgmt.ResourceProvider;

    import ecstasy.reflect.FileTemplate;
    import ecstasy.reflect.ModuleTemplate;

    import Injector.ConsoleBuffer as Buffer;

    @Inject Console   console;
    @Inject Directory curDir;

    void run(String[] modules=[])
        {
        if (modules.empty)
            {
            console.println($"Current directory is {curDir}");
            while (True)
                {
                console.print("\nEnter module path: ");

                String path = console.readLine(); // e.g. "tests/manual/TestSimple.xtc"
                if (path.size == 0)
                    {
                    break;
                    }
                Tuple<FutureVar, Buffer>? result = loadAndRun(path);
                if (result != Null)
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
                    if (e == Null)
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

        @Inject Container.Linker linker;
        @Inject ModuleRepository repository;

        FileTemplate fileTemplate = linker.loadFileTemplate(bytes);

        InstantRepository repo = new InstantRepository(fileTemplate.mainModule, repository);

        Injector  injector  = new Injector();
        Container container = new Container(repo.moduleName, Lightweight, repo, injector);
        Buffer    buffer    = injector.consoleBuffer;

        buffer.println($"++++++ Loading module: {repo.moduleName} +++++++\n");

        @Future Tuple result = container.invoke("run", Tuple:());
        return (&result, buffer);
        }

    const Injector
            implements ResourceProvider
        {
        ConsoleBuffer consoleBuffer = new ConsoleBuffer(new ConsoleBack());

        /**
         * The "client" side of the ConsoleBuffer that operates in the injected space.
         */
        static const ConsoleBuffer(ConsoleBack backService)
                implements Console
            {
            @Override
            void print(Object o)
                {
                backService.print(o.toString());
                }

            @Override
            void println(Object o = "")
                {
                backService.println(o.toString());
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
                return backService.flush();
                }

            @Override
            Appender<Char> appendTo(Appender<Char> buf)
                {
                return buf.addAll("Console");
                }
            }

        /**
         * The "service" side of the ConsoleBuffer.
         */
        static service ConsoleBack
            {
            private StringBuffer buffer = new StringBuffer();

            void print(String s)
                {
                buffer.append(s);
                }

            void println(String s)
                {
                buffer.append(s).append('\n');
                }

            String flush()
                {
                String s = buffer.toString();
                buffer = new StringBuffer();
                return s;
                }
            }

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
            @Inject Random    random;

            Boolean wrongName = False;
            switch (type)
                {
                case Console:
                    if (name == "console")
                        {
                        return consoleBuffer;
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

                case Random:
                    if (name == "random")
                        {
                        return random;
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

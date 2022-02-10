/**
 * The Injector service.
 */
service Injector(Directory appHomeDir)
        implements ResourceProvider
    {
    @Lazy ConsoleImpl consoleImpl.calc()
        {
        File consoleFile = appHomeDir.fileFor("console.log");
        if (consoleFile.exists)
            {
            // remove the old content
            consoleFile.truncate(0);
            }
        else
            {
            consoleFile.ensure();
            }
        return new ConsoleImpl(consoleFile);
        }

    /**
     * The file-based Console implementation.
     */
    class ConsoleImpl(File consoleFile)
            implements Console
        {
        @Override
        void print(Object o)
            {
            write(o.is(String) ? o : o.toString());
            }

        @Override
        void println(Object o = "")
            {
            writeln(o.is(String) ? o : o.toString());
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

        void write(String s)
            {
            consoleFile.append(s.utf8());
            }

        void writeln(String s)
            {
            // consider remembering the position if the file size calls for pruning
            consoleFile.append(s.utf8()).append(['\n'.toByte()]);
            }
        }

    @Override
    Supplier getResource(Type type, String name)
        {
        import Container.Linker;

        Boolean wrongName = False;
        switch (type)
            {
            case Console:
                if (name == "console")
                    {
                    return &consoleImpl.maskAs(Console);
                    }
                wrongName = True;
                break;

            case Clock:
                if (name == "clock")
                    {
                    @Inject Clock clock;
                    return clock;
                    }
                wrongName = True;
                break;

            case Timer:
                if (name == "timer")
                    {
                    @Inject Timer timer;
                    return timer;
                    }
                wrongName = True;
                break;

            case FileStore:
                if (name == "storage")
                    {
                    @Inject FileStore storage;
                    return storage;
                    }
                wrongName = True;
                break;

            case Directory:
                switch (name)
                    {
                    case "rootDir":
                        @Inject Directory rootDir;
                        return rootDir;

                    case "homeDir":
                        @Inject Directory homeDir;
                        return homeDir;

                    case "curDir":
                        @Inject Directory curDir;
                        return curDir;

                    case "tmpDir":
                        @Inject Directory tmpDir;
                        return tmpDir;

                    default:
                        wrongName = True;
                        break;
                    }
                break;

            case Linker:
                if (name == "linker")
                    {
                    @Inject Linker linker;
                    return linker;
                    }
                wrongName = True;
                break;

            case Random:
                if (name == "random" || name == "rnd")
                    {
                    return (InjectedRef.Options opts) ->
                        {
                        @Inject(opts=opts) Random random;
                        return random;
                        };
                    }
                wrongName = True;
                break;
            }

        throw wrongName
            ? new Exception($"Invalid resource name: \"{name}\" of type \"{type}\"")
            : new Exception($"Invalid resource type: \"{type}\" for name \"{name}\"");
        }
    }

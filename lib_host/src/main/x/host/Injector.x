/**
 * The Injector service.
 */
service Injector(Directory appHomeDir, Boolean platform)
        implements ResourceProvider
    {
    @Lazy FileStore store.calc()
        {
        import ecstasy.fs.DirectoryFileStore;

        return new DirectoryFileStore(appHomeDir);
        }

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
                    if (platform)
                        {
                        @Inject Console console;
                        return console;
                        }
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
                    if (platform)
                        {
                        @Inject FileStore storage;
                        return storage;
                        }

                    return &store.maskAs(FileStore);
                    }
                wrongName = True;
                break;

            case Directory:
                switch (name)
                    {
                    case "rootDir":
                        if (platform)
                            {
                            @Inject Directory rootDir;
                            return rootDir;
                            }

                        Directory root = store.root;
                        return &root.maskAs(Directory);

                    case "homeDir":
                        if (platform)
                            {
                            @Inject Directory homeDir;
                            return homeDir;
                            }

                        Directory root = store.root;
                        return &root.maskAs(Directory);

                    case "curDir":
                        if (platform)
                            {
                            @Inject Directory curDir;
                            return curDir;
                            }

                        Directory root = store.root;
                        return &root.maskAs(Directory);

                    case "tmpDir":
                        if (platform)
                            {
                            @Inject Directory tmpDir;
                            return tmpDir;
                            }

                        Directory temp = store.root.find("_temp").as(Directory);
                        return &temp.maskAs(Directory);

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
                    import ecstasy.annotations.InjectedRef;

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
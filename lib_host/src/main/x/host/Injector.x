/**
 * The Injector service.
 */
service Injector(Directory appHomeDir)
        implements ResourceProvider
    {
    @Lazy ConsoleBuffer consoleBuffer.calc()
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
        return new ConsoleBuffer(new ConsoleBack(consoleFile));
        }

    /**
     * The "client" side of the ConsoleBuffer that operates in the injected space.
     */
    const ConsoleBuffer(ConsoleBack backService)
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

        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            return buf.addAll("Console");
            }
        }

    /**
     * The "service" side of the ConsoleBuffer.
     */
    class ConsoleBack(File consoleFile)
        {
        void print(String s)
            {
            consoleFile.append(s.utf8());
            }

        void println(String s)
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
                    return &consoleBuffer.maskAs(Console);
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

module host.xtclang.org
    {
    package oodb   import oodb.xtclang.org;
    package imdb   import imdb.xtclang.org;
    package jsondb import jsondb.xtclang.org;

    import ecstasy.annotations.InjectedRef;

    import ecstasy.io.IOException;

    import ecstasy.mgmt.Container;
    import ecstasy.mgmt.ModuleRepository;
    import ecstasy.mgmt.ResourceProvider;

    import ecstasy.reflect.ClassTemplate;
    import ecstasy.reflect.FileTemplate;
    import ecstasy.reflect.ModuleTemplate;
    import ecstasy.reflect.TypeTemplate;

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
                    console.println(resultTuple[1].flush());
                    if (e != Null)
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
        if (fileXtc := curDir.findFile(path)) {}
        else
            {
            console.println($"'{path}' - not found");
            return Null;
            }

        Byte[] bytes;
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

        FileTemplate fileTemplate;
        try
            {
            fileTemplate = linker.loadFileTemplate(bytes).resolve(repository);
            }
        catch (Exception e)
            {
            console.println($"Failed to resolve the module: {fileXtc} ({e.text})");
            return Null;
            }

        ModuleTemplate  moduleTemplate = fileTemplate.mainModule;
        Injector        injector;
        function void() terminate = () -> {};

        if (String dbModuleName := detectDatabase(fileTemplate))
            {
            Path?     buildPath = fileXtc.path.parent;
            Directory buildDir  = fileXtc.store.dirFor(buildPath?) : curDir;
            String[]  errors    = new String[];

            DbHost         dbHost;
            ModuleTemplate dbModuleTemplate;

            if (dbHost           := createDBHost(errors),
                dbModuleTemplate := dbHost.generateDBModule(repository, dbModuleName, buildDir, errors)) {}
            else
                {
                console.println($"Failed to create a host for : {dbModuleName}");
                for (String error : errors)
                    {
                    console.println(error);
                    }
                return Null;
                }

            terminate = dbHost.closeDatabase;

            // give the db container the real console
            Injector dbInjector = new Injector()
                {
                @Override
                Supplier getResource(Type type, String name)
                    {
                    return type.isA(Console)
                            ? console
                            : super(type, name);
                    }
                };

            dbHost.dbContainer = new Container(dbModuleTemplate, Lightweight, repository, dbInjector);

            import oodb.Connection;
            import oodb.DBUser;

            function Connection(DBUser) createConnection = dbHost.ensureDatabase();

            injector = new Injector()
                {
                @Override
                Supplier getResource(Type type, String name)
                    {
                    if (type.is(Type<Connection>))
                        {
                        return (InjectedRef.Options opts) ->
                            {
                            // consider the injector to be passed some info about the calling
                            // container, so the host could figure out the user
                            DBUser user = new oodb.model.DBUser(1, "test");
                            Connection conn = createConnection(user);
                            return &conn.maskAs<Connection>(type);
                            };
                        }
                    return super(type, name);
                    }
                };
            }
        else
            {
            injector = new Injector();
            }

        Container container = new Container(moduleTemplate, Lightweight, repository, injector);
        Buffer    buffer    = injector.consoleBuffer;

        buffer.println($"++++++ Loading module: {moduleTemplate.qualifiedName} +++++++\n");

        @Future Tuple result = container.invoke("run", Tuple:());
        &result.whenComplete((r, x) -> terminate());
        return (&result, buffer);
        }

    /**
     * @return True iff the primary module for the specified FileTemplate depends on a Database
     *         module
     * @return (optional) the Database module name
     */
    conditional String detectDatabase(FileTemplate fileTemplate)
        {
        import ClassTemplate.Contribution;

        TypeTemplate dbTypeTemplate = oodb.Database.as(Type).template;

        for ((String name, String dependsOn) : fileTemplate.mainModule.moduleNamesByPath)
            {
            if (dependsOn != TypeSystem.MackKernel)
                {
                ModuleTemplate depModule = fileTemplate.getModule(dependsOn);

                for (Contribution contrib : depModule.contribs)
                    {
                    if (contrib.action == Incorporates &&
                            contrib.ingredient.type == dbTypeTemplate)
                        {
                        return True, dependsOn;
                        }
                    }
                }
            }
        return False;
        }

    /**
     * Create a DbHost.
     */
    conditional DbHost createDBHost(Log errors)
        {
        @Inject Map<String, String> properties;

        switch (String impl = properties.getOrDefault("db.impl", "imdb"))
            {
            case "imdb":
                return True, new ImdbHost();

            case "json":
                return True, new JsondbHost();

            default:
                errors.add($"Error: Unknown db implementation: {impl}");
                return False;
            }
        }

    service Injector
            implements ResourceProvider
        {
        @Lazy ConsoleBuffer consoleBuffer.calc()
            {
            return new ConsoleBuffer(new ConsoleBack());
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
        class ConsoleBack
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
    }

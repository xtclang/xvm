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
                FutureVar? result = loadAndRun(path);
                if (result != Null)
                    {
                    result.get(); // block until done
                    }
                }
            }
        else
            {
            FutureVar?[] results = new Array(modules.size, i -> loadAndRun(modules[i]));
            waitForResults(results, 0);
            }
        }

    void waitForResults(FutureVar?[] results, Int index)
        {
        while (index < results.size)
            {
            FutureVar? result = results[index++];
            if (result != Null)
                {
                result.whenComplete((_, e) ->
                    {
                    if (e != Null)
                        {
                        console.println(e);
                        }
                    waitForResults(results, index);
                    });
                return;
                }
            }
        }

    FutureVar? loadAndRun(String path)
        {
        File fileXtc;
        if (!(fileXtc := curDir.findFile(path)))
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
        Path?           buildPath      = fileXtc.path.parent;
        Directory       buildDir       = fileXtc.store.dirFor(buildPath?) : curDir;
        Directory       appHomeDir     = ensureHome(buildDir, moduleTemplate);
        function void() terminate      = () -> {};
        Injector        injector;

        if (String dbModuleName := detectDatabase(fileTemplate))
            {
            String[]       errors = new String[];
            DbHost         dbHost;
            ModuleTemplate dbModuleTemplate;

            if (dbHost           := createDBHost(dbModuleName, errors),
                dbModuleTemplate := dbHost.generateDBModule(repository, buildDir, errors)) {}
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

            Injector dbInjector = new Injector(ensureHome(buildDir, dbModuleTemplate));

            dbHost.dbContainer = new Container(dbModuleTemplate, Lightweight, repository, dbInjector);

            import oodb.Connection;
            import oodb.RootSchema;
            import oodb.DBUser;

            function Connection(DBUser) createConnection = dbHost.ensureDatabase();

            injector = new Injector(appHomeDir)
                {
                @Override
                Supplier getResource(Type type, String name)
                    {
                    if (type.is(Type<Connection>) || type.is(Type<RootSchema>))
                        {
                        return (InjectedRef.Options opts) ->
                            {
                            // consider the injector to be passed some info about the calling
                            // container, so the host could figure out the user
                            DBUser user = new oodb.model.User(1, "test");
                            Connection conn = createConnection(user);
                            return type.is(Type<Connection>)
                                    ? &conn.maskAs<Connection>(type)
                                    : &conn.maskAs<RootSchema>(type);
                            };
                        }
                    return super(type, name);
                    }
                };
            }
        else
            {
            injector = new Injector(appHomeDir);
            }

        Container container = new Container(moduleTemplate, Lightweight, repository, injector);

        @Future Tuple result = container.invoke("run", Tuple:());
        &result.whenComplete((r, x) -> terminate());
        return &result;
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
    conditional DbHost createDBHost(String dbModuleName, Log errors)
        {
        @Inject Map<String, String> properties;

        switch (String impl = properties.getOrDefault("db.impl", "json"))
            {
            case "":
            case "json":
                return True, new JsondbHost(dbModuleName);

            default:
                errors.add($"Error: Unknown db implementation: {impl}");
                return False;
            }
        }

    /**
     * Ensure a home directory for the specified module.
     */
    Directory ensureHome(Directory parentDir, ModuleTemplate template)
        {
        return parentDir.dirFor($"{template.qualifiedName}_home").ensure();
        }

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
    }

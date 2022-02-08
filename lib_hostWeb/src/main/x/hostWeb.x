/**
 * The module for basic web-based hosting functionality.
 */
module hostWeb.xtclang.org
    {
    package host import host.xtclang.org;
    package web  import web.xtclang.org;

    void run()
        {
        import web.WebServer;

        WebServer server = new WebServer(8080)
                .addRoutes(new HostApi(), "/host")
                .start();

        @Inject Console console;
        console.println("Started Ecstasy hosting at http://localhost:8080");

        wait();
        }

    void wait()
        {
        // wait forever
        @Inject Timer timer;
        timer.schedule(Duration:1h, () -> {});
        }

    service HostApi
        {
        import ecstasy.mgmt.Container;
        import ecstasy.mgmt.ModuleRepository;

        import ecstasy.reflect.FileTemplate;

        import host.AppHost;
        import host.DbHost;
        import host.Injector;

        import web.Consumes;
        import web.Get;
        import web.HttpStatus;
        import web.PathParam;
        import web.Post;
        import web.Produces;

        Map<String, AppHost>   loaded  = new HashMap();
        Map<String, FutureVar> running = new HashMap();

        @Post("/load/{appName}")
        HttpStatus load(@PathParam String appName)
            {
assert:debug;
            // we assume that the hostWeb covers a single "domain", which means that
            // there is one and only one loaded app for a given name
            if (loaded.contains(appName))
                {
                return HttpStatus.OK;
                }

            FileTemplate fileTemplate;
            Directory    appHomeDir;
            Injector     injector;

            Log errors = new String[];
            if ((fileTemplate, appHomeDir) := host.load(appName, errors))
                {
                if (String dbModuleName := host.detectDatabase(fileTemplate))
                    {
                    DbHost dbHost;
                    if (AppHost appHost := loaded.get(dbModuleName))
                        {
                        assert appHost.is(DbHost);
                        dbHost = appHost;
                        }
                    else
                        {
                        Directory workDir = appHomeDir.parent ?: assert;
                        if (!(dbHost := host.createDbHost(workDir, dbModuleName, errors)))
                            {
                            // TODO: how to communicate the errors?
                            return HttpStatus.NotFound;
                            }
                        loaded.put(dbModuleName, dbHost);
                        }

                    injector = host.createDbInjector(dbHost, appHomeDir);
                    }
                else
                    {
                    injector = new host.Injector(appHomeDir);
                    }

                @Inject ModuleRepository repository;

                AppHost appHost = new AppHost(appName, appHomeDir);
                appHost.container =
                        new Container(fileTemplate.mainModule, Lightweight, repository, injector);

                loaded.put(appName, appHost);
                return HttpStatus.OK;
                }
            return HttpStatus.NotFound;
            }

        @Post("/run/{appName}")
        HttpStatus run(@PathParam String appName)
            {
assert:debug;
            if (FutureVar result := running.get(appName), !result.assigned)
                {
                return HttpStatus.Processing;
                }

            AppHost appHost;
            if (!(appHost := loaded.get(appName)))
                {
                HttpStatus status = load(appName);
                if (status != HttpStatus.OK)
                    {
                    return status;
                    }
                assert appHost := loaded.get(appName);
                }

            Tuple result = appHost.container.invoke^("run", Tuple:());
            running.put(appName, &result);

            return HttpStatus.OK;
            }

        @Get("/{appName}")
        @Produces("text/plain")
        String report(@PathParam String appName)
            {
assert:debug;
            if (AppHost appHost := loaded.get(appName))
                {
                if (FutureVar result := running.get(appName))
                    {
                    return result.completion.toString();
                    }
                else
                    {
                    return "Not running";
                    }
                }
            else
                {
                return "Not loaded";
                }
            }

        @Post("/unload/{appName}")
        void unload(@PathParam String appName)
            {
            }
        }
    }

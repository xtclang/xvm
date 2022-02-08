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
            // we assume that the hostWeb covers a single "domain", which means that
            // there is one and only one loaded app for a given name
            if (loaded.contains(appName))
                {
                return HttpStatus.OK;
                }

            FileTemplate fileTemplate;
            Directory    appHomeDir;
            Injector     injector;

            Log    errors = new String[];
            String path   = $"build/{appName}.xtc"; // REVIEW: temporary hack
            if ((fileTemplate, appHomeDir) := host.load(path, errors))
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
                            // REVIEW: how to communicate the errors?
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

        @Get("/report/{appName}")
        @Produces("application/json")
        String report(@PathParam String appName)
            {
            String response;
            if (AppHost appHost := loaded.get(appName))
                {
                if (FutureVar result := running.get(appName))
                    {
                    response = result.completion.toString();
                    }
                else
                    {
                    response = "Not running";
                    }
                }
            else
                {
                response = "Not loaded";
                }
            return response.quoted();
            }

        @Post("/unload/{appName}")
        HttpStatus unload(@PathParam String appName)
            {
            if (loaded.contains(appName))
                {
                loaded.remove(appName);
                return HttpStatus.OK;
                }
            return HttpStatus.NotFound;
            }
        }
    }

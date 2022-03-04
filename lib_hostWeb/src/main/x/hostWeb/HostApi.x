service HostApi
    {
    import ecstasy.mgmt.Container;
    import ecstasy.mgmt.ModuleRepository;

    import ecstasy.reflect.FileTemplate;
    import ecstasy.reflect.ModuleTemplate;

    import host.AppHost;
    import host.DbHost;
    import host.Injector;

    import web.Consumes;
    import web.Get;
    import web.HttpStatus;
    import web.PathParam;
    import web.Post;
    import web.Produces;
    import web.WebService;

    typedef Map<String, WebService> as Roots;

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

        Log    errors  = new String[];
        String xtcPath = $"build/{appName}.xtc"; // REVIEW: temporary hack
        if ((fileTemplate, appHomeDir) := host.load(xtcPath, errors))
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
                injector = new Injector(appHomeDir);
                }

            @Inject ModuleRepository repository;

            ModuleTemplate template  = fileTemplate.mainModule;
            Container      container = new Container(template, Lightweight, repository, injector);

            // TODO GG: hostWeb. should not be needed
            Boolean        webModule = hostWeb.findClassAnnotation(template, "web.WebModule");
            AppHost        appHost;

            if (webModule)
                {
                Tuple result = container.invoke("collectRoots_", Tuple:());
                Roots roots  = result.size == 0 ? Map:[] : result[0].as(Roots);
                if (roots.size == 0)
                    {
                    // REVIEW: how to communicate the errors?
                    return HttpStatus.BadRequest;
                    }

assert:debug;
                for ((String path, WebService webService) : roots)
                    {
                    server.addRoutes(webService, path);
                    }
                appHost = new WebHost(appName, appHomeDir, roots.keys);
                }
            else
                {
                appHost = new AppHost(appName, appHomeDir);
                }

            appHost.container = container;

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
            return HttpStatus.NoContent;
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

        // TODO GG: move to AppHost
        if (!appHost.is(WebHost))
            {
            Tuple result = appHost.container.invoke^("run", Tuple:());
            running.put(appName, &result);
            }
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
        if (AppHost appHost := loaded.get(appName))
            {
            loaded.remove(appName);

            if (FutureVar result := running.get(appName), !result.assigned)
                {
                // TODO GG: appHost.container.kill();
                result.set(Tuple:());
                }
            running.remove(appName);

            return HttpStatus.OK;
            }
        return HttpStatus.NotFound;
        }

    @Post("/debug")
    HttpStatus debug()
        {
        // temporary
        assert:debug;
        return HttpStatus.OK;
        }
    }

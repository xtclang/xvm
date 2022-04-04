@web.WebService
service HostApi
    {
    import ecstasy.mgmt.Container;
    import ecstasy.mgmt.ModuleRepository;

    import ecstasy.reflect.AnnotationTemplate;
    import ecstasy.reflect.ClassTemplate;
    import ecstasy.reflect.FileTemplate;
    import ecstasy.reflect.ModuleTemplate;
    import ecstasy.reflect.TypeTemplate;

    import host.AppHost;
    import host.DbHost;
    import host.Injector;

    import web.Consumes;
    import web.Get;
    import web.HttpStatus;
    import web.PathParam;
    import web.Post;
    import web.Produces;
    import web.QueryParam;
    import web.WebServer.Handler;

    @Inject Console console; // TEMPORARY; for debugging only

    Map<String, AppHost>   loaded  = new HashMap();
    Map<String, FutureVar> running = new HashMap();

    @Post("/load")
    (HttpStatus, String) load(@QueryParam("app") String appName, @QueryParam String realm)
        {
        // we assume that the hostWeb covers a single "domain", which means that
        // there is one and only one loaded app for a given name
        if (loaded.contains(appName))
            {
            return HttpStatus.OK, "Already loaded";
            }

        FileTemplate fileTemplate;
        Directory    appHomeDir;
        Injector     injector;

        Log    errors  = new String[];
        String xtcPath = $"build/{appName}.xtc"; // REVIEW: temporary hack
        if (!((fileTemplate, appHomeDir) := host.load(xtcPath, errors)))
            {
            return HttpStatus.NotFound, $"Cannot find application \"{appName}\"";
            }

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
                    return HttpStatus.NotFound, $"Cannot load the database \"{dbModuleName}\"";
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
        Container      container;
        try
            {
            container = new Container(template, Lightweight, repository, injector);
            }
        catch (Exception e)
            {
            return HttpStatus.BadRequest, $"Failed to load \"{appName}\": {e.text}";
            }

        Boolean webModule = template.findAnnotation("web.WebModule");
        AppHost appHost;

        if (webModule)
            {
            Tuple result = container.invoke("createCatalog_", Tuple:(server.httpServer));

            if (!realm.startsWith('/'))
                {
                realm = '/' + realm;
                }

            server.addHandler(realm, result[0].as(Handler));

            appHost = new WebHost(appName, appHomeDir, realm);
            }
        else
            {
            appHost = new AppHost(appName, appHomeDir);
            }

        appHost.container = container;

        loaded.put(appName, appHost);
        return HttpStatus.OK, "";
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
            if (appHost.is(WebHost))
                {
                server.removeHandler(appHost.appRealm);
                }
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
@web.WebService("/host")
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
    import web.WebServer.Handler;

    @Inject Console console; // TEMPORARY

    typedef Map<String, Handler> as Roots;

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
        if (!((fileTemplate, appHomeDir) := host.load(xtcPath, errors)))
            {
            console.println($"Cannot find application \"{appName}\"");
            return HttpStatus.NotFound;
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
        Container      container;
        try
            {
            container = new Container(template, Lightweight, repository, injector);
            }
        catch (Exception e)
            {
            console.println($"Failed to load \"{appName}\": {e.text}");
            return HttpStatus.BadRequest;
            }

        Boolean webModule = findClassAnnotation(template, "web.WebModule");
        AppHost appHost;

        if (webModule)
            {
            Tuple result = container.invoke("collectRoots_", Tuple:(server.httpServer));
            Roots roots  = result.size == 0 ? [] : result[0].as(Roots);
            if (roots.size == 0)
                {
                // REVIEW: how to communicate the errors?
                console.println($"Application \"{appName}\" doesn't have any endpoints");
                return HttpStatus.BadRequest;
                }

            for ((String path, Handler handler) : roots)
                {
                server.addHandler(path, handler);
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
            if (appHost.is(WebHost))
                {
                for (String path : appHost.roots)
                    {
                    server.removeHandler(path);
                    }
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


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Check if the `ClassTemplate` has a specified annotation.
     *
     * @return True iff there is an annotation of the specified name
     * @return the corresponding `AnnotationTemplate` (optional)
     */
    conditional AnnotationTemplate findClassAnnotation(ClassTemplate template, String annotationName)
        {
        import ecstasy.reflect.ClassTemplate.Composition;
        import ecstasy.reflect.ClassTemplate.AnnotatingComposition;

        for (val contrib : template.contribs)
            {
            if (contrib.action == AnnotatedBy)
                {
                assert AnnotatingComposition composition := contrib.ingredient.is(AnnotatingComposition);
                if (composition.annotation.template.displayName == annotationName)
                    {
                    return True, composition.annotation;
                    }
                }
            }

        return False;
        }
    }
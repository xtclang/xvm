/**
 * The module for core hosting functionality.
 */
module host.xtclang.org
    {
    package oodb     import oodb.xtclang.org;
    package jsondb   import jsondb.xtclang.org;
    package web      import web.xtclang.org;
    package platform import platform.xtclang.org;

    import ecstasy.io.Log;

    import ecstasy.mgmt.Container;
    import ecstasy.mgmt.ModuleRepository;
    import ecstasy.mgmt.ResourceProvider;

    import ecstasy.reflect.FileTemplate;
    import ecstasy.reflect.ModuleTemplate;

    import platform.AppHost;
    import platform.ErrorLog;

    import web.HttpServer;

    void run(String[] args=[])
        {
        @Inject Console console;

        HostManager mgr    = new HostManager();
        ErrorLog    errors = new ErrorLog();

        if (args.empty)
            {
            // start the host controller
            Int portController  = 8080;
            Int portApplication = 80;

            @Inject("server", portController)  HttpServer httpServerC;
            @Inject("server", portApplication) HttpServer httpServerA;

            console.println($"Staring the host controller...");

            @Inject ModuleRepository repository;
            @Inject Directory        curDir;

            ModuleTemplate controllerModule = repository.getResolvedModule("hostWeb.xtclang.org");

            assert AppHost ctrlHost :=
                mgr.createAppHost(controllerModule.parent, curDir, errors, realm="platform");

            ctrlHost.container.invoke("configure", Tuple:(&mgr.maskAs(platform.HostManager),
                    httpServerC, httpServerA));
            }
        else
            {
            // host a single individual application
            String path = args[0];
            if ((FileTemplate fileTemplate, Directory appHomeDir) := mgr.loadTemplate(path, errors))
                {
                if (AppHost appHost := mgr.createAppHost(fileTemplate, appHomeDir, errors))
                    {
                    Tuple result = appHost.container.invoke^("run", Tuple:());
                    &result.whenComplete((r, x) -> appHost.close(x));

                    &result.handle(e ->
                        {
                        console.println($"Execution error: {e}");
                        return Tuple:();
                        });
                    }
                }
            }

        errors.reportAll(msg -> console.println(msg));
        }
    }
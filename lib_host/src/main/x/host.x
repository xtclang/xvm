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

    import platform.ErrorLog;

    import web.HttpServer;

    void run(String[] args=[])
        {
        HostManager mgr    = new HostManager();
        ErrorLog    errors = new ErrorLog();

        @Inject Console          console;
        @Inject Directory        curDir;
        @Inject ModuleRepository repository;

        // create and configure the host controller
        console.println($"Starting the host controller...");

        ModuleTemplate controlModule = repository.getResolvedModule("hostControl.xtclang.org");
        if (Container container :=
                mgr.createContainer(repository, controlModule.parent, curDir, True, errors))
            {
            @Inject("server", "admin.xqiz.it:8080") HttpServer httpAdmin;
            container.invoke("configure", Tuple:(&mgr.maskAs(platform.HostManager), httpAdmin));
            }

        // TODO create and configure the account manager, etc.

        errors.reportAll(msg -> console.println(msg));
        }
    }
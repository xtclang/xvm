import ecstasy.reflect.FileTemplate;

import platform.AppHost;
import platform.ErrorLog;
import platform.HostManager;

import web.Consumes;
import web.Get;
import web.HttpStatus;
import web.PathParam;
import web.Post;
import web.Produces;
import web.QueryParam;
import web.WebServer;
import web.WebServer.Handler;
import web.WebService;

@WebService
service Controller
    {
    construct(HostManager mgr, HttpServer httpServer)
        {
        this.mgr       = mgr;
        this.webServer = new WebServer(httpServer);

        webServer.start();
        }

    /**
     * The host manager.
     */
    HostManager mgr;

    /**
     * The WebServer serving loaded web applications.
     */
    WebServer webServer;

    /**
     * Currently running applications (once-and-done).
     */
    Map<String, FutureVar> running = new HashMap();

    @Post("/load")
    (HttpStatus, String) load(@QueryParam("app") String appName, @QueryParam String realm)
        {
        // we assume that the hostWeb covers a single "domain", which means that
        // there is one and only one loaded app for a given name
        if (mgr.getAppHost(appName))
            {
            return HttpStatus.OK, "Already loaded";
            }

        String   path   = $"build/{appName}.xtc"; // REVIEW: temporary hack
        ErrorLog errors = new ErrorLog();

        if ((FileTemplate fileTemplate, Directory appHomeDir) := mgr.loadTemplate(path, errors))
            {
            if (AppHost appHost := mgr.createAppHost(fileTemplate, appHomeDir, errors, realm),
                        appHost.isWeb)
                {
                Tuple result = appHost.container.invoke("createCatalog_", Tuple:(webServer.httpServer));

                webServer.addHandler(appHost.appRealm, result[0].as(Handler));
                return HttpStatus.OK, "";
                }
            }
        return HttpStatus.NotFound, errors.errors.toString();
        }

    @Get("/report/{appName}")
    @Produces("application/json")
    String report(@PathParam String appName)
        {
        String response;
        if (AppHost appHost := mgr.getAppHost(appName))
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
        if (AppHost appHost := mgr.getAppHost(appName))
            {
            mgr.removeAppHost(appHost);

            if (appHost.isWeb)
                {
                webServer.removeHandler(appHost.appRealm);
                }
            else
                {
                if (FutureVar result := running.get(appName), !result.assigned)
                    {
                    // TODO GG: appHost.container.kill();
                    result.set(Tuple:());
                    }
                running.remove(appName);
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
import ecstasy.mgmt.Container;

import ecstasy.reflect.FileTemplate;

import platform.WebHost;
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

@WebService("/host")
service Controller(HostManager mgr)
    {
    /**
     * The host manager.
     */
    private HostManager mgr;

    @Post("/load")
    (HttpStatus, String) load(@QueryParam("app") String appName, @QueryParam String domain)
        {
        // there is one and only one application per [sub] domain
        if (mgr.getWebHost(domain))
            {
            return HttpStatus.OK, "Already loaded";
            }

        String   path   = $"build/{appName}.xtc"; // REVIEW: temporary hack
        ErrorLog errors = new ErrorLog();

        if ((FileTemplate fileTemplate, Directory appHomeDir) := mgr.loadTemplate(path, errors))
            {
            if (WebHost webHost := mgr.createWebHost(fileTemplate, appHomeDir, domain, False, errors))
                {
                try
                    {
                    webHost.container.invoke("createCatalog_", Tuple:(webHost.httpServer));

                    return HttpStatus.OK, $"Loaded \"{appName}\" hosting on \"http:{domain}.xqiz.it:8080\"";
                    }
                catch (Exception e)
                    {
                    webHost.close(e);
                    mgr.removeWebHost(webHost);
                    }
                }
            }
        return HttpStatus.NotFound, errors.toString();
        }

    @Get("/report/{domain}")
    @Produces("application/json")
    String report(@PathParam String domain)
        {
        String response;
        if (WebHost webHost := mgr.getWebHost(domain))
            {
            Container container = webHost.container;
            response = $"{container.status} {container.statusIndicator}";
            }
        else
            {
            response = "Not loaded";
            }
        return response.quoted();
        }

    @Post("/unload/{domain}")
    HttpStatus unload(@PathParam String domain)
        {
        if (WebHost webHost := mgr.getWebHost(domain))
            {
            mgr.removeWebHost(webHost);
            webHost.close();

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
import ecstasy.annotations.InjectedRef;

import ecstasy.io.IOException;

import ecstasy.mgmt.Container;
import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ClassTemplate;
import ecstasy.reflect.FileTemplate;
import ecstasy.reflect.ModuleTemplate;
import ecstasy.reflect.TypeTemplate;

import platform.AppHost;
import platform.WebHost;

import Injector.ConsoleBuffer as Buffer;


/**
 * The module for basic hosting functionality.
 */
service HostManager
        implements platform.HostManager
    {
    @Inject Console          console;
    @Inject Directory        curDir;
    @Inject ModuleRepository repository;

    /**
     * Loaded WebHost objects keyed by the application domain name.
     */
    Map<String, WebHost> loaded = new HashMap();


    // ----- platform.HostManager API --------------------------------------------------------------

    @Override
    conditional WebHost getWebHost(String domain)
        {
        return loaded.get(domain);
        }

    @Override
    conditional (FileTemplate, Directory) loadTemplate(String path, Log errors)
        {
        File fileXtc;
        if (!(fileXtc := curDir.findFile(path)))
            {
            errors.add($"Error: {path.quoted()} - not found");
            return False;
            }

        Byte[] bytes;
        try
            {
            bytes = fileXtc.contents;
            }
        catch (IOException e)
            {
            errors.add($"Error: Failed to read the module: {fileXtc}");
            return False;
            }

        FileTemplate fileTemplate;
        try
            {
            @Inject Container.Linker linker;

            fileTemplate = linker.loadFileTemplate(bytes).resolve(repository);
            }
        catch (Exception e)
            {
            errors.add($"Error: Failed to resolve the module: {fileXtc} ({e.text})");
            return False;
            }

        Path?     buildPath = fileXtc.path.parent;
        Directory workDir   = fileXtc.store.dirFor(buildPath?) : curDir; // temporary
        Directory homeDir   = ensureHome(workDir, fileTemplate.mainModule.qualifiedName);

        return True, fileTemplate, homeDir;
        }

    @Override
    conditional WebHost createWebHost(FileTemplate fileTemplate, Directory appHomeDir,
                                      String domain, Boolean platform, Log errors)
        {
        ModuleTemplate template   = fileTemplate.mainModule;
        String         moduleName = template.displayName;
        if (!template.findAnnotation("web.WebModule"))
            {
            errors.add($"Module \"{moduleName}\" is not a WebModule");
            return False;
            }

        if ((Container container, AppHost[] dependents) :=
                createContainer(fileTemplate, appHomeDir, platform, errors))
            {
            WebHost webHost = new WebHost(container, moduleName, appHomeDir, domain,
                                createHttpServer(domain), dependents);
            if (!platform)
                {
                loaded.put(domain, webHost);
                }
            return True, webHost;
            }

        return False;
        }

    @Override
    void removeWebHost(WebHost webHost)
        {
        loaded.remove(webHost.domain);
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Create a Container for the specified template.
     *
     * @return True iff the container has been loaded successfully
     * @return (optional) the Container
     * @return (optional) an array of AppHost objects for all dependent containers that have been
     *         loaded along the "main" container
     */
    conditional (Container, AppHost[])
            createContainer(FileTemplate fileTemplate, Directory appHomeDir,
                            Boolean platform, Log errors)
        {
        DbHost[] dbHosts;
        Injector injector;

        Map<String, String> dbNames = detectDatabases(fileTemplate);
        if (dbNames.size > 0)
            {
            dbHosts = new DbHost[];

            for ((String dbPath, String dbModuleName) : dbNames)
                {
                Directory workDir = appHomeDir.parent ?: assert;
                DbHost    dbHost;

                if (!(dbHost := createDbHost(workDir, dbModuleName, errors)))
                    {
                    errors.add($"Cannot load the database \"{dbModuleName}\"");
                    return False;
                    }
                dbHosts += dbHost;
                }
            dbHosts.makeImmutable();

            injector = createDbInjector(dbHosts, appHomeDir);
            }
        else
            {
            dbHosts  = [];
            injector = new Injector(appHomeDir, platform);
            }

        ModuleTemplate template = fileTemplate.mainModule;
        try
            {
            return True, new Container(template, Lightweight, repository, injector), dbHosts;
            }
        catch (Exception e)
            {
            errors.add($"Failed to load \"{template.displayName}\": {e.text}");
            return False;
            }
        }

    /**
     * @return an array of the Database module names that the specified template depends on
     */
    Map<String, String> detectDatabases(FileTemplate fileTemplate)
        {
        import ClassTemplate.Contribution;

        TypeTemplate        dbTypeTemplate = oodb.Database.as(Type).template;
        Map<String, String> dbNames        = new HashMap();

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
                        dbNames.put(name, dependsOn);
                        }
                    }
                }
            }
        return dbNames;
        }

    /**
     * Create a DbHost for the specified db module.
     *
     * @return (optional) the DbHost
     */
    conditional DbHost createDbHost(Directory workDir, String dbModuleName, Log errors)
        {
        Directory      dbHomeDir = ensureHome(workDir, dbModuleName);
        DbHost         dbHost;
        ModuleTemplate dbModuleTemplate;

        @Inject Map<String, String> properties;

        switch (String impl = properties.getOrDefault("db.impl", "json"))
            {
            case "":
            case "json":
                dbHost = new JsondbHost(dbModuleName, dbHomeDir);
                break;

            default:
                errors.add($"Error: Unknown db implementation: {impl}");
                return False;
            }

        if (!(dbModuleTemplate := dbHost.ensureDBModule(repository, workDir, errors)))
            {
            errors.add($"Error: Failed to create a host for : {dbModuleName}");
            return False;
            }

        dbHost.container = new Container(dbModuleTemplate, Lightweight, repository,
                                new Injector(dbHomeDir, False));
        dbHost.makeImmutable();
        return True, dbHost;
        }

    /**
     * @return an Injector that injects db connections based on the arrays of the specified DbHosts
     */
    Injector createDbInjector(DbHost[] dbHosts, Directory appHomeDir)
        {
        import oodb.Connection;
        import oodb.RootSchema;
        import oodb.DBUser;

        return new Injector(appHomeDir, False)
            {
            @Override
            Supplier getResource(Type type, String name)
                {
                if (type.is(Type<RootSchema>) || type.is(Type<Connection>))
                    {
                    Type schemaType;
                    if (type.is(Type<RootSchema>))
                        {
                        schemaType = type;
                        }
                    else
                        {
                        assert schemaType := type.resolveFormalType("Schema");
                        }

                    for (DbHost dbHost : dbHosts)
                        {
                        // the actual type that "createConnection" produces is:
                        // RootSchema + Connection<RootSchema>;

                        Type dbSchemaType   = dbHost.schemaType;
                        Type typeConnection = Connection;

                        typeConnection = dbSchemaType + typeConnection.parameterize([dbSchemaType]);
                        if (typeConnection.isA(schemaType))
                            {
                            function Connection(DBUser) createConnection = dbHost.ensureDatabase();

                            return (InjectedRef.Options opts) ->
                                {
                                // consider the injector to be passed some info about the calling
                                // container, so the host could figure out the user
                                DBUser     user = new oodb.model.User(1, "test");
                                Connection conn = createConnection(user);
                                return type.is(Type<Connection>)
                                        ? &conn.maskAs<Connection>(type)
                                        : &conn.maskAs<RootSchema>(type);
                                };
                           }
                        }
                    }
                return super(type, name);
                }
            };
        }

    /**
     * Ensure a home directory for the specified module.
     */
    Directory ensureHome(Directory parentDir, String moduleName)
        {
        return parentDir.dirFor(moduleName).ensure();
        }

    /**
     * TODO
     */
    HttpServer createHttpServer(String domain)
        {
        String address = $"{domain}.xqiz.it:8080";
        // TODO: ensure a DNS entry
        @Inject(opts=address) HttpServer server;
        return server;
        }
    }
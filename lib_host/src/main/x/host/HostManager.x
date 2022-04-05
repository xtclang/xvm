import ecstasy.annotations.InjectedRef;

import ecstasy.io.IOException;

import ecstasy.mgmt.Container;
import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ClassTemplate;
import ecstasy.reflect.FileTemplate;
import ecstasy.reflect.ModuleTemplate;
import ecstasy.reflect.TypeTemplate;

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
     * Loaded AppHost objects keyed by the module names.
     */
    Map<String, AppHost> loaded = new HashMap();


    // ----- platform.HostManager API --------------------------------------------------------------

    @Override
    conditional AppHost getAppHost(String appName)
        {
        return loaded.get(appName);
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
    conditional AppHost createAppHost(FileTemplate fileTemplate, Directory appHomeDir, Log errors,
                                      String realm = "", String[] privateDbNames=[])
        {
        Injector injector;

        if (String dbModuleName := detectDatabase(fileTemplate))
            {
            DbHost dbHost;
            if (!privateDbNames.contains(dbModuleName), AppHost ah := loaded.get(dbModuleName))
                {
                assert dbHost := ah.is(DbHost);
                }
            else
                {
                Directory workDir = appHomeDir.parent ?: assert;

                if (!(dbHost := createDbHost(workDir, dbModuleName, errors)))
                    {
                    errors.add($"Cannot load the database \"{dbModuleName}\"");
                    return False;
                    }
                }

            injector = createDbInjector(dbHost, appHomeDir);
            }
        else
            {
            injector = new Injector(appHomeDir, realm == "platform");
            }

        ModuleTemplate template = fileTemplate.mainModule;
        String         appName  = template.displayName;
        Container      container;
        try
            {
            container = new Container(template, Lightweight, repository, injector);
            }
        catch (Exception e)
            {
            errors.add($"Failed to load \"{appName}\": {e.text}");
            return False;
            }

        if (!realm.startsWith('/'))
            {
            realm = '/' + realm;
            }

        AppHost appHost = template.findAnnotation("web.WebModule")
            ? new WebHost(appName, appHomeDir, realm)
            : new AppHost(appName, appHomeDir);

        appHost.container = container;
        appHost.makeImmutable();

        loaded.put(appName, appHost);
        return True, appHost;
        }

    @Override
    void removeAppHost(AppHost appHost)
        {
        loaded.remove(appHost.moduleName);
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * @return True iff the primary module for the specified FileTemplate depends on a Database
     *         module
     * @return (optional) the Database module name
     */
    conditional String detectDatabase(FileTemplate fileTemplate)
        {
        import ClassTemplate.Contribution;

        TypeTemplate dbTypeTemplate = oodb.Database.as(Type).template;

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
                        return True, dependsOn;
                        }
                    }
                }
            }
        return False;
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
        return True, dbHost;
        }

    /**
     * @return an Injector for the specified DbHost
     */
    Injector createDbInjector(DbHost dbHost, Directory appHomeDir)
        {
        import oodb.Connection;
        import oodb.RootSchema;
        import oodb.DBUser;

        function Connection(DBUser) createConnection = dbHost.ensureDatabase();

        return new Injector(appHomeDir, False)
            {
            @Override
            Supplier getResource(Type type, String name)
                {
                if (type.is(Type<Connection>) || type.is(Type<RootSchema>))
                    {
                    return (InjectedRef.Options opts) ->
                        {
                        // consider the injector to be passed some info about the calling
                        // container, so the host could figure out the user
                        DBUser user = new oodb.model.User(1, "test");
                        Connection conn = createConnection(user);
                        return type.is(Type<Connection>)
                                ? &conn.maskAs<Connection>(type)
                                : &conn.maskAs<RootSchema>(type);
                        };
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
    }
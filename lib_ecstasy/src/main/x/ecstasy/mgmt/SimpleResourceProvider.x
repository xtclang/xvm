/**
 * SimpleResourceProvider is a minimal `ResourceProvider` implementation that is necessary to
 * load a Ecstasy module dynamically into a lightweight container. The example use:
 *
 *   void runModule(String moduleName)
 *       {
 *       import ecstasy.mgmt.*;
 *       import ecstasy.reflect.ModuleTemplate;
 *
 *       @Inject ModuleRepository repository;
 *
 *       ModuleTemplate   template = repository.getResolvedModule(moduleName);
 *       ResourceProvider injector = new SimpleResourceProvider();
 *
 *       Container container = new Container(template, Lightweight, repository, injector);
 *       container.invoke("run", Tuple:());
 *       }
 */
service SimpleResourceProvider
             implements ResourceProvider
    {
    @Override
    Supplier getResource(Type type, String name)
        {
        import annotations.InjectedRef;
        import Container.Linker;

        switch (type, name)
            {
            case (Console, "console"):
                @Inject Console console;
                return console;

            case (Clock, "clock"):
                @Inject Clock clock;
                return clock;

            case (Timer, "timer"):
                return (InjectedRef.Options opts) ->
                    {
                    @Inject(opts=opts) Timer timer;
                    return timer;
                    };

            case (Random, "random"):
            case (Random, "rnd"):
                return (InjectedRef.Options opts) ->
                    {
                    @Inject(opts=opts) Random random;
                    return random;
                    };

            case (Linker, "linker"):
                @Inject Linker linker;
                return linker;

            default:
                throw new Exception($"Invalid resource: type=\"{type}\", name=\"{name}\"");
            }
        }
    }
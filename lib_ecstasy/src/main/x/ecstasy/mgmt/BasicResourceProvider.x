/**
 * BasicResourceProvider is a minimal `ResourceProvider` implementation that is necessary to
 * load an Ecstasy module dynamically into a lightweight container. The example use:
 *
 *   void runModule(String moduleName) {
 *       import ecstasy.mgmt.*;
 *       import ecstasy.reflect.ModuleTemplate;
 *
 *       @Inject ModuleRepository repository;
 *
 *       ModuleTemplate   template = repository.getResolvedModule(moduleName);
 *       ResourceProvider injector = new BasicResourceProvider();
 *
 *       Container container = new Container(template, Lightweight, repository, injector);
 *       container.invoke("run", Tuple:());
 *   }
 */
service BasicResourceProvider
             implements ResourceProvider {
    @Override
    Supplier getResource(Type type, String name) {
        import annotations.InjectedRef;
        import Container.Linker;

        switch (type, name) {
        case (Console, "console"):
            @Inject Console console;
            return console;

        case (Clock, "clock"):
            @Inject Clock clock;
            return clock;

        case (Timer, "timer"):
            return (InjectedRef.Options opts) -> {
                @Inject(opts=opts) Timer timer;
                return timer;
            };

        case (Random, "random"):
        case (Random, "rnd"):
            return (InjectedRef.Options opts) -> {
                @Inject(opts=opts) Random random;
                return random;
            };

        default:
            // if the type is Nullable, no need to complain; just return Null, otherwise
            // return a deferred exception (thrown only if the container actually asks for the
            // resource at run time)
            return Nullable.as(Type).isA(type)
                ? Null.as(Supplier)
                : (InjectedRef.Options opts) ->
                    throw new Exception($|Unsupported resource: type="{type}", name="{name}"
                                       );
        }
    }
}
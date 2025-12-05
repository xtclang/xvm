import ecstasy.annotations.Inject.Options;

import ecstasy.reflect.Injector;

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
 *       container.invoke("run", ());
 *   }
 */
service BasicResourceProvider
             implements ResourceProvider, Injector {
    @Override
    Supplier getResource(Type type, String name) {
        import ecstasy.collections.HashCollector;
        import ecstasy.mgmt.Container.Linker;

        switch (type, name) {
        case (Console, "console"):
            @Inject Console console;
            return console;

        case (Clock, "clock"):
            @Inject Clock clock;
            return clock;

//        case (HashCollector, "hash"): // TODO CP: add native or natural implementation
//            @Inject HashCollector hash;
//            return hash;
//
        case (Injector, "injector"):
            return &this.maskAs(Injector);

        case (Timer, "timer"):
            return (Inject.Options opts) -> {
                @Inject(opts=opts) Timer timer;
                return timer;
            };

        case (Random, "random"):
        case (Random, "rnd"):
            return (Inject.Options opts) -> {
                @Inject(opts=opts) Random random;
                return random;
            };

        default:
            // if the type is Nullable, no need to complain; just return Null, otherwise
            // return a deferred exception (thrown only if the container actually asks for the
            // resource at run time)
            @Inject Injector injector;
            return (Options opts) -> {
                Object o = injector.inject(type, name, opts);
                if (o != Null) {
                    return o;
                }
                return Nullable.as(Type).isA(type)
                    ? Null.as(Supplier)
                    : (Inject.Options opts) ->
                        throw new Exception($|Unsupported resource: type="{type}", name="{name}"
                                           );
            };
        }
    }

    @Override
    <InjectionType> InjectionType inject(Type<InjectionType> type, String name,
                                         Inject.Options opts = Null) {
        Supplier supplier = getResource(type, name);
        if (val supply := supplier.is(function Resource(Inject.Options))) {
            return supply(opts).as(InjectionType);
        }

        return supplier.as(InjectionType);
    }
}
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

        // allow injection of Nullable types
        Boolean isNullable = False;
        Type    sansNull   = type;
        if (sansNull := type.isNullable()) {
            isNullable = True;
        }

        switch (sansNull, name) {
        case (Console, "console"):
            @Inject Console console;
            return console;

        case (Clock, "clock"):
            @Inject Clock clock;
            return clock;

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

        case (String, _):
            // ToDo This will return ANY string injectable from the parent, we might want to think
            // about this and maybe filter them in some way as we would not necessarily want to pass
            // down some injected information that the child container should not be able to see
            return (Inject.Options opts) -> {
                if (isNullable) {
                    @Inject(resourceName=name, opts=opts) String? value;
                    return value;
                } else {
                    @Inject(resourceName=name, opts=opts) String value;
                    return value;
                }
            };

        case (List<String>, _):
            // ToDo This will return ANY string injectable from the parent, we might want to think
            // about this and maybe filter them in some way as we would not necessarily want to pass
            // down some injected information that the child container should not be able to see
            return (Inject.Options opts) -> {
                if (isNullable) {
                    @Inject(resourceName=name, opts=opts) List<String>? value;
                    return value;
                } else {
                    @Inject(resourceName=name, opts=opts) List<String> value;
                    return value;
                }
            };

        default:
            if (Supplier supp := getEnumResource(sansNull, name, isNullable)) {
                return supp;
            }
            if (Supplier supp := getDestringableResource(sansNull, name, isNullable)) {
                return supp;
            }
            // if the type is Nullable, no need to complain; just return Null, otherwise
            // return a deferred exception (thrown only if the container actually asks for the
            // resource at run time)
            return isNullable
                ? Null.as(Supplier)
                : (Inject.Options opts) ->
                    throw new Exception($|Unsupported resource: type="{type}", name="{name}"
                                       );
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

    /**
     * Returns a supplier that constructs a Destringable resource for a string injection.
     *
     * @param type        the non-nullable type of the injection
     * @param name        the name of the injection
     * @param isNullable  `True` iff the injection result may be Nullable
     *
     * @return `True` iff the injection type is a Destringable or nullable Destringable
     * @return a supplier that constructs a Destringable resource for a string injection
     */
    conditional Supplier getDestringableResource(Type type, String name, Boolean isNullable) {
        if (type.is(Type<Destringable>)) {
            // the requested type is Destringable so it may be possible to construct it from a
            // string injection
            @Inject(resourceName=name) String? value;
            if (value.is(String)) {
                return True, new type.DataType(value);
            }
            if (isNullable) {
                return True, Null;
            }
            return True, (Inject.Options opts) ->
                throw new Exception($|Unsupported resource: type="{type}", name="{name}"
                                    );
        }
        return False;
    }

    /**
     * Returns a supplier that constructs an Enum resource for a string injection.
     *
     * @param type        the non-nullable type of the injection
     * @param name        the name of the injection
     * @param isNullable  `True` iff the injection result may be Nullable
     *
     * @return `True` iff the injection type is a Destringable or nullable Destringable
     * @return a supplier that constructs an Enum resource for a string injection
     */
    conditional Supplier getEnumResource(Type type, String name, Boolean isNullable) {
        if (type.is(Type<Enum>)) {
            // the requested type is Destringable so it may be possible to construct it from a
            // string injection
            @Inject(resourceName=name) String? value;
            if (value.is(String), Class clz := type.fromClass(), clz.is(Enumeration)) {
                if (Enum en := clz.byName.get(value)) {
                    return True, en;
                }
                // a value was specified but does not match any enum value, so return an exception
                return True, (Inject.Options opts) ->
                    throw new Exception($|Injectable {name}="{value}" does not match any names\
                                         | in enum {type} {clz.names}
                                         );
            }
            if (isNullable) {
                return True, Null;
            }
            return True, (Inject.Options opts) ->
                throw new Exception($|Unsupported resource: type="{type}", name="{name}"
                                    );
        }
        return False;
    }
}
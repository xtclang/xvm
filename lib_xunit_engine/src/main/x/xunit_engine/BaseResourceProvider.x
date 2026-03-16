import ecstasy.lang.src.Compiler;

import ecstasy.mgmt.*;

import ecstasy.reflect.Injector;

/**
 * A base class `ResourceProvider` implementation that can provide resources to inject into tests.
 *
 * @param curDir      the current working directory
 * @param repository  the module repository containing the test module and dependencies
 */
@Abstract service BaseResourceProvider(Directory curDir, ModuleRepository repository)
        implements ResourceProvider, Injector {

    /**
     * The parent container's `Injector` to use to inject resources that are not provided by the
     * XUnit framework or by test overrides.
     */
    public/private @Inject Injector injector;

    /**
     * The `FileStore` to use to access files.
     */
    @Lazy FileStore store.calc() {
        @Inject FileStore storage;
        return storage;
    }

    @Override
    Supplier getResource(Type type, String name) {
        // allow injection of Nullable types
        switch (type.isNullable() ?: type, name) {
        case (FileStore, "storage"):
            return &store.maskAs(FileStore);

        case (Directory, _):
            switch (name) {
            case "rootDir":
                return curDir;

            case "homeDir":
                return curDir;

            case "curDir":
                return curDir;
            }
            break;

        case (ModuleRepository, "repository"):
            return repository;
        }

        return (Inject.Options opts) -> injector.inject(type, name, opts);
    }

    @Override
    <InjectionType> InjectionType inject(Type<InjectionType> type, String name,
            Inject.Options opts = Null) {

        Supplier supplier = getResource(type, name);
        if (val supply := supplier.is(ResourceSupplier)) {
            return supply(opts).as(InjectionType);
        }
        return supplier.as(InjectionType);
    }
}

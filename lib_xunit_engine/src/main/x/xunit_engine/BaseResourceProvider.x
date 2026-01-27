import ecstasy.lang.src.Compiler;

import ecstasy.mgmt.*;

/**
 * A base class `ResourceProvider` implementation that can provide resources to inject into tests.
 *
 * @param curDir      the current working directory
 * @param repository  the module repository containing the test module and dependencies
 */
@Abstract service BaseResourceProvider(Directory curDir, ModuleRepository repository)
        extends BasicResourceProvider {

    /**
     * The `FileStore` to use to access files.
     */
    @Lazy FileStore store.calc() {
        @Inject FileStore storage;
        return storage;
    }

    @Override
    Supplier getResource(Type type, String name) {
        import Container.Linker;

        switch (type, name) {

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

            default:
                return super(type, name);
            }

        case (Console, _):
            @Inject Console console;
            return console;

        case (Compiler, "compiler"):
            @Inject Compiler compiler;
            return compiler;

        case (Linker, "linker"):
            @Inject Linker linker;
            return linker;

        case (ModuleRepository, "repository"):
            return repository;
        }

        return super(type, name);
    }
}

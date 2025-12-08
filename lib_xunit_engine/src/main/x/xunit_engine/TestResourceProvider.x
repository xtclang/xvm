import ecstasy.annotations.Inject.Options;

import ecstasy.fs.DirectoryFileStore;
import ecstasy.fs.FileNode;

import ecstasy.mgmt.*;

import ecstasy.reflect.ModuleTemplate;

import xunit.MethodOrFunction;

import xunit.extensions.ExecutionContext;

import extensions.ResourceLookupExtension;

/**
 * A `ResourceProvider` implementation that can provide resources to inject into tests.
 */
service TestResourceProvider(Directory curDir)
        extends BasicResourceProvider {

    /**
     * The `FileStore` to use to access files.
     */
    @Lazy FileStore store.calc() = new DirectoryFileStore(curDir);

    private ExecutionContext? context = Null;

    private ResourceLookupExtension[] lookups = new Array();

    @Override
    Supplier getResource(Type type, String name) {
        import Container.Linker;

        switch (type, name) {
        case (TestResourceProvider, "provider"):
            return this;

        case (ExecutionContext, "context"):
            return getExecutionContext;

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

            case "tmpDir":
                return tempDir;

            case "testOutput":
                return getTestDirectory;

            default:
                return super(type, name);
            }

        case (Console, _):
            @Inject Console console;
            return console;

        case (Linker, "linker"):
            @Inject Linker linker;
            return linker;

        case (ModuleRepository, "repository"):
            @Inject ModuleRepository repository;
            return repository;
        }

        Supplier supplier = super(type, name);
        return (Options opts) -> {
            if (Object o := contextLookup(type, name, opts)) {
                return o;
            }
            if (supplier.is(ResourceSupplier)) {
                return supplier(opts);
            }
            return supplier;
        };
    }

    private conditional Object contextLookup(Type type, String name, Options opts) {
        ExecutionContext? ctx = this.context;
        if (ctx.is(ExecutionContext)) {
            if (Object resource := ctx.lookup(type, name, opts)) {
                return True, resource;
            } else {
                for (ResourceLookupExtension extension : lookups) {
                    if (Object resource := extension.lookup(type, name, opts)) {
                            return True, resource;
                    }
                }
            }
        }
        return False;
    }

    /**
     * Adds a `ResourceLookupExtension` to provide additional resources.
     */
    void addResourceLookupExtension(ResourceLookupExtension extension) = lookups.add(extension);

    /**
     * Remove a previously added `ResourceLookupExtension`.
     */
    void removeResourceLookupExtension(ResourceLookupExtension extension)
            = lookups.remove(extension);

    /**
     * Set the current ExecutionContext.
     *
     * @param context  the current ExecutionContext
     */
    void setExecutionContext(ExecutionContext? context) {
        this.context = context;
    }

    /**
     * Returns the current ExecutionContext.
     */
    ExecutionContext getExecutionContext(Options opts) {
        ExecutionContext? ctx = this.context;
        assert ctx.is(ExecutionContext);
        return ctx;
    }

    /**
     * Returns the directory to for any files specific for the current test.
     */
    Directory getTestDirectory(Options opts) {
        Directory testDir = curDir.dirFor("build/test-output");
        return testDirectoryUnder(testDir);
    }

    /**
     * Returns the directory to for any files specific for the current test.
     *
     * @param root  the root directory to place the test directory under
     *
     * @return the directory to for any files specific for the current test
     */
    Directory testDirectoryUnder(Directory root) {
        Directory         dir = root;
        ExecutionContext? ctx = this.context;

        if (ctx.is(ExecutionContext)) {
            Class? testClass = ctx.testClass;
            if (testClass.is(Class)) {
                String name = testClass.name;
                dir = dir.dirFor(testClass.name);
                MethodOrFunction? test = ctx.testMethod;
                if (test.is(Test)) {
                    dir = dir.dirFor(test.name);
                }
            }
        }
        dir.ensure();
        return dir;
    }

    /**
     * Returns the directory to for any temporary files.
     */
    Directory tempDir(Options opts) {
        Directory temp = store.root.find("_temp").as(Directory).ensure();
        return testDirectoryUnder(&temp.maskAs(Directory));
    }
}

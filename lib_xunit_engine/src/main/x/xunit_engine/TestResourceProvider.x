import ecstasy.annotations.Inject.Options;

import ecstasy.fs.DirectoryFileStore;
import ecstasy.fs.FileNode;

import ecstasy.lang.src.Compiler;

import ecstasy.mgmt.*;

import ecstasy.reflect.ModuleTemplate;

import extensions.ExtensionRegistry;

import xunit.MethodOrFunction;

import xunit.extensions.ExecutionContext;
import xunit.extensions.ResourceLookupCallback;

/**
 * A `ResourceProvider` implementation that can provide resources to inject into tests.
 *
 * @param curDir  the current working directory
 * @param outDir  the XUnit root test output directory
 */
service TestResourceProvider(Directory curDir, Directory outDir)
        extends BasicResourceProvider {

    /**
     * The `FileStore` to use to access files.
     */
    @Lazy FileStore store.calc() {
        @Inject FileStore storage;
        return storage;
    }

    /**
     * The build output directory.
     */
    @Lazy Directory buildDir.calc() = curDir.dirFor(DefaultXUnitDir);

    /**
     * The test output root directory directory.
     */
    @Lazy Directory testOutputRootDir.calc() = outDir.dirFor(TestOutputRootDir);

    /**
     * The current execution context.
     */
    private ExecutionContext? context = Null;

    /**
     * The current lookup callbacks.
     */
    private ResourceLookupCallback[] lookupCallbacks = [];

    @Override
    Supplier getResource(Type type, String name) {
        import Container.Linker;

        switch (type, name) {
        case (TestResourceProvider, _):
            return this;

        case (ResourceProvider, _):
            return &this.maskAs(ResourceProvider);

        case (ExecutionContext, _):
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

            case "buildDir":
                return buildDir;

            case "testOutputRoot":
                return testOutputRootDir;

            case "testOutput":
                return getTestDirectory;

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
        // try to lookup the resource in the current execution context
        ExecutionContext? ctx = this.context;
        if (ctx.is(ExecutionContext)) {
            if (Object resource := ctx.lookup(type, name, opts)) {
                return True, resource;
            }
        }

        // the context did not have the resource, so try any ResourceLookupCallback extensions
        for (ResourceLookupCallback callback : lookupCallbacks) {
            if (Object resource := callback.lookup(type, name, opts)) {
                return True, resource;
            }
        }
        return False;
    }

    /**
     * Set the current ExecutionContext.
     *
     * @param context  the current ExecutionContext
     */
    void setExecutionContext(ExecutionContext? context, ResourceLookupCallback[] callbacks) {
        this.context         = context;
        this.lookupCallbacks = callbacks;
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
        Directory testDir = testOutputRootDir.ensure();
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
            dir = xunit.extensions.testDirectoryFor(dir, ctx.testClass, ctx.testMethod);
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

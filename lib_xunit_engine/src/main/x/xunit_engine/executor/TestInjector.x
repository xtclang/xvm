import ecstasy.annotations.Inject.Options;

import ecstasy.mgmt.BasicResourceProvider;
import ecstasy.mgmt.ModuleRepository;
import ecstasy.mgmt.ResourceProvider;
import ecstasy.mgmt.Container.Linker;

import executor.ResourceLookupProvider;

import xunit.extensions.ExecutionContext;
import xunit.extensions.ResourceLookupCallback;

/**
 * A resource provider use in the generated test module.
 *
 * This provider tracks the current ExecutionContext so that it can inject resources based on the
 * currently executing test.
 */
service TestInjector
        extends BaseResourceProvider
        implements ResourceLookupProvider {

    construct () {
        @Inject Directory curDir;
        @Inject ModuleRepository repository;
        construct BaseResourceProvider(curDir, repository);
    }

    /**
     * The current execution context.
     */
    @Override
    public/private ExecutionContext? context = Null;

    /**
     * The current lookup callbacks.
     */
    @Override
    public/private ResourceLookupCallback[] lookupCallbacks = [];

    @Override
    ResourceProvider.Supplier getResource(Type type, String name) {

        switch (type, name) {
        case (ResourceLookupProvider, _):
            return &this.maskAs(ResourceLookupProvider);

        case (ResourceProvider, _):
            return &this.maskAs(ResourceProvider);

        case (ExecutionContext, _):
            return getExecutionContext;

        case (Directory, _):
            switch (name) {
            case "tmpDir":
                return tempDir;

            case "testOutputRoot":
                @Inject Directory testOutputRoot;
                return testOutputRoot;

            case "testOutput":
                return getTestDirectory;

            default:
                return super(type, name);
            }
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
     * @param context    the current ExecutionContext, or Null if there is no current context
     * @param callbacks  the current resource lookup callbacks
     */
     @Override
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
        @Inject Directory testOutputRoot;
        testOutputRoot.ensure();
        return testDirectoryUnder(testOutputRoot);
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
/**
 * The XUnit test framework engine module.
 */
module xunit_engine.xtclang.org {
    package collections import collections.xtclang.org;
    package xunit import xunit.xtclang.org;

    import ecstasy.mgmt.*;

    import ecstasy.reflect.ModuleTemplate;

    import ecstasy.text.Log;
    import ecstasy.text.SimpleLog;

    import tools.ModuleGenerator;

    void run(String[]? args) {
        @Inject("repository") ModuleRepository coreRepo;
        @Inject               Directory        curDir;

        if (args.is(String[])) {
            String           moduleName = args[0];
            ModuleTemplate   template   = coreRepo.getResolvedModule(moduleName);
            ModuleGenerator  gen        = new ModuleGenerator(moduleName, template);
            Directory        buildDir   = curDir.dirFor("xunit").ensure();
            ModuleRepository repo       = new LinkedRepository([new DirRepository(buildDir), coreRepo].freeze(True));
            Log              log        = new SimpleLog();

            ModuleTemplate xunitTemplate;
            if (!(xunitTemplate := gen.ensureXUnitModule(repo, buildDir, log))) {
                log.add($"Error: Failed to create a XUnit host for: {moduleName}");
                throw new Exception(log.toString());
            }

            Container container = new Container(xunitTemplate, Lightweight, repo, SimpleResourceProvider);
            container.invoke("run", Tuple:(args));
        }
    }


    static service SimpleResourceProvider
            extends BasicResourceProvider {
        @Override
        Supplier getResource(Type type, String name) {
            import Container.Linker;

            switch (type, name) {
            case (Linker, "linker"):
                @Inject Linker linker;
                return linker;
            case (ModuleRepository, "repository"):
                @Inject ModuleRepository repository;
                return repository;
            }
            return super(type, name);
        }
    }

    /**
     * An class that determines whether a test or fixture should be selected
     * during discovery.
     */
    interface TestSelectionPredicate
            extends Const {
        /**
         * Returns whether a test or fixture should be selected.
         *
         * @return `True` if the test or fixture should be selected, otherwise `False`
         */
        Boolean match(Test test);
    }

    /**
     * An class that determines whether a test or fixture should be selected
     * during discovery.
     */
    interface ModelSelectionPredicate
            extends Const {
        /**
         * Returns whether a test or fixture should be selected.
         *
         * @return `True` if the test or fixture should be selected, otherwise `False`
         */
        Boolean match(Model model);
    }
}
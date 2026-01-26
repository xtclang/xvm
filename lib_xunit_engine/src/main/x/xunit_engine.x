/**
 * The XUnit test framework execution engine module.
 *
 * This is the module that provides the execution engine to execute XUnit tests in other modules.
 * Developers only import the xunit module into their application to be able to write tests. The
 * xunit_engine module is then used internally by the XDK to execute XUnit tests. Developers should
 * not be required to import this module into their applications.
 */
module xunit_engine.xtclang.org {
    package collections import collections.xtclang.org;
    package xunit       import xunit.xtclang.org;

    import ecstasy.mgmt.Container;
    import ecstasy.mgmt.DirRepository;
    import ecstasy.mgmt.LinkedRepository;
    import ecstasy.mgmt.ModuleRepository;

    import ecstasy.reflect.ModuleTemplate;

    import ecstasy.text.Log;
    import ecstasy.text.SimpleLog;

    import tools.ModuleGenerator;

    import xunit.UniqueId;

    import xunit.extensions.ResourceRegistry;
    import xunit.extensions.ResourceRegistry.RegistrationBehavior;

    /**
     * The injection name prefix for all JSON DB configuration values.
     */
    static String ConfigPrefix = "xvm.xunit";

    /**
     * The injection name to enable the XDK debugger.
     */
    static String ConfigDebug = ConfigPrefix + ".debug";

    /**
     * The injection name for the test module to execute.
     */
    static String ConfigTestModule = ConfigPrefix + ".testModule";

    /**
     * The injection name for the test module version to execute.
     */
    static String ConfigTestModuleVersion = ConfigPrefix + ".testModuleVersion";

    /**
     * The injection name for the test build directory.
     */
    static String ConfigTestBuildDir = ConfigPrefix + ".buildDir";

    /**
     * The injection name for the test build directory.
     */
    static String ConfigTestOutputDir = ConfigPrefix + ".outputDir";

    /**
     * The default XUnit output directory located under the current directory.
     */
    static String DefaultXUnitDir = "xunit";

    /**
     * The root test output directory located under the build directory.
     */
    static String TestOutputRootDir = "test-output";

    /**
     * This is the entry point int the module to execute tests in another module.
     *
     * @params args  the arguments to execute tests. The first element in the array is the name of the
     *               module to be tested. The second element is an optional module version.
     */
    Int run(String[]? args) {
        @Inject("repository")            ModuleRepository coreRepo;
        @Inject                          Directory        curDir;
        @Inject                          Console          console;
        @Inject(ConfigTestModule)        String?          injectedModule;
        @Inject(ConfigTestModuleVersion) String?          injectedVersion;
        @Inject(ConfigDebug)             String?          debugEnabled;

        if (debugEnabled.is(String), debugEnabled == "true") {
            assert:debug;
        }

        String? moduleName    = Null;
        String? moduleVersion = Null;

        if (injectedModule.is(String)) {
            moduleName    = injectedModule;
            moduleVersion = injectedVersion;
        } else if (args.is(String[]), !args.empty) {
            moduleName    = args.size > 0 ? args[0] : Null;
            moduleVersion = args.size > 1 && args[1] != "_" ? args[1] : Null;
        }

        if (moduleName.is(String)) {
            return runTests(moduleName, moduleVersion);
        }
        console.print("XUnit: Cannot execute tests, no test module specified");
        return 1;
    }

    Int runTests(String moduleName, String? moduleVersion) {
        @Inject(ConfigTestBuildDir)  String?          buildDirName;
        @Inject(ConfigTestOutputDir) String?          outDirName;
        @Inject("repository")        ModuleRepository coreRepo;
        @Inject                      Directory        curDir;
        @Inject                      Console          console;

        Directory outDir   = outDirName.is(String)
                                    ? curDir.dirFor(outDirName)
                                    : curDir.dirFor(DefaultXUnitDir);

        Directory buildDir = buildDirName.is(String)
                                    ? curDir.dirFor(buildDirName)
                                    : outDir;

        outDir.ensure();
        buildDir.ensure();

        Version?         version   = moduleVersion.is(String) ? new Version(moduleVersion) : Null;
        DirRepository    buildRepo = new DirRepository(buildDir);
        ModuleRepository repo      = new LinkedRepository([buildRepo, coreRepo].freeze(True));
        Log              log       = new SimpleLog();

        console.print($"XUnit: Creating test module for {moduleName} in {buildDir}");
        ModuleGenerator gen = new ModuleGenerator(moduleName, version);
        if (ModuleTemplate template := gen.ensureModule(repo, buildDir, log)) {
            console.print($"XUnit: Created test module {template.qualifiedName} in {buildDir}");
            TestResourceProvider injector = new TestResourceProvider(curDir, outDir);
            Tuple t = new Container(template, Lightweight, repo, injector).invoke("run");
            // The temporary test module can now be deleted
            if (File moduleFile := buildDir.findFile(template.parent.name)) {
            	moduleFile.delete();
            }
            // the run method in the test module returns True if the tests pass, otherwise false.
            // we then turn this into an exit code, 0 for success, or 1 for failure.
            return t[0].as(Boolean) ? 0 : 1;
        } else {
            log.add($"Error: Failed to create a host for: {moduleName}");
            throw new Exception(log.toString());
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

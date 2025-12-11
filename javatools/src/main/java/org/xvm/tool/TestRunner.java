package org.xvm.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.xvm.api.Connector;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.tool.LauncherOptions.RunnerOptions;
import org.xvm.tool.LauncherOptions.TestRunnerOptions;


/**
 * The "xtc test" command - runs tests in an Ecstasy module using the xunit engine.
 * <p>
 * Usage:
 * <pre>
 *   xtc test [-L repo(s)] module.xtc
 * </pre>
 * <p>
 * The TestRunner extends Runner but loads the xunit_engine module and injects the
 * test module information, allowing the xunit framework to discover and run tests.
 */
public class TestRunner extends Runner {

    /**
     * The XUnit engine module name.
     */
    public static final String XUNIT_MODULE = "xunit_engine.xtclang.org";

    /**
     * The name of the XUnit injectable key used to specify the test module name.
     */
    public static final String XUNIT_MODULE_ARG = "xvm.xunit.testModule";

    /**
     * The name of the XUnit injectable key used to specify the test module version.
     */
    public static final String XUNIT_MODULE_VERSION_ARG = "xvm.xunit.testModuleVersion";

    /**
     * The name of the XUnit injectable key used to specify specific test classes to run.
     */
    public static final String XUNIT_TEST_CLASSES_ARG = "xvm.xunit.discovery.testClass";

    /**
     * The name of the XUnit injectable key used to specify specific test groups to run.
     */
    public static final String XUNIT_TEST_GROUPS_ARG = "xvm.xunit.discovery.testGroup";

    /**
     * The name of the XUnit injectable key used to specify specific test packages to run.
     */
    public static final String XUNIT_TEST_PACKAGES_ARG = "xvm.xunit.discovery.testPackage";

    /**
     * The name of the XUnit injectable key used to specify specific test methods to run.
     */
    public static final String XUNIT_TEST_METHODS_ARG = "xvm.xunit.discovery.test";

    private static final String COMMAND_NAME = "test";

    /**
     * Entry point from the OS.
     *
     * @param args command line arguments
     */
    static void main(final String[] args) {
        Launcher.main(insertCommand(COMMAND_NAME, args));
    }

    /**
     * TestRunner constructor for programmatic invocation.
     *
     * @param options     the runner options (RunnerOptions or TestRunnerOptions)
     * @param console     representation of the terminal within which this command is run
     * @param errListener optional error listener for programmatic error access
     */
    public TestRunner(final TestRunnerOptions options, final Console console, final ErrorListener errListener) {
        super(options, console, errListener);
    }

    /**
     * @return the command name for this launcher
     */
    public static String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    protected Connector createConnector(final ModuleRepository repo, final ModuleStructure module) {
        // Create connector with optional JIT
        final RunnerOptions options = options();
        final Connector connector = createBaseConnector(repo, options.isJit());

        // Load the xunit engine module (it will load the test module as a dependency)
        connector.loadModule(XUNIT_MODULE);

        // Inject the test module information so xunit can discover and run tests
        final var injections    = new LinkedHashMap<>(options.getInjections());
        final var moduleVersion = module.getVersionString();
        injections.putAll(Map.of(
            XUNIT_MODULE_ARG, List.of(module.getName()),
            XUNIT_MODULE_VERSION_ARG, moduleVersion == null ? List.of() : List.of(moduleVersion)));

        connector.start(injections);
        return connector;
    }

    @Override
    public String desc() {
        return """
            Ecstasy test runner:

                Executes the tests in an Ecstasy module using the xunit framework,
                compiling the module first if necessary.

                Also supports:
                    <filename>, <filename>.x, or <filename>.xtc""";
    }
}

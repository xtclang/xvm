package org.xvm.tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.api.Connector;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.javajit.JitConnector;

/**
 * The "xtc test" command:
 * <pre>
 *  java org.xvm.tool.TestRunner [-L repo(s)] app.xtc
 * </pre>
 */
public class TestRunner
        extends Runner {

    /**
     * The XUnit module name.
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

    public TestRunner(List<String> listArg, Console console) {
        super(listArg, console);
    }

    @Override
    protected Connector createConnector(ModuleRepository repo, ModuleStructure module) {
        Options options = options();
        boolean fJit    = options.isJit();
        Connector connector = fJit ? new JitConnector(repo) : new Connector(repo);
        connector.loadModule(XUNIT_MODULE);
        Map<String, Object> injections = new HashMap<>(options.getInjections());
        injections.put(XUNIT_MODULE_ARG, module.getName());
        injections.put(XUNIT_MODULE_VERSION_ARG, module.getVersionString());
        connector.start(injections);
        return connector;
    }

    /**
     * Entry point from the OS.
     *
     * @param asArg command line arguments
     */
    public static void main(String[] asArg) {
        try {
            // use System.exit() to communicate the result of execution back to the caller
            System.exit(launch(asArg));
        } catch (LauncherException e) {
            System.exit(e.error ? 1 : 0);
        }
    }

    /**
     * Helper method for external launchers.

     * @param asArg  command line arguments
     *
     * @return the result of the {@link #process()} call
     *
     * @throws LauncherException if an unrecoverable exception occurs
     */
    public static int launch(String[] asArg) throws LauncherException {
        return new TestRunner(asArg == null ? List.of() : List.of(asArg), null).run();
    }

    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc() {
        return """
            Ecstasy test runner:

                Executes the tests in an Ecstasy module, compiling it first if necessary.

            Usage:

                xtc test <options> <moduleName> [<moduleVersion>]
            
            Also supports any of:
            
                xtc test <options> <filename>
                xtc test <options> <filename>.x
                xtc test <options> <filename>.xtc
            """;
    }

    // ----- options -------------------------------------------------------------------------------

    @Override
    public Options options() {
        return (Options) super.options();
    }

    @Override
    protected Options instantiateOptions() {
        return new Options();
    }

    /**
     * Runner command-line options implementation.
     */
    public class Options
            extends Runner.Options {

        public Options() {
            super();
            // The test runner does not support the "method" option inherited from the Runner
            options().remove("M");
            options().remove("method");

            addOption("c", "test-class", Form.String,   true, "the fully qualified name of a " +
                    "class to execute tests in", "xvm.xunit.discovery.testClass");
            addOption("g", "test-group", Form.String,   true, "only execute tests with the " +
                    "specified @Test annotation group", "xvm.xunit.discovery.testGroup");
            addOption("p", "test-package", Form.String,   true, "the name of a package to execute" +
                    " tests in", "xvm.xunit.discovery.testPackage");
            addOption("t", "test-method", Form.String,   true, "the fully qualified name of a " +
                    "test method to execute", "xvm.xunit.discovery.test");
        }

        @Override
        public String getMethodName() {
            return "run";
        }
    }
}

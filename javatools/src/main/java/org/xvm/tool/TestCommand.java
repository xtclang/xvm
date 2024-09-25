package org.xvm.tool;

import org.xvm.api.Connector;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ModuleStructure;
import org.xvm.tool.flag.FlagSet;
import org.xvm.util.Severity;

/**
 * This is the command-line Ecstasy test command "xtc test".
 */
public class TestCommand
        extends BaseRunCommand
    {
    /**
     * Test runner constructor.
     *
     * @param sName    the name of this command (as specified on the command line)
     * @param console  representation of the terminal within which this command is run
     */
    public TestCommand(String sName, Console console)
        {
        super(sName, console);
        }

    @Override
    protected void invoke(Connector connector, ConstantPool pool, ModuleStructure module)
        {
        if (!connector.invokeTest0())
            {
            log(Severity.WARNING, "No tests executed, module " + module.getName()
                    + " does not import XUnit or a suitable test executor");
            }
        }

    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc()
        {
        return """
            Ecstasy test runner:

            Executes the tests in an Ecstasy module, compiling it first if necessary.
            Supports running tests in any of the following file types:

                <filename>      - assumed to be a compiled module file name
                <filename>.x    - an Ecstasy module source file name
                <filename>.xtc  - a compiled module file name""";
        }

    @Override
    protected String getShortDescription()
        {
        return "Executes the tests in an Ecstasy module, compiling it first if necessary.";
        }

    @Override
    protected String getUsageLine(String sName)
        {
        return sName + " [flags] <filename>";
        }

    // ----- command line flags --------------------------------------------------------------------

    @Override
    protected FlagSet instantiateFlags(String sName)
        {
        return super.instantiateFlags(sName)
                .withStringList(ARG_TEST_PATTERN, 't',
                        "A regex pattern to use to select specific tests to include, or to exclude if the pattern starts with a '!'",
                        false, true, "xunit.test_patterns");
        }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * The command line flag used to specify a pattern to use to identify tests to execute.
     */
    public static final String ARG_TEST_PATTERN = "test";
    }
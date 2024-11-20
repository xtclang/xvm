package org.xvm.tool;

import org.xvm.tool.flag.FlagSet;

/**
 * The XTC command launcher.
 * <p>
 * This is a parent launcher that has a number of sub-commands for running
 * various Ecstasy command line tools.
 */
public class XtcCommand
        extends AbstractCommand
    {
    /**
     * Entry point from the OS.
     *
     * @param asArg command line arguments
     */
    public static void main(String[] asArg)
        {
        try
            {
            Launcher.launch(XtcCommand.XTC_COMMAND, asArg);
            }
        catch (LauncherException e)
            {
            System.exit(e.error ? -1 : 0);
            }
        }

    /**
     * Create an XTC launcher.
     */
    public XtcCommand()
        {
        this(null);
        }

    /**
     * Create an XTC launcher.
     *
     * @param console  an optional {@link Console}
     */
    public XtcCommand(Console console)
        {
        super(XTC_COMMAND, console);
        addCommands(createBuildCommand(), createRunCommand(), createTestCommand(), createInfoCommand());
        }

    @Override
    protected void process()
        {
        displayHelp();
        }

    @Override
    public String desc()
        {
        return "The Ecstasy command line tool.";
        }

    @Override
    protected String getShortDescription()
        {
        return desc();
        }

    @Override
    protected String getUsageLine(String sName)
        {
        return sName + " [command] [flags]";
        }

    @Override
    protected FlagSet instantiateFlags(String sName)
        {
        return new FlagSet()
                .withModulePath(true);
        }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Create the {@link Launcher} to call for the "build" command.
     *
     * @return the {@link Launcher} to call for the "build" command
     */
    protected AbstractCommand createBuildCommand()
        {
        return new Compiler(XTC_COMMAND_BUILD, m_console);
        }

    /**
     * Create the {@link Launcher} to call for the "run" command.
     *
     * @return the {@link Launcher} to call for the "run" command
     */
    protected AbstractCommand createRunCommand()
        {
        return new Runner(XTC_COMMAND_RUN, m_console);
        }

    /**
     * Create the {@link Launcher} to call for the "test" command.
     *
     * @return the {@link Launcher} to call for the "test" command
     */
    protected AbstractCommand createTestCommand()
        {
        return new TestCommand(XTC_COMMAND_TEST, m_console);
        }

    /**
     * Create the {@link Launcher} to call for the "info" command.
     *
     * @return the {@link Launcher} to call for the "info" command
     */
    protected AbstractCommand createInfoCommand()
        {
        return new Disassembler(XTC_COMMAND_INFO, m_console);
        }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * The XTC launcher executable name.
     */
    public static final String XTC_COMMAND = "xtc";

    /**
     * The xtc sub command to run the compiler (equivalent to the xcc command).
     */
    public static final String XTC_COMMAND_BUILD = "build";

    /**
     * The xtc sub command to run the runner (equivalent to the xec command).
     */
    public static final String XTC_COMMAND_RUN = "run";

    /**
     * The xtc sub command to run the runner (equivalent to the xec command).
     */
    public static final String XTC_COMMAND_TEST = "test";

    /**
     * The xtc sub command to run the info (equivalent to the xam command).
     */
    public static final String XTC_COMMAND_INFO = "info";
    }

package org.xvm.tool;


import java.util.List;

import org.xvm.asm.ErrorListener;

import org.xvm.tool.flag.FlagSet;

/**
 * The base class for "launcher" commands.
 * <p/>
 * The Launcher is effectively just a main entry point containing the
 * various "commands" that the launcher can execute.
 *
 * <ul><li> <code>xcc</code> <i>("ecstasy")</i> routes to {@link Compiler}
 * </li><li> <code>xec</code> <i>("exec")</i> routes to {@link Runner}
 * </li><li> <code>xtc</code> routes to {@link XtcCommand}
 * </li></ul>
 */
public class Launcher
        extends AbstractCommand
        implements ErrorListener
    {
    /**
     * Create a new {@link Launcher} instance
     */
    public Launcher()
        {
        super("");

        // the global options common to all subcommands
        globalFlags()
                .withHelp()
                .withVerboseFlag()
                .withVersionFlag();

        // Add the launcher sub-commands
        addCommand(new Compiler())                 // xcc
                .addCommand(new Runner())          // xec
                .addCommand(new Disassembler())    // xam
                .addCommand(new XtcCommand());     // xtc
        }

    /**
     * Entry point from the OS.
     *
     * @param asArg  command line arguments
     */
    public static void main(String[] asArg)
        {
        List<String> listArg = asArg == null ? List.of() : List.of(asArg);
        launch(null, listArg);
        }

    /**
     * Entry point to execute the {@link Launcher}
     * with a command and arguments.
     *
     * @param sCommand  the name of the command to execute, or {@code null}
     *                  if the command name is in the {@code asArg} array
     * @param asArg     the array of command line arguments
     */
    public static void launch(String sCommand, String... asArg)
        {
        launch(sCommand, List.of(asArg));
        }

    /**
     * Entry point to execute the {@link Launcher}
     * with a command and arguments.
     *
     * @param sCommand  the name of the command to execute, or {@code null}
     *                  if the command name is in the {@code asArg} array
     * @param listArg   the array of command line arguments
     */
    public static void launch(String sCommand, List<String> listArg)
        {
        new Launcher().run(sCommand, listArg);
        }

    // ----- AbstractCommand methods ---------------------------------------------------------------

    @Override
    protected FlagSet instantiateFlags(String sName)
        {
        return new FlagSet().withModulePath(true);
        }

    @Override
    protected void process()
        {
        // we should never get here in the root launcher,
        // but if we do then just display the help
        displayHelp();
        }

    @Override
    public String desc()
        {
        return "The Ecstasy command line launcher";
        }

    @Override
    protected String getShortDescription()
        {
        return "The Ecstasy command line launcher";
        }

    @Override
    protected String getUsageLine(String sName)
        {
        return sName + " [command] [flags]";
        }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * Unknown fatal error. {0}
     */
    public static final String FATAL_ERROR      = "LAUNCHER-01";
    /**
     * Duplicate name "{0}" detected in {1}
     */
    public static final String DUP_NAME         = "LAUNCHER-02";
    /**
     * No package node for {0}
     */
    public static final String MISSING_PKG_NODE = "LAUNCHER-03";
    /**
     * Failure reading: {0}
     */
    public static final String READ_FAILURE     = "LAUNCHER-04";
    }
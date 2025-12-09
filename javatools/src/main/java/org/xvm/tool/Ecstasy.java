package org.xvm.tool;


/**
 * The unified "xtc" command - a dispatcher for Ecstasy tools.
 * <p>
 * Supports subcommands:
 * <ul>
 *   <li>{@code xtc build} - Compile Ecstasy source files (delegates to Compiler/xcc)</li>
 *   <li>{@code xtc run} - Execute an Ecstasy module (delegates to Runner/xec)</li>
 *   <li>{@code xtc test} - Run tests using xunit framework (delegates to TestRunner)</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 *   xtc &lt;command&gt; [options] [arguments]
 *   xtc --help
 *   xtc --version
 * </pre>
 */
public class Ecstasy {

    /**
     * Command name for dispatch.
     */
    public static final String COMMAND_NAME = "xtc";

    /**
     * Entry point from the OS.
     *
     * @param args command line arguments (subcommand followed by subcommand options)
     */
    public static void main(final String[] args) {
        Launcher.main(Launcher.insertCommand(COMMAND_NAME, args));
    }
}

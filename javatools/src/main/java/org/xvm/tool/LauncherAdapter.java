package org.xvm.tool;

import org.xvm.asm.ErrorListener;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.LauncherOptions.DisassemblerOptions;
import org.xvm.tool.LauncherOptions.RunnerOptions;

/**
 * ServiceLoader-discoverable adapter that enables Launcher to be invoked
 * without reflection using the Launchable interface.
 *
 * <p>This single adapter works for all launcher types (Compiler, Runner, Disassembler)
 * by delegating to the existing Launcher.launch() programmatic API.
 *
 * <p>Registered in META-INF/services/org.xvm.tool.Launchable for ServiceLoader discovery.
 */
public final class LauncherAdapter implements Launchable {

    @Override
    public int launch(final LauncherOptions options, final Console console, final ErrorListener errorListener) {
        // Determine command name from options type
        final String command = getCommandName(options);

        // Convert options to command-line args
        final String[] args = options.toCommandLine();

        // Delegate to existing Launcher API - programmatic invocation with custom console/errorListener
        return Launcher.launch(command, args, console, errorListener);
    }

    /**
     * Determines the launcher command name from the options type.
     *
     * @param options The launcher options
     * @return The command name ("xcc", "xec", or "xtc")
     */
    private String getCommandName(final LauncherOptions options) {
        if (options instanceof CompilerOptions) {
            return Compiler.COMMAND_NAME;
        }
        if (options instanceof RunnerOptions) {
            return Runner.COMMAND_NAME;
        }
        if (options instanceof DisassemblerOptions) {
            return Disassembler.COMMAND_NAME;
        }
        throw new IllegalArgumentException("Unknown options type: " + options.getClass().getName());
    }
}

package org.xtclang.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.process.ExecResult;

import java.util.Objects;

// TODO: Finish the support for native launchers from the local System PATH, and implement the "nativeLauncher = true" flag.
@SuppressWarnings("unused")
public class NativeBinaryLauncher extends XtcLauncher {

    private final String commandName;

    NativeBinaryLauncher(final Project project, final String commandName) {
        super(project, commandName);
        this.commandName = commandName;
    }

    @Override
    public ExecResult apply(final CommandLine args) {
        Objects.requireNonNull(args);
        warn("{} XTC Plugin will launch '{}' by executing a native process (not officially supported yet).", prefix, commandName);
        final var builder = XtcExecResult.builder(getClass(), args);
        return createExecResult(builder.execResult(project.exec(spec -> {
            spec.setStandardOutput(builder.getOut());
            spec.setErrorOutput(builder.getErr());
            spec.setExecutable(commandName);
            spec.setArgs(args.toList());
            spec.setIgnoreExitValue(true);
        })));
    }
}

package org.xtclang.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.internal.DefaultXtcTaskExtension;

import java.io.File;
import java.util.Objects;
import java.util.StringTokenizer;

// TODO: Finish the support for native launchers from the local System PATH, and implement the "nativeLauncher = true" flag.
//   This is code complete but should be tested.
@SuppressWarnings("unused")
public class NativeBinaryLauncher extends XtcLauncher {

    private final String commandName;

    NativeBinaryLauncher(final Project project, final String commandName, final boolean logOutputs) {
        super(project, commandName, logOutputs);
        this.commandName = commandName;
    }

    @Override
    protected boolean validateCommandLine(final CommandLine cmd) {
        final var mainClassName = cmd.getMainClassName();
        final var jvmArgs = cmd.getJvmArgs();
        if (DefaultXtcTaskExtension.hasModifiedJvmArgs(jvmArgs)) {
            warn("{} WARNING: XTC Plugin '{}' has non-default JVM args ({}). These will be ignored, as we are running a native launcher.", prefix, mainClassName, jvmArgs);
        }
        return super.validateCommandLine(cmd);
    }

    private File findOnPath(final String commandName) {
        final var path = Objects.requireNonNull(System.getenv("PATH"));
        final var st = new StringTokenizer(path, File.pathSeparator);
        while (st.hasMoreTokens()) {
            final var cmd = new File(st.nextToken(), commandName);
            if (cmd.exists() && cmd.canExecute()) {
                info("{} Successfully resolved path for command '{}': '{}'", prefix, commandName, cmd.getAbsolutePath());
                return cmd;
            }
        }
        throw buildException("Could not resolve " + commandName + " from system path: " + path);
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        validateCommandLine(cmd);
        final var builder = resultBuilder(cmd);
        return createExecResult(builder.execResult(project.exec(spec -> {
            if (logOutputs) {
                spec.setStandardOutput(builder.getOut());
                spec.setErrorOutput(builder.getErr());
            }
            spec.setExecutable(findOnPath(commandName));
            spec.setArgs(cmd.toList());
            spec.setIgnoreExitValue(true);
        })));
    }
}

package org.xvm.plugin;

import org.gradle.process.ExecResult;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Objects;

// TODO: Finish the support for native launchers from the local System PATH, and implement the "nativeLauncher = true" flag.
@SuppressWarnings("unused")
public class NativeBinaryLauncher extends XtcLauncher {

    private final String commandName;

    NativeBinaryLauncher(final XtcProjectDelegate delegate, final String commandName) {
        super(delegate.getProject());
        this.commandName = commandName;
    }

    @Override
    public ExecResult apply(final CommandLine args) {
        Objects.requireNonNull(args);
        warn("{} XTC Plugin will launch '{}' by executing a native process.", prefix, commandName);
        final var oldOut = System.out;
        final var oldErr = System.err;
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out));
            System.setErr(new PrintStream(out));
            project.exec(spec -> {
                spec.setExecutable(commandName);
                spec.setArgs(args.toList());
            });
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
            showOutput(args, out.toString(), err.toString());
        }
        return XtcExecResult.OK;
    }
}

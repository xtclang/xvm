package org.xvm.plugin;

import org.gradle.process.ExecResult;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

public class BuildThreadLauncher extends XtcLauncher {

    private final Method main;

    BuildThreadLauncher(final XtcProjectDelegate delegate, final String mainClassName) {
        super(delegate.getProject());
        this.main = resolveMethod(mainClassName);
        lifecycle("{} Running {} in build process; this is not recommended for production use.", prefix, mainClassName);
    }

     private Method resolveMethod(final String className) {
        try {
            return Class.forName(className).getMethod("main", String[].class);
        } catch (final ClassNotFoundException | NoSuchMethodException e) {
            info("{} Failed to resolve method 'main' in class '{}', launching with JavaExec.", prefix, className);
            return null;
        }
    }

    @Override
    protected ExecResult apply(final CommandLine args) {
        Objects.requireNonNull(args);
        warn("{} XTC Plugin will launch '{}' from the plugin process.", prefix, args.getMainClassName());
        final var oldOut = System.out;
        final var oldErr = System.err;
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out));
            System.setErr(new PrintStream(out));
            main.invoke(null, (Object)args.toList().toArray(new String[0]));
        } catch (final IllegalAccessException | InvocationTargetException e) {
            return new XtcExecResult(1, e);
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
            showOutput(args, out.toString(), err.toString());
        }
        return XtcExecResult.OK;
    }
}

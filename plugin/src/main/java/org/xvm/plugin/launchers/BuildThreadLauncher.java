package org.xvm.plugin.launchers;

import org.gradle.process.ExecResult;
import org.xvm.plugin.XtcProjectDelegate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

public class BuildThreadLauncher extends XtcLauncher {
    private final Method main;

    @SuppressWarnings("FieldCanBeLocal")
    private final Method moduleInfo;

    BuildThreadLauncher(final XtcProjectDelegate delegate, final String mainClassName) {
        super(delegate.getProject());
        this.main = resolveMethod(mainClassName, "main", String[].class);
        this.moduleInfo = resolveMethod("org.xvm.tool.ModuleInfo", "extractModuleName", File.class);
        lifecycle("{} Resolved module info: {}.{}", prefix, moduleInfo.getClass().getSimpleName(), moduleInfo.getName());
        lifecycle("{} Running {} in build process; this is not recommended for production use.", prefix, mainClassName);
    }

    @Override
    public ExecResult apply(final CommandLine args) {
        Objects.requireNonNull(args);
        warn("{} WARNING: XTC Plugin will launch '{}' from its build process. No JavaExec/Exec will be performed.", prefix, args.getMainClassName());
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

    private Method resolveMethod(final String className, final String methodName, final Class<?>... parameterTypes) {
        try {
            return Class.forName(className).getMethod(methodName, parameterTypes);
        } catch (final ClassNotFoundException | NoSuchMethodException e) {
            throw buildException("{} Failed to resolve method '{}' in class '{}' ({}).", prefix, methodName, className, e.getMessage());
        }
    }
}

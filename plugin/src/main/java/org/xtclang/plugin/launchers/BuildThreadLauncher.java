package org.xtclang.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.internal.DefaultXtcLauncherTaskExtension;
import org.xtclang.plugin.launchers.XtcExecResult.XtcExecResultBuilder;
import org.xtclang.plugin.tasks.XtcLauncherTask;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;

public class BuildThreadLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> extends XtcLauncher<E, T> {
    /**
     * The launcher invocation method, in a variant that cannot do System.exit(), to safeguard the
     * "fork = false" configuration, which should be used for debugging purposes only.
     */
    private static final String LAUNCH_METHOD_NAME = "launch";
    private static final Class<?> LAUNCH_METHOD_PARAMS = String[].class;

    private final Method main;

    public BuildThreadLauncher(final Project project, final T task) {
        super(project, task);
        this.main = resolveMethod(task);
    }

    @Override
    protected boolean validateCommandLine(final CommandLine cmd) {
        Objects.requireNonNull(cmd);
        final var mainClassName = cmd.getMainClassName();
        final var jvmArgs = cmd.getJvmArgs();
        logger.info("{} WARNING: Task will launch '{}' from its build process. No JavaExec/Exec will be performed.", prefix, mainClassName);
        if (DefaultXtcLauncherTaskExtension.hasModifiedJvmArgs(jvmArgs)) {
            logger.warn("{} WARNING: Task has non-default JVM args ({}). These will be ignored, as launcher is configured not to fork.", prefix, jvmArgs);
        }
        return false;
    }

    private static PrintStream printStream(final OutputStream out) {
        return out instanceof PrintStream ? (PrintStream)out : new PrintStream(out);
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        logger.info("{} Launching task {}}", prefix, this);

        validateCommandLine(cmd);

        final var oldIn = System.in;
        final var oldOut = System.out;
        final var oldErr = System.err;
        final var builder = resultBuilder(cmd);
        try {
            if (task.hasVerboseLogging()) {
                logger.lifecycle("{} WARNING: (equivalent to what we are executing without forking in current thread) JavaExec command: {}", prefix, cmd.toString());
            }
            // TODO: Rewrite super.redirectIo so we can reuse it here. That is prettier. Push and pop streams to field?
            //   (may be even more readable to implement it as a try-with-resources of some kind)
            if (task.hasStdinRedirect()) {
                System.setIn(task.getStdin().get());
            }
            if (task.hasStdoutRedirect()) {
                System.setOut(printStream(task.getStdout().get()));
            }
            if (task.hasStderrRedirect()) {
                System.setErr(printStream(task.getStderr().get()));
            }
            main.invoke(null, (Object)cmd.toList().toArray(new String[0]));
            builder.exitValue(0);
        } catch (final IllegalAccessException e) {
            throw buildException("Failed to invoke '{}.{}' through reflection: {}", main.getDeclaringClass().getName(), main.getName(), e.getMessage());
        } catch (final Throwable t) {
            handleThrowable(builder, t);
        } finally {
            System.setIn(oldIn);
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        return createExecResult(builder);
    }

    private void handleThrowable(final XtcExecResultBuilder builder, final Throwable t) {
        final var cause = t.getCause();
        if (cause == null) {
            throw buildException("Unexpected throwable from invocation to '{}.{}' through reflection: {}",
                main.getDeclaringClass().getName(), main.getName(), t.getMessage());
        }

        // Check if cause was a launcher exception.
        //
        // TODO: This is a rather hacky way of checking the LauncherException.
        //   We do not want to refer to classes outside the plugin at compile time, because we
        //   may not always (actually quite seldom) want to bundle the plugin with its javatools.jar.
        //   Doing so, however, gives us some convenient ways to quickly debug a build, but it
        //   has a bit of a dependency skew code smell to it, along with the extra copy of the
        //   javatools, so we prefer to avoid it if can, or at least never assume we have
        //   compile time access to javatools in the plugin.
        final boolean isError = cause.toString().contains("isError=true");
        logger.warn("{} LauncherException caught in {}, isError={}", prefix, getClass().getSimpleName(), isError, cause);
        builder.exitValue(isError ? -1 : 0);
        if (isError) {
            builder.failure(cause);
        }
    }

    @SuppressWarnings("unused")
    private Class<?> dynamicallyLoadJar(final File jar, final String className) throws IOException {
        try (final var classLoader = new URLClassLoader(new URL[]{jar.toURI().toURL()})) {
            return classLoader.loadClass(className);
        } catch (final ClassNotFoundException e) {
            throw buildException(e, "Failed to load class from jar '{}': {}", jar, e.getMessage());
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static Method resolveMethod(final XtcLauncherTask<?> task) {
        try {
            return Class.forName(task.getJavaLauncherClassName()).getMethod(LAUNCH_METHOD_NAME, LAUNCH_METHOD_PARAMS);
        } catch (final ClassNotFoundException | NoSuchMethodException e) {
            throw task.buildException(e, "Failed to resolve method '{}' in class '{}': {}.", LAUNCH_METHOD_NAME, task.getJavaLauncherClassName(), e.getMessage());
        }
    }
}

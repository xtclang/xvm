package org.xtclang.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.XtcBuildException;
import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.internal.DefaultXtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;

public class BuildThreadLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> extends XtcLauncher<E, T> {
    /**
     * The launcher invocation method, in a variant that cannot do System.exit(), to safeguard the
     * "fork = false" configuration, which should be used for debugging purposes only.
     * <p>
     * TODO: The "illegal state exception" on abort seems to create an infinite stack stream, during compilation.
     * Probably because it gets caught up in some internal chain of similar exceptions, not following the principle
     * of least astonishment. To reproduce - add a syntax error and have the compiler crash on a non forking build.
     */
    private static final String INVOKE_METHOD_NAME_NO_SYSTEM_EXIT = "call";

    private final Method main;

    public BuildThreadLauncher(final Project project, final T task) {
        super(project, task);
        this.main = resolveMethod(project, task.getJavaLauncherClassName(), INVOKE_METHOD_NAME_NO_SYSTEM_EXIT, String[].class);
    }

    @Override
    protected boolean validateCommandLine(final CommandLine cmd) {
        Objects.requireNonNull(cmd);
        final var mainClassName = cmd.getMainClassName();
        final var jvmArgs = cmd.getJvmArgs();
        warn("{} WARNING: XTC Plugin Task '{}' will launch '{}' from its build process. No JavaExec/Exec will be performed.", prefix, task.getName(), mainClassName);
        if (DefaultXtcLauncherTaskExtension.hasModifiedJvmArgs(jvmArgs)) {
            warn("{} WARNING: XTC Plugin '{}' Task '{}' has non-default JVM args ({}). These will be ignored, as launcher is configured not to fork.", prefix, task.getName(), mainClassName, jvmArgs);
        }
        return false;
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        logger.info("{} Launching '{}' task: {}}", prefix, task.getName(), this);

        validateCommandLine(cmd);

        final var oldIn = System.in;
        final var oldOut = System.out;
        final var oldErr = System.err;
        final var builder = resultBuilder(cmd);
        try {
            if (hasVerboseLogging()) {
                lifecycle("{} (equivalent) JavaExec command: {}", prefix, cmd.toString());
            }
            if (shouldRedirectOutputToLogger()) {
                System.setOut(new PrintStream(builder.getOut()));
                System.setErr(new PrintStream(builder.getErr()));
                if (task.hasOutputRedirects()) {
                    warn("{} WARNING: Task '{}' is already configured to override stdout and/or stderr. It may cause problems to redirect them to the build log.", prefix, task.getName());
                }
            }
            // TODO: Rewrite super.redirectIo so we can reuse it here. That is prettier. Push and pop streams to field?
            //   (may be even more readable to implement it as a try-with-resources of some kind)
            if (task.hasStdinRedirect()) {
                System.setIn(task.getStdin().get());
            }
            if (task.hasStdoutRedirect()) {
                System.setOut(new PrintStream(task.getStdout().get()));
            }
            if (task.hasStderrRedirect()) {
                System.setErr(new PrintStream(task.getStderr().get()));
            }
            main.invoke(null, (Object)cmd.toList().toArray(new String[0]));
            builder.exitValue(0);
        } catch (final IllegalAccessException e) {
            throw buildException("Failed to invoke '{}.{}' through reflection: {}", main.getDeclaringClass().getName(), main.getName(), e.getMessage());
        } catch (final Throwable e) {
            final var cause = e.getCause();
            if (cause instanceof IllegalStateException) {
                final var launcherError = Boolean.parseBoolean(cause.getMessage());
                builder.exitValue(launcherError ? -1 : 0); // If there was an abort without error, silently ignore this exception and treat it like System.exit(0)
                if (launcherError) {
                    builder.failure(cause); // Flag an abnormal Console.abort and an ExecException, as for the JavaExec launcher or native launcher.
                }
            } else {
                throw buildException("Unexpected exception from invocation to '{}.{}' through reflection: {}", main.getDeclaringClass().getName(), main.getName(), e.getMessage());
            }
        } finally {
            System.setIn(oldIn);
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        return createExecResult(builder);
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
    private static Method resolveMethod(final Project project, final String className, final String methodName, final Class<?>... parameterTypes) {
        try {
            return Class.forName(className).getMethod(methodName, parameterTypes);
        } catch (final ClassNotFoundException | NoSuchMethodException e) {
            throw XtcBuildException.buildException(project.getLogger(), e, "Failed to resolve method '{}' in class '{}' ({}).", methodName, className, e.getMessage());
        }
    }
}

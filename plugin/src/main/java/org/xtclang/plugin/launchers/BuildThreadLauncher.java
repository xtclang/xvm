package org.xtclang.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.process.ExecResult;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;

public class BuildThreadLauncher extends XtcLauncher {
    /**
     * The launcher invocation method, in a variant that cannot do System.exit, to safeguard the
     * "fork = false" configuration, which should be used for debugging purposes only.
     */

    private static final String INVOKE_METHOD_NAME_NO_SYSTEM_EXIT = "call";

    private final Method main;

    BuildThreadLauncher(final Project project, final String mainClassName) {
        super(project, "In-process: " + mainClassName);
        this.main = resolveMethod(mainClassName, INVOKE_METHOD_NAME_NO_SYSTEM_EXIT, String[].class);
    }

    @Override
    public ExecResult apply(final CommandLine args) {
        Objects.requireNonNull(args);
        warn("{} WARNING: XTC Plugin will launch '{}' from its build process. No JavaExec/Exec will be performed.", prefix, args.getMainClassName());
        final var oldOut = System.out;
        final var oldErr = System.err;
        final var builder = XtcExecResult.builder(getClass(), args);
        try {
            System.setOut(new PrintStream(builder.getOut()));
            System.setErr(new PrintStream(builder.getErr()));
            main.invoke(null, (Object)args.toList().toArray(new String[0]));
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
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        return createExecResult(builder);
    }

    @SuppressWarnings("unused")
    private Class<?> dynamicallyLoadJar(final File jar, final String className) throws IOException {
        /* TODO better way to resolve and dynamically load the javatools jar?
        val resolvePlugin by tasks.registering {
            doLast {
                val file: File = project.projectDir.toPath().resolve(tasks.jar.get().archiveFile.get().toString()).toFile()
                val classloader: ClassLoader = URLClassLoader(arrayOf(file.toURI().toURL()))
                Class.forName("mypackage.MyClass", true, classloader).getMethod("sayHello").invoke(null)
            }
        }*/
        try (final var classLoader = new URLClassLoader(new URL[]{jar.toURI().toURL()})) {
            return classLoader.loadClass(className);
        } catch (final ClassNotFoundException e) {
            throw buildException("Failed to load class from jar '{}': {}", jar, e.getMessage());
        }
    }

    private Method resolveMethod(final String className, final String methodName, final Class<?>... parameterTypes) {
        try {
            return Class.forName(className).getMethod(methodName, parameterTypes);
        } catch (final ClassNotFoundException | NoSuchMethodException e) {
            throw buildException("Failed to resolve method '{}' in class '{}' ({}).", methodName, className, e.getMessage());
        }
    }
}

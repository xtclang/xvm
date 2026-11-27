package org.xtclang.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.xtclang.plugin.internal.DefaultXtcRunModule;
import org.xtclang.plugin.internal.XtcLauncherDefaults;

/**
 * What a project's {@code xtcRun} or {@code xtcTest} runs its modules with, as the language server's model exports it: the
 * method and arguments of each module it names, by the module's name, the first time it names it, and the JVM arguments
 * that its JVM gets, as a task's allJvmArgs gives them: the plugin's own options, {@code -ea} where
 * {@code enableAssertions} is true, and then its {@code jvmArgs}.
 * Every value is read so that none can fail the export: a value that cannot be read, as a provider without a value or
 * one that throws, is left out, a module with it entirely, and {@link #problems} says what was left out and why.
 *
 * @param methods   the method of each module, the default one where the module sets none or a blank one
 * @param arguments the arguments of each module of {@code methods}
 * @param jvmArgs   the JVM arguments, none where its {@code jvmArgs} cannot be read
 * @param problems  what could not be read, each as {@code <what>: <why>}
 */
record XtcLspRunSettings(Map<String, String> methods, Map<String, List<String>> arguments, List<String> jvmArgs, List<String> problems) {
    /** The settings of {@code extension}, its modules' too where {@code modules} is true. */
    static XtcLspRunSettings of(final XtcRuntimeExtension extension, final boolean modules) {
        final var problems = new ArrayList<String>();
        final List<String> buildArgs = read(() -> extension.getJvmArgs().getOrNull(), "jvmArgs", problems);
        final Boolean assertions = read(() -> extension.getEnableAssertions().getOrNull(), "enableAssertions", problems);
        final var jvmArgs = buildArgs == null ? null : XtcLauncherDefaults.allJvmArgs(
            assertions == null ? XtcLauncherDefaults.enableAssertions() : assertions, buildArgs);
        final var methods = new LinkedHashMap<String, String>();
        final var arguments = new LinkedHashMap<String, List<String>>();
        if (modules) {
            final var named = new HashSet<String>();
            final List<XtcRunModule> declared = read(() -> extension.getModules().getOrNull(), "modules", problems);
            for (final var module : declared == null ? List.<XtcRunModule>of() : declared) {
                final String name = read(() -> module.getModuleName().getOrElse(""), "a module's moduleName", problems);
                // a module without a name, which runXtc refuses to run, has nothing to apply to; the first time decides
                if (name == null || name.isBlank() || !named.add(name)) {
                    continue;
                }
                final var unread = problems.size();
                final String method = read(() -> module.getMethodName().getOrElse(""), "module " + name + "'s methodName", problems);
                final List<String> args = read(() -> module.getModuleArgs().getOrNull(), "module " + name + "'s moduleArgs", problems);
                if (problems.size() == unread && args != null) {
                    methods.put(name, method == null || method.isBlank() ? DefaultXtcRunModule.DEFAULT_METHOD_NAME : method);
                    arguments.put(name, List.copyOf(args));
                }
            }
        }
        return new XtcLspRunSettings(Collections.unmodifiableMap(methods), Collections.unmodifiableMap(arguments),
            jvmArgs == null ? List.of() : List.copyOf(jvmArgs), List.copyOf(problems));
    }

    /**
     * The value that {@code value} gives, or null where it has none or cannot give one, which {@code problems} then
     * records for {@code what}.
     */
    private static <T> T read(final Supplier<T> value, final String what, final List<String> problems) {
        try {
            final T read = value.get();
            if (read == null) {
                problems.add(what + ": it has no value");
            }
            return read;
        } catch (final RuntimeException failure) {
            final var message = failure.getMessage();
            problems.add(what + ": " + (message == null || message.isBlank()
                ? failure.getClass().getSimpleName() : message.lines().findFirst().orElseThrow()));
            return null;
        }
    }
}

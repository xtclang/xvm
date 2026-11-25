package org.xtclang.plugin.internal;

import static org.xtclang.plugin.XtcPluginConstants.PLUGIN_BUILD_INFO_RESOURCE_PATH;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The JVM options that the build gives every JVM a launcher task starts, plugin-build-info.properties'
 * {@code defaultJvmArgs}: whether they enable assertions ({@code -ea}), which a launcher's {@code enableAssertions}
 * takes as its default, and the other options, which the launcher always adds before a task's {@code jvmArgs}, as
 * Gradle's tasks add their own options.
 */
public final class XtcLauncherDefaults {
    /** The option that enables assertions. */
    public static final String ENABLE_ASSERTIONS = "-ea";

    private static final List<String> BUILD_JVM_ARGS = load();

    private XtcLauncherDefaults() {
    }

    /**
     * @return whether the build's JVM options enable assertions
     */
    public static boolean enableAssertions() {
        return BUILD_JVM_ARGS.contains(ENABLE_ASSERTIONS);
    }

    /**
     * @return the build's JVM options but {@code -ea}, in their order
     */
    public static List<String> jvmOptions() {
        return BUILD_JVM_ARGS.stream().filter(arg -> !arg.equals(ENABLE_ASSERTIONS)).toList();
    }

    /**
     * The JVM arguments that a launched JVM gets, as Gradle's {@code allJvmArgs}: {@code -ea} if
     * {@code enableAssertions}, then the plugin's own options, then {@code jvmArgs}; a build that changes none of them
     * launches with the build's {@code defaultJvmArgs} as they are.
     *
     * @param enableAssertions whether the JVM runs with assertions enabled
     * @param jvmArgs          the build script's JVM arguments
     *
     * @return the JVM arguments, in that order
     */
    public static List<String> allJvmArgs(final boolean enableAssertions, final List<String> jvmArgs) {
        final var all = new ArrayList<String>();
        if (enableAssertions) {
            all.add(ENABLE_ASSERTIONS);
        }
        all.addAll(jvmOptions());
        all.addAll(jvmArgs);
        return List.copyOf(all);
    }

    private static List<String> load() {
        try (final var input = XtcLauncherDefaults.class.getResourceAsStream(PLUGIN_BUILD_INFO_RESOURCE_PATH)) {
            final var props = new Properties();
            props.load(input);
            return List.of(props.getProperty("defaultJvmArgs").split(",")).stream().filter(arg -> !arg.isBlank()).toList();
        } catch (final Exception e) {
            // without the build info, as in a plugin built outside the XDK's build, assertions are enabled
            return List.of(ENABLE_ASSERTIONS);
        }
    }
}

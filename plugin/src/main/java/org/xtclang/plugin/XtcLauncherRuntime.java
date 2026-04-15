package org.xtclang.plugin;

import java.io.File;
import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Explicit launcher runtime descriptor shared by execution strategies.
 * The runtime is modeled as a classpath plus the launcher jar that provides
 * the XTC tool entry points.
 */
public record XtcLauncherRuntime(
        @NotNull String source,
        @NotNull File launcherJar,
        @NotNull List<File> classpath) {

    public XtcLauncherRuntime {
        classpath = List.copyOf(classpath);
        if (classpath.isEmpty()) {
            throw new IllegalArgumentException("Launcher runtime classpath must not be empty");
        }
        if (!classpath.contains(launcherJar)) {
            throw new IllegalArgumentException("Launcher runtime classpath must contain launcher jar: " + launcherJar);
        }
    }

    public String asClasspathArgument() {
        return classpath.stream()
            .map(File::getAbsolutePath)
            .reduce((left, right) -> left + File.pathSeparator + right)
            .orElseThrow(() -> new IllegalStateException("Launcher runtime classpath must not be empty"));
    }
}

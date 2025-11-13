package org.xtclang.plugin.launchers;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Provider;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Encapsulates the context needed for XTC launcher execution.
 * Reduces constructor parameter count and groups related configuration.
 */
public class LauncherContext {
    private final Provider<@NotNull String> projectVersion;
    private final Provider<@NotNull FileTree> xdkFileTree;
    private final Provider<@NotNull FileCollection> javaToolsConfig;
    private final Provider<@NotNull String> toolchainExecutable;
    private final File workingDirectory;

    public LauncherContext(
            final Provider<@NotNull String> projectVersion,
            final Provider<@NotNull FileTree> xdkFileTree,
            final Provider<@NotNull FileCollection> javaToolsConfig,
            final Provider<@NotNull String> toolchainExecutable,
            final File workingDirectory) {
        this.projectVersion = projectVersion;
        this.xdkFileTree = xdkFileTree;
        this.javaToolsConfig = javaToolsConfig;
        this.toolchainExecutable = toolchainExecutable;
        this.workingDirectory = workingDirectory;
    }

    public Provider<@NotNull String> getProjectVersion() {
        return projectVersion;
    }

    public Provider<@NotNull FileTree> getXdkFileTree() {
        return xdkFileTree;
    }

    public Provider<@NotNull FileCollection> getJavaToolsConfig() {
        return javaToolsConfig;
    }

    public Provider<@NotNull String> getToolchainExecutable() {
        return toolchainExecutable;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }
}

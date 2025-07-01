package org.xtclang.plugin.launchers;

import java.io.File;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gradle.api.Action;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;

/**
 * Configuration data needed by launchers. This class contains only serializable
 * data to support Gradle's configuration cache.
 */
public class LauncherConfiguration implements Serializable {
    private static final long serialVersionUID = 1L;

    // Basic identification
    private final String projectName;
    private final String taskName;
    private final String logPrefix;
    
    // Paths and files
    private final File xdkContentsDir;
    private final HashSet<File> javaToolsConfigFiles;
    
    // Launcher-specific settings
    private final String javaLauncherClassName;
    private final String nativeLauncherCommandName;
    private final boolean verboseLogging;
    
    // Execution interfaces (these will be provided at runtime, not serialized)
    private transient JavaExecInterface javaExecInterface;
    private transient ExecInterface execInterface;
    private transient FileOperations fileOperations;
    private transient Logger logger;

    public LauncherConfiguration(
            String projectName,
            String taskName,
            File xdkContentsDir,
            Set<File> javaToolsConfigFiles,
            String javaLauncherClassName,
            String nativeLauncherCommandName,
            boolean verboseLogging) {
        this.projectName = projectName;
        this.taskName = taskName;
        this.logPrefix = "[" + projectName + ":" + taskName + "]";
        this.xdkContentsDir = xdkContentsDir;
        this.javaToolsConfigFiles = new HashSet<>(javaToolsConfigFiles);
        this.javaLauncherClassName = javaLauncherClassName;
        this.nativeLauncherCommandName = nativeLauncherCommandName;
        this.verboseLogging = verboseLogging;
    }

    // Runtime injection methods for non-serializable components
    public void setJavaExecInterface(JavaExecInterface javaExecInterface) {
        this.javaExecInterface = javaExecInterface;
    }

    public void setExecInterface(ExecInterface execInterface) {
        this.execInterface = execInterface;
    }

    public void setFileOperations(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    // Getters
    public String getProjectName() {
        return projectName;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getLogPrefix() {
        return logPrefix;
    }

    public File getXdkContentsDir() {
        return xdkContentsDir;
    }

    public Set<File> getJavaToolsConfigFiles() {
        return javaToolsConfigFiles;
    }

    public String getJavaLauncherClassName() {
        return javaLauncherClassName;
    }

    public String getNativeLauncherCommandName() {
        return nativeLauncherCommandName;
    }

    public JavaExecInterface getJavaExecInterface() {
        if (javaExecInterface == null) {
            throw new IllegalStateException("JavaExecInterface not set. Call setJavaExecInterface() first.");
        }
        return javaExecInterface;
    }

    public ExecInterface getExecInterface() {
        if (execInterface == null) {
            throw new IllegalStateException("ExecInterface not set. Call setExecInterface() first.");
        }
        return execInterface;
    }

    public FileOperations getFileOperations() {
        if (fileOperations == null) {
            throw new IllegalStateException("FileOperations not set. Call setFileOperations() first.");
        }
        return fileOperations;
    }

    public Logger getLogger() {
        if (logger == null) {
            throw new IllegalStateException("Logger not set. Call setLogger() first.");
        }
        return logger;
    }
    
    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    /**
     * Interface for Java execution operations.
     */
    public interface JavaExecInterface {
        ExecResult javaexec(Action<? super JavaExecSpec> action);
    }

    /**
     * Interface for native execution operations.
     */
    public interface ExecInterface {
        ExecResult exec(Action<? super ExecSpec> action);
    }

    /**
     * Interface for file operations.
     */
    public interface FileOperations {
        FileCollection fileTree(File dir);
        FileCollection files(Object... paths);
    }
}
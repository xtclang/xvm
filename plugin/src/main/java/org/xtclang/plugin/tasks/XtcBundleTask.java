package org.xtclang.plugin.tasks;

import javax.inject.Inject;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import org.xtclang.plugin.launchers.AttachedStrategy;
import org.xtclang.plugin.launchers.DirectStrategy;
import org.xtclang.plugin.launchers.ExecutionMode;
import org.xtclang.plugin.launchers.ExecutionStrategy;

/**
 * Bundles multiple .xtc modules into a single bundled .xtc file.
 * <p>
 * The bundled file contains all input modules with a shared constant pool.
 * One module is designated as the primary; the rest become embedded.
 * Fingerprints for modules satisfied within the bundle are removed.
 * <p>
 * Supports DIRECT (in-process) and ATTACHED (forked) execution modes through
 * the standard {@link ExecutionStrategy} framework.
 */
@CacheableTask
public abstract class XtcBundleTask extends XtcDefaultTask {

    private final Provider<Directory> projectDirectory;

    @Inject
    public XtcBundleTask(final ObjectFactory objects, final ProjectLayout layout) {
        super(objects);
        this.projectDirectory = objects.directoryProperty().value(layout.getProjectDirectory());
    }

    /**
     * The .xtc module files to bundle. Non-.xtc files in the collection are ignored.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getInputModules();

    /**
     * The output bundled .xtc file.
     */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /**
     * The fully qualified module name to designate as primary in the bundle.
     * Defaults to the first input module if not specified.
     */
    @Input
    @Optional
    public abstract Property<String> getPrimaryModule();

    /**
     * Execution mode. Defaults to DIRECT (in-process, no fork).
     */
    @Input
    public abstract Property<ExecutionMode> getExecutionMode();

    /**
     * Javatools classpath for forked execution. Required for ATTACHED mode,
     * ignored for DIRECT mode (where javatools is already on the classpath).
     */
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getJavaToolsClasspath();

    /**
     * Verbose output flag for forked execution.
     */
    @Input
    @Optional
    public abstract Property<Boolean> getVerbose();

    /**
     * The project directory, captured at configuration time for config cache compatibility.
     * Used as the working directory for forked execution.
     */
    @Internal
    public Provider<Directory> getProjectDirectory() {
        return projectDirectory;
    }

    @TaskAction
    public void bundle() {
        var xtcFiles = getInputModules().getFiles().stream()
                .filter(f -> f.getName().endsWith(".xtc"))
                .toList();

        if (xtcFiles.isEmpty()) {
            throw new GradleException("No .xtc files found in inputModules");
        }

        var output = getOutputFile().get().getAsFile();
        output.getParentFile().mkdirs();

        var mode = getExecutionMode().getOrElse(ExecutionMode.DIRECT);
        var strategy = createStrategy(mode);
        var exitCode = strategy.execute(this);
        if (exitCode != 0) {
            throw new GradleException("xtc bundle failed with exit code " + exitCode);
        }

        logger.lifecycle("Bundled {} modules into {}", xtcFiles.size(), output.getName());
    }

    private ExecutionStrategy createStrategy(final ExecutionMode mode) {
        return switch (mode) {
            case DIRECT -> new DirectStrategy(logger);
            case ATTACHED -> new AttachedStrategy(logger, resolveJavaExecutable());
            case DETACHED -> throw new UnsupportedOperationException(
                    "DETACHED mode is not supported for bundle tasks (bundling is a synchronous packaging operation)");
        };
    }

    private String resolveJavaExecutable() {
        // For forked execution, resolve the java executable from the Java toolchain
        var javaHome = System.getProperty("java.home");
        if (javaHome == null) {
            throw new GradleException("Cannot resolve java executable for forked bundle execution");
        }
        return javaHome + "/bin/java";
    }
}

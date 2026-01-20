package org.xtclang.plugin.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.XtcRuntimeExtension;
import org.xtclang.plugin.XtcTestExtension;
import org.xtclang.plugin.internal.DefaultXtcRunModule;
import org.xtclang.plugin.launchers.ExecutionStrategy;

import static org.xtclang.plugin.XtcPluginConstants.XTC_MODULE_FILE_EXTENSION;
import static org.xtclang.plugin.XtcPluginConstants.XTC_TEST_RUNNER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginUtils.failure;

/**
 * Task that runs XTC xunit tests.
 * <p>
 * This task extends XtcRunTask to inherit all module running capabilities,
 * adding test-specific behavior like fail-on-failure control and test filtering.
 * <p>
 * Tests are configured the same way as run modules - via the xtcTest extension
 * or directly on the task. The task will run the configured test modules and
 * report results.
 */
@CacheableTask
public abstract class XtcTestTask extends XtcRunTask implements XtcTestExtension {
    private final Property<@NotNull Boolean> failOnTestFailure;
    private final ListProperty<@NotNull String> includes;
    private final ListProperty<@NotNull String> excludes;
    private final XtcTestExtension testExtension;
    private final Directory outputDir;

    @SuppressWarnings({"ConstructorNotProtectedInAbstractClass", "this-escape"})
    @Inject
    public XtcTestTask(final ObjectFactory objects, final Project project) {
        super(objects, project);

        // Test-specific properties with conventions from extension
        this.testExtension = XtcProjectDelegate.resolveXtcTestExtension(project);
        this.outputDir = project.getLayout().getBuildDirectory().get().dir("xunit");
        this.failOnTestFailure = objects.property(Boolean.class).convention(testExtension.getFailOnTestFailure());
        this.includes = objects.listProperty(String.class).convention(testExtension.getIncludes());
        this.excludes = objects.listProperty(String.class).convention(testExtension.getExcludes());
    }

    /**
     * Override to return the xtcTest extension instead of xtcRun extension.
     * This ensures that module configuration from xtcTest {} block is used.
     */
    @Override
    protected XtcRuntimeExtension getExtension() {
        return testExtension;
    }

    @Input
    @Override
    public Property<@NotNull Boolean> getFailOnTestFailure() {
        return failOnTestFailure;
    }

    @Internal
    @Override
    public ListProperty<@NotNull String> getIncludes() {
        return includes;
    }

    @Internal
    @Override
    public ListProperty<@NotNull String> getExcludes() {
        return excludes;
    }

    @Internal
    @Override
    public final String getJavaLauncherClassName() {
        return XTC_TEST_RUNNER_CLASS_NAME;
    }

    @Internal
    public Directory getOutputDirectory() {
        return outputDir;
    }

    @TaskAction
    @Override
    public void executeTask() {
        logger.info("[plugin] Running XTC test task: {}", getName());

        // Run tests using parent's execution logic
        // The parent XtcRunTask already handles per-module exit codes and failures
        // xunit framework handles test result reporting
        try {
            super.executeTask();
        } catch (final Exception e) {
            if (getFailOnTestFailure().get()) {
                throw failure("Test failure.", e);
            }
            logger.warn("[plugin] Test execution failed but failOnTestFailure is false", e);
        }
    }

    @Override
    protected int executeStrategy(final XtcRunModule runConfig, final ExecutionStrategy strategy) {
        return strategy.execute(this, runConfig);
    }

    @Override
    protected List<XtcRunModule> resolveModulesToRunFromModulePath(final List<File> resolvedModulePath) {
        // If modules are explicitly configured, use those
        if (!isEmpty()) {
            logger.info("[plugin] Test modules explicitly configured, using those.");
            return super.resolveModulesToRunFromModulePath(resolvedModulePath);
        }

        // Auto-discover test modules from the test source set output directory
        logger.info("[plugin] No test modules explicitly configured, auto-discovering from test source set output.");
        final List<XtcRunModule> discoveredModules = discoverTestModules();

        if (discoveredModules.isEmpty()) {
            logger.info("[plugin] No test modules found in test source set output. Skipping test task.");
            return List.of();
        }

        logger.info("[plugin] Auto-discovered {} test module(s):", discoveredModules.size());
        discoveredModules.forEach(module -> logger.info("[plugin]    Test module: {}", module.getModuleName().get()));

        // TODO: Apply include/exclude filters to discovered test modules
        return discoveredModules;
    }

    /**
     * Discovers test modules from the test source set output directory.
     * Scans for .xtc files and creates XtcRunModule instances for each.
     *
     * @return list of discovered test modules
     */
    private List<XtcRunModule> discoverTestModules() {
        final List<XtcRunModule> modules = new ArrayList<>();

        // Get the test source set output directory
        final Provider<@NotNull Directory> testOutputDir = sourceSetOutputDirs.get(SourceSet.TEST_SOURCE_SET_NAME);
        if (testOutputDir == null) {
            logger.warn("[plugin] Test source set output directory not found.");
            return modules;
        }

        final File testOutputDirFile = testOutputDir.get().getAsFile();
        if (!testOutputDirFile.exists() || !testOutputDirFile.isDirectory()) {
            logger.info("[plugin] Test source set output directory does not exist or is not a directory: {}", testOutputDirFile);
            return modules;
        }

        // Scan for .xtc files
        final File[] xtcFiles = testOutputDirFile.listFiles((dir, name) -> name.endsWith("." + XTC_MODULE_FILE_EXTENSION));
        if (xtcFiles == null || xtcFiles.length == 0) {
            logger.info("[plugin] No .xtc files found in test output directory: {}", testOutputDirFile);
            return modules;
        }

        // Create XtcRunModule for each discovered .xtc file
        for (final File xtcFile : xtcFiles) {
            final String fileName = xtcFile.getName();
            // Remove .xtc extension to get module name
            final String moduleName = fileName.substring(0, fileName.length() - XTC_MODULE_FILE_EXTENSION.length() - 1);

            final DefaultXtcRunModule runModule = objects.newInstance(DefaultXtcRunModule.class);
            runModule.getModuleName().set(moduleName);
            // Method name defaults to "run" which is correct for test modules
            modules.add(runModule);

            logger.info("[plugin] Discovered test module: {} (from {})", moduleName, xtcFile);
        }

        return modules;
    }
}

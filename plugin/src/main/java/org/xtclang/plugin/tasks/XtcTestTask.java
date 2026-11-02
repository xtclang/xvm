package org.xtclang.plugin.tasks;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.XtcRunModule;
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
 * adding test-specific behavior like fail-on-failure control.
 * <p>
 * Tests are configured the same way as run modules - via the xtcTest extension
 * or directly on the task. The task will run the configured test modules and
 * report results.
 * <p>
 * Test filtering (include/exclude) is handled at the source set level.
 */
@CacheableTask
public abstract class XtcTestTask extends XtcRunTask implements XtcTestExtension {
    private final Property<@NotNull Boolean> failOnTestFailure;
    private final Provider<@NotNull Directory> outputDir;
    private final ListProperty<@NotNull String> testPackages;
    private final ListProperty<@NotNull String> testClasses;
    private final ListProperty<@NotNull String> testMethods;
    private final Property<@NotNull String> testReport;

    @SuppressWarnings({"ConstructorNotProtectedInAbstractClass", "this-escape"})
    @Inject
    public XtcTestTask(final ObjectFactory objects, final Project project) {
        super(objects, project, XtcProjectDelegate.resolveXtcTestExtension(project));

        // Test-specific properties with conventions from extension
        final var testExtension = XtcProjectDelegate.resolveXtcTestExtension(project);
        this.outputDir = project.getLayout().getBuildDirectory().map(dir -> dir.dir("xunit"));
        this.failOnTestFailure = objects.property(Boolean.class).convention(testExtension.getFailOnTestFailure());
        this.testPackages = objects.listProperty(String.class);
        this.testClasses = objects.listProperty(String.class);
        this.testMethods = objects.listProperty(String.class);
        this.testReport = objects.property(String.class);

        // A debugged run always runs, and its results are not cached
        getOutputs().upToDateWhen(task -> !((XtcTestTask) task).getDebug().isPresent());
        getOutputs().doNotCacheIf("the tests are debugged", task -> ((XtcTestTask) task).getDebug().isPresent());
    }

    @Input
    @Override
    public Property<@NotNull Boolean> getFailOnTestFailure() {
        return failOnTestFailure;
    }

    /**
     * The packages whose tests run, by their paths in the test module, such as {@code tests}; with the classes and
     * methods, xunit runs the tests that any of them selects, and all of the module's without any.
     * Example: ./gradlew testXtc --test-package=tests
     */
    @Option(option = "test-package", description = "Run the tests of a package, by its path in the module (repeatable)")
    public void setTestPackageOption(final List<String> packages) {
        testPackages.set(packages);
    }

    @Input
    public ListProperty<@NotNull String> getTestPackages() {
        return testPackages;
    }

    /**
     * The classes whose tests run, by their paths in the test module, such as {@code tests.MathTest}.
     * Example: ./gradlew testXtc --test-class=tests.MathTest
     */
    @Option(option = "test-class", description = "Run the tests of a class, by its path in the module (repeatable)")
    public void setTestClassOption(final List<String> classes) {
        testClasses.set(classes);
    }

    @Input
    public ListProperty<@NotNull String> getTestClasses() {
        return testClasses;
    }

    /**
     * The test methods that run, by their paths in the test module, such as {@code tests.MathTest.adds}, or a
     * module-level test by its name.
     * Example: ./gradlew testXtc --test-method=tests.MathTest.adds
     */
    @Option(option = "test-method", description = "Run a test method, by its path in the module (repeatable)")
    public void setTestMethodOption(final List<String> methods) {
        testMethods.set(methods);
    }

    @Input
    public ListProperty<@NotNull String> getTestMethods() {
        return testMethods;
    }

    /**
     * How xunit also reports the tests' execution, for a tool that reads it from the output, such as an IDE:
     * {@code json}, one JSON object per event, which xunit's {@code JsonExecutionListener} describes. xunit refuses a
     * name it does not know.
     * Example: ./gradlew testXtc --test-report=json
     */
    @Option(option = "test-report", description = "Also report the tests' execution for a tool: json")
    public void setTestReportOption(final String report) {
        testReport.set(report);
    }

    @Input
    @Optional
    public Property<@NotNull String> getTestReport() {
        return testReport;
    }

    @Internal
    @Override
    public final String getJavaLauncherClassName() {
        return XTC_TEST_RUNNER_CLASS_NAME;
    }

    @OutputDirectory
    public Provider<@NotNull Directory> getOutputDirectory() {
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
                throw failure(e, "Test failure.");
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
        if (getCliModuleName().isPresent() || !isEmpty()) {
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

        return discoveredModules.stream().map(this::applyCliOverrides).toList();
    }

    /**
     * Discovers test modules from the test source set output directory.
     * Scans for .xtc files and creates XtcRunModule instances for each.
     * Results are sorted by module name for deterministic test execution order.
     *
     * @return list of discovered test modules, sorted by name
     */
    private List<XtcRunModule> discoverTestModules() {
        // Get the test source set output directory
        final Provider<@NotNull Directory> testOutputDir = sourceSetOutputDirs.get(SourceSet.TEST_SOURCE_SET_NAME);
        if (testOutputDir == null) {
            logger.warn("[plugin] Test source set output directory not found.");
            return List.of();
        }

        final Path testOutputPath = testOutputDir.get().getAsFile().toPath();
        if (!Files.exists(testOutputPath) || !Files.isDirectory(testOutputPath)) {
            logger.info("[plugin] Test source set output directory does not exist or is not a directory: {}", testOutputPath);
            return List.of();
        }

        final String xtcExtension = "." + XTC_MODULE_FILE_EXTENSION;
        final List<XtcRunModule> modules = new ArrayList<>();

        // Use NIO Files.list for proper resource handling and deterministic ordering
        try (Stream<Path> files = Files.list(testOutputPath)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(xtcExtension))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(xtcPath -> {
                    final String fileName = xtcPath.getFileName().toString();
                    // Remove .xtc extension to get module name
                    final String moduleName = fileName.substring(0, fileName.length() - xtcExtension.length());

                    final DefaultXtcRunModule runModule = objects.newInstance(DefaultXtcRunModule.class);
                    runModule.getModuleName().set(moduleName);
                    // Method name defaults to "run" which is correct for test modules
                    modules.add(runModule);

                    logger.info("[plugin] Discovered test module: {} (from {})", moduleName, xtcPath);
                });
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to list test output directory: " + testOutputPath, e);
        }

        if (modules.isEmpty()) {
            logger.info("[plugin] No .xtc files found in test output directory: {}", testOutputPath);
        }

        return modules;
    }
}

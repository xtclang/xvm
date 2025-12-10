package org.xtclang.plugin.tasks;

import java.io.File;
import java.util.List;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.XtcTestExtension;

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

    @SuppressWarnings({"ConstructorNotProtectedInAbstractClass", "this-escape"})
    @Inject
    public XtcTestTask(final ObjectFactory objects, final Project project) {
        super(objects, project);

        // Test-specific properties with conventions from extension
        final var testExt = XtcProjectDelegate.resolveXtcTestExtension(project);
        this.failOnTestFailure = objects.property(Boolean.class).convention(testExt.getFailOnTestFailure());
        this.includes = objects.listProperty(String.class).convention(testExt.getIncludes());
        this.excludes = objects.listProperty(String.class).convention(testExt.getExcludes());
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
    protected List<XtcRunModule> resolveModulesToRunFromModulePath(final List<File> resolvedModulePath) {
        // Get modules from parent, then apply test filters if any

        // TODO: Apply include/exclude filters to test modules - For now, just return all configured modules
        return super.resolveModulesToRunFromModulePath(resolvedModulePath);
    }
}

package org.xtclang.plugin.tasks;

import java.util.Arrays;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;

import org.xtclang.plugin.ProjectDelegate;
import org.xtclang.plugin.XtcBuildRuntimeException;
import org.xtclang.plugin.XtcProjectDelegate;

// TODO: This is intended a common superclass to avoid the messy delegate pattern.
//   We also put XTC launcher common logic in here, like e.g. fork, jvmArgs and such.

// TODO: We'd like our tasks to have the same kind of extension pattern as the XtcProjectDelegate
//   now that we have a working inheritance hierarchy no longer constrained to multiple inheritance
//   or various Gradle APIs.
public abstract class XtcDefaultTask extends DefaultTask {
    /**
     * Used to print only key messages with an "always" semantic. Used to quickly switch on and off,
     * or persist in the shell, a setting that is used for stuff like always printing launcher command
     * lines, regardless of log level, but not doing it if the override is turned off (default).
     */

    // TODO gradually remove the delegate and distribute the logic to its correct places in the "normal" Gradle plugin and DSL APIs and implementations.
    protected final Project project;
    protected final String projectName;
    protected final ObjectFactory objects;
    protected final Logger logger;
    protected final boolean overrideVerboseLogging;

    private boolean isBeingExecuted;

    protected XtcDefaultTask(final Project project) {
        this(project, ProjectDelegate.hasVerboseLogging(project));
    }

    protected XtcDefaultTask(final Project project, final boolean overrideVerboseLogging) {
        this.project = project;
        this.projectName = project.getName();
        this.objects = project.getObjects();
        this.logger = project.getLogger();
        this.overrideVerboseLogging = overrideVerboseLogging;
    }

    protected final String prefix() {
        return ProjectDelegate.prefix(projectName, getName());
    }

    /**
     * We count everything with the log level "info" or finer as verbose logging.
     * This was at project level before, but is now at task level, so we can react and be
     * verbose if someone has set the verbose flag for the task. We may want to dump
     * stuff we send to the launcher, not just make the launcher more talkative.
     *
     * @return True of we are running with verbose logging enabled, false otherwise.
     */
    public boolean hasVerboseLogging() {
        return overrideVerboseLogging || ProjectDelegate.hasVerboseLogging(project);
    }

    protected void executeTask() {
        isBeingExecuted = true;
    }

    @Internal
    protected boolean isBeingExecuted() {
        return isBeingExecuted;
    }

    protected void checkIsBeingExecuted() {
        checkIsBeingExecuted(this);
    }

    @SuppressWarnings("UnusedReturnValue")
    protected <T> T checkIsBeingExecuted(final T configData) {
        // Used to implement sanity checks that we have started a TaskAction before resolving configuration contents.
        if (!isBeingExecuted()) {
            throw buildException("Task '{}' attempts to use configuration before it is fully resolved.", getName());
        }
        return configData;
    }

    public FileCollection filesFromConfigs(final String... configNames) {
        return filesFromConfigs(false, configNames);
    }

    public FileCollection filesFromConfigs(final List<String> configNames) {
        return filesFromConfigs(false, configNames);
    }

    public FileCollection filesFromConfigs(final boolean shouldBeResolved, final String... configNames) {
        return filesFromConfigs(shouldBeResolved, Arrays.asList(configNames));
    }

    public FileCollection filesFromConfigs(final boolean shouldBeResolved, final List<String> configNames) {
        final Logger logger = project.getLogger();
        final String prefix = prefix();
        logger.info("{} Resolving filesFrom config: {}", prefix, configNames);
        FileCollection fc = project.getObjects().fileCollection();
        for (final var name : configNames) {
            final Configuration config = project.getConfigurations().getByName(name);
            if (shouldBeResolved && config.getState() != Configuration.State.RESOLVED) {
                throw buildException("Configuration '{}' is not resolved, which is a requirement from the task execution phase.", name);
            }
            final var files = project.files(config);
            logger.info("{} Scanning file collection: filesFrom: {} {}, files: {}", prefix, name, config.getState(), files.getFiles());
            fc = fc.plus(files);
        }
        fc.getAsFileTree().forEach(it -> logger.debug("{}    Resolved fileTree '{}'", prefix, it.getAbsolutePath()));
        return fc;
    }

    public XtcBuildRuntimeException buildException(final String msg, final Object... args) {
        return buildException(null, msg, args);
    }

    public XtcBuildRuntimeException buildException(final Throwable t, final String msg, final Object... args) {
        return XtcProjectDelegate.buildException(logger, prefix(), t, msg, args);
    }
}

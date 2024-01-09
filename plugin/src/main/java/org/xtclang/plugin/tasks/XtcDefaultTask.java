package org.xtclang.plugin.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.ProjectDelegate;
import org.xtclang.plugin.XtcProjectDelegate;

// TODO: This is intended a common superclass to avoid the messy delegate pattern.
//   We also put XTC launcher common logic in here, like e.g. fork, jvmArgs and such.

// TODO: We'd like our tasks to have the same kind of extension pattern as the XtcProjectDelegate
//   now that we have a working inheritance hierarchy no longer constrained to multiple inheritance
//   or various Gradle APIs.
public abstract class XtcDefaultTask extends DefaultTask {
    protected final XtcProjectDelegate delegate; // TODO gradually remove the delegate and distribute the logic to its correct places in the "normal" Gradle plugin and DSL APIs and implementations.
    protected final Project project;
    protected final String prefix;
    protected final String name;
    protected final ObjectFactory objects;
    protected final Logger logger;

    private boolean isResolvable;

    protected XtcDefaultTask(final XtcProjectDelegate delegate) {
        this.delegate = delegate;
        this.project = delegate.getProject();
        this.prefix = delegate.prefix();
        this.name = delegate.getProject().getName();
        this.objects = delegate.getObjects();
        this.logger = delegate.getLogger();
    }

    protected void start() {
        isResolvable = true;
    }

    @Internal
    protected boolean isResolvable() {
        return isResolvable;
    }

    protected void checkResolvable() {
        checkResolvable(this);
    }

    @SuppressWarnings("UnusedReturnValue")
    protected <T> T checkResolvable(final T configData) {
        // Used to implement sanity checks that we have started a TaskAction before resolving configuration contents.
        if (!isResolvable()) {
            throw delegate.buildException("Task '{}' attempts to use configuration before it is fully resolved.");
        }
        return configData;
    }

    protected ExecResult handleExecResult(final Project project, final ExecResult result) {
        final int exitValue = result.getExitValue();
        if (exitValue != 0) {
            getLogger().error("{} '{}' terminated abnormally (exitValue: {}). Rethrowing exception.", ProjectDelegate.prefix(project), getName(), exitValue);
        }
        result.rethrowFailure();
        result.assertNormalExitValue();
        return result;
    }
}

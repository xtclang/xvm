package org.xtclang.plugin.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
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
    // TODO gradually remove the delegate and distribute the logic to its correct places in the "normal" Gradle plugin and DSL APIs and implementations.
    protected final XtcProjectDelegate delegate;
    protected final Project project;
    protected final String prefix;
    protected final String projectName;
    protected final ObjectFactory objects;
    protected final String taskName;
    protected final Logger logger;

    private boolean isResolvable;

    protected XtcDefaultTask(final XtcProjectDelegate delegate, final String taskName) {
        this.delegate = delegate; // TODO get rid of delegates.
        this.taskName = taskName;
        this.project = delegate.getProject();
        this.projectName = project.getName();
        this.objects = project.getObjects();
        this.logger = project.getLogger();
        assert projectName.equals(delegate.getProjectName()) : "Delegate field mismatch for projectName.";
        this.prefix = ProjectDelegate.prefix(projectName, taskName);
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
            throw buildException("Task '{}' attempts to use configuration before it is fully resolved.", taskName);
        }
        return configData;
    }

    public XtcBuildRuntimeException buildException(final String msg, final Object... args) {
        return buildException(null, msg, args);
    }

    public XtcBuildRuntimeException buildException(final Throwable t, final String msg, final Object... args) {
        return delegate.buildException(t, msg, args);
    }
}

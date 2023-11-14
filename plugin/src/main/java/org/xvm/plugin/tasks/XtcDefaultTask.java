package org.xvm.plugin.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.process.ExecResult;
import org.xvm.plugin.ProjectDelegate;
import org.xvm.plugin.XtcProjectDelegate;

// TODO: This is intended a common superclass to avoid the messy delegate pattern.
//   We also put XTC launcher common logic in here, like e.g. fork, jvmArgs and such.

// TODO: We'd like our tasks to have the same kind of extension pattern as the XtcProjectDelegate
//   now that we have a working inheritance hierarchy no longer constrained to multiple inheritance
//   or various Gradle APIs.
public abstract class XtcDefaultTask extends DefaultTask {
    protected final XtcProjectDelegate project;
    protected final String prefix;
    protected final String name;
    protected final Logger logger;
    protected final ObjectFactory objects;

    protected XtcDefaultTask(final XtcProjectDelegate project) {
        this.project = project;
        this.prefix = project.prefix();
        this.name = getName();
        this.objects = project.getObjects();
        this.logger = project.getLogger();
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

package org.xtclang.plugin.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.ProjectDelegate;
import org.xtclang.plugin.XtcProjectDelegate;

import java.io.File;
import java.util.Collection;

// TODO: This is intended a common superclass to avoid the messy delegate pattern.
//   We also put XTC launcher common logic in here, like e.g. fork, jvmArgs and such.

// TODO: We'd like our tasks to have the same kind of extension pattern as the XtcProjectDelegate
//   now that we have a working inheritance hierarchy no longer constrained to multiple inheritance
//   or various Gradle APIs.
public abstract class XtcDefaultTask extends DefaultTask {
    protected final XtcProjectDelegate project;
    protected final String prefix;
    protected final String name;
    protected final ObjectFactory objects;
    protected final Logger logger;

    private boolean isResolvable;

    protected XtcDefaultTask(final XtcProjectDelegate project) {
        this.project = project;
        this.prefix = project.prefix();
        this.name = project.getProject().getName();
        this.objects = project.getObjects();
        this.logger = project.getLogger();
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
            throw project.buildException("Task '{}' attempts to use configuration before it is fully resolved.");
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

    // TODO: Likely we can just replace this with SourceDirectorySet::srcDirs for most things and will drastically simplify generated command lines.
    protected static Collection<File> filesToDirs(final Collection<File> files) {
        // Shorten a file set to a directory set
        // TODO: Also add logic to filter out subdirectories to parents already in the set.
        // TODO: Also use on module path.
        final var dirs = files.stream().map(r -> r.isDirectory() ? r : r.getParentFile()).distinct().toList();
        assert dirs.size() <= files.size() : "Directory filtering should be shorter.";
        return dirs;
    }
}

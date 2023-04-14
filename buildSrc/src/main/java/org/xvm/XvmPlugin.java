package org.xvm;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * TODO: WORK IN PROGRESS (XTC language plugin for IDE integration)
 *
 * Simplest possible template for an XvmPlugin. This is where specifications for the Xtc
 * Language Plugin will go.
 *
 * A Gradle plugin exists so that any project that ends up defining a large number of
 * custom tasks with complex dependencies, it makes sense to create a plugin for that logic
 * instead. The Gradle "job description" for a plugin is to create all tasks available
 * to the project, and wire them together, without adding complexity that needs to be
 * explicit throughout the build.gradle files.
 */
public class XvmPlugin implements Plugin<Project> {

    private final String taskName;

    public XvmPlugin() {
        this("DUMMY_TASK");
    }

    private XvmPlugin(final String taskName) {
        this.taskName = taskName;
    }

    @Override
    public void apply(final Project project) {
        // TODO: register tasks associated with the project here, configure them etc.
        // project.getTasks().create(taskName, XvmTask.class);
    }
}

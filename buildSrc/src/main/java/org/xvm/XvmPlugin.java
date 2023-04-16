package org.xvm;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.util.Objects;

/**
 * TODO: WORK IN PROGRESS (XTC language plugin for IDE integration)
 * <p>
 * Simplest possible template for an XvmPlugin. This is where specifications for the Xtc
 * Language Plugin will go, for anything written in Java. This can also be used as
 * a generic skeleton for different kinds of plugins that we may need, to structure¨
 * common build logic.
 *
 * The Gradle plugin pattern:
 *     A Gradle plugin exists so that any project that ends up defining a large number of
 *     custom tasks with complex dependencies, it makes sense to create a plugin for that logic
 *     instead. The Gradle "job description" for a plugin is to create all tasks available
 *     to the project, and wire them together, without adding complexity that needs to be
 *     explicit throughout the build.gradle files.
 */
public class XvmPlugin implements Plugin<Project> {
    /**
     * Simplest possible utility class to build XvmTasks from. XvmTasks will become a superclass for
     * all tasks we create in order to support the Xtc language plugin, and various reusable aspects
     * of the XDK build.
     *
     * You can use the abstract task to define what input data it needs and them implement many
     * tasks extending this, with different names and input:
     *
     * tasks.register<XvmTask>("firstTask") {
     *     message = "Hello from first task!"
     * }
     *
     * tasks.register<XvmTask>("secondTask") {
     *     message = "Hello from second task!"
     * }
     */
    abstract static class XvmTask extends DefaultTask {
        @Input
        abstract public Property<String> getMessage();

        private final Logger logger;

        XvmTask() {
            this.logger = getLogger();
        }

        @TaskAction
        public void run() {
            logger.lifecycle("Performing task action for " + getName());
            System.out.println("Mandatory input is: '" + getMessage() + "'");
        }
    }

    private final String info;

    public XvmPlugin() {
        this(null);
    }

    private XvmPlugin(final String name) {
        this.info = Objects.requireNonNullElse(name, getClass().getName());
    }

    @Override
    public void apply(final Project project) {
        // TODO: register tasks associated with the project here, configure them etc.
        var task = project.getTasks().create("firstTask", XvmTask.class);
        task.getInputs().property("message", "Task created from Java message.");
    }
}

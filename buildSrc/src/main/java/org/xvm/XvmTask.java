package org.xvm;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.util.Set;
import java.util.function.Function;

/**
 * TODO: WORK IN PROGRESS (XTC language plugin for IDE integration)
 *
 * Simplest possible utility class to build XvmTasks from. XvmTasks will become a superclass for
 * all tasks we create in order to support the Xtc language plugin, and various reusable aspects
 * of the XDK build.
 */
public class XvmTask extends DefaultTask {

    private final Set<Function<? super DefaultTask, ?>> getters =
        Set.of(
            DefaultTask::getName,
            DefaultTask::getDescription,
            DefaultTask::getState,
            DefaultTask::getExtensions,
            DefaultTask::getGroup);

    @TaskAction
    public void run() {
        // TODO: This is just a test task, call some getters on itself and dump the contents.
        System.out.println("Executing task " + getPath() + "!");
        getters.forEach(getter -> System.out.println(getter.apply(this)));
    }
}

package org.xvm;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.util.Set;
import java.util.function.Function;
public class XvmTask extends DefaultTask {
    private final Set<Function<? super DefaultTask, ?>> getters = Set.of(DefaultTask::getName, DefaultTask::getDescription, DefaultTask::getState, DefaultTask::getLocalState, DefaultTask::getEnabled, DefaultTask::getExtensions, DefaultTask::getGroup);
    @TaskAction
    public void run() {
        System.out.println("Hello from task " + getPath() + "!");
        for (var getter : getters) {
            System.out.println(getter.apply(this));
        }
    }
}

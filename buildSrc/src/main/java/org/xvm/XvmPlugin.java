package org.xvm;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/*
 * With a larger project it could happen that you end up defining tens of custom
 * tasks with intricate dependency graphs. In this case it makes sense to create a
 * custom plugin instead. That plugin creates all the tasks and wires them together.
 */
public class XvmPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().create("pluginsomething", XvmTask.class);
    }
}

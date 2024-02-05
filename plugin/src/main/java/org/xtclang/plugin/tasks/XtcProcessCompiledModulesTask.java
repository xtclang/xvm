package org.xtclang.plugin.tasks;

import org.gradle.api.tasks.TaskAction;
import org.xtclang.plugin.XtcProjectDelegate;

import javax.inject.Inject;

import static org.xtclang.plugin.XtcPluginConstants.XDK_TASK_PROCESS_COMPILED_MODULES_NAME;

/**
 * Runs in a post pass for every compile task.
 * Inputs: outputs of the compile task and a build directory destination.
 * Outputs: the files of the build directory so far.
 *
 * TODO: We can move renaming here as well, it's basically just another doLast action
 *   for this task. I think this makes sense, because we have missed this guy in the life
 *   cycle. The question is where to put the dependency, so that assemble lifecycle tasks
 *   are still respected.
 */
public class XtcProcessCompiledModulesTask extends XtcDefaultTask {

    @Inject
    public XtcProcessCompiledModulesTask(final XtcProjectDelegate project, final org.gradle.api.tasks.SourceSet sourceSet) {
        super(project, XDK_TASK_PROCESS_COMPILED_MODULES_NAME);
    }

    @TaskAction
    public void processCompiledModules() {
        start();
        throw new UnsupportedOperationException("Implement me.");
    }
}

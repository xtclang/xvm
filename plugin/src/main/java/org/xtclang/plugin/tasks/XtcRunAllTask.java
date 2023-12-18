package org.xtclang.plugin.tasks;

import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.xtclang.plugin.XtcProjectDelegate;

import javax.inject.Inject;

public class XtcRunAllTask extends XtcRunTask {
    @Inject
    public XtcRunAllTask(final XtcProjectDelegate project, final SourceSet moduleSourceSet) {
        super(project, moduleSourceSet);
    }

    @Override
    @TaskAction
    public void run() {
        project.warn("{} '{}' Running all XTC modules, even if they aren't configured to be run by default.", prefix, getName());
        super.run();
    }
}

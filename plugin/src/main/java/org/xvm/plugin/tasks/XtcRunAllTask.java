package org.xvm.plugin.tasks;

import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.xvm.plugin.XtcProjectDelegate;

import javax.inject.Inject;

// TODO not cachable?
public class XtcRunAllTask extends XtcRunTask {

    @Inject
    public XtcRunAllTask(final XtcProjectDelegate project, final SourceSet moduleSourceSet) {
        super(project, moduleSourceSet);
    }

    @Override
    @TaskAction
    public void run() {
        project.warn("{} '{}' Running all XTC modules, even if they aren't configured to be run by default.", project.prefix(), getName());
        super.run();
    }
}

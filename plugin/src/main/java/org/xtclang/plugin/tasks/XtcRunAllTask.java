package org.xtclang.plugin.tasks;

import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.xtclang.plugin.XtcProjectDelegate;

import javax.inject.Inject;

public class XtcRunAllTask extends XtcRunTask {
    @Inject
    public XtcRunAllTask(final XtcProjectDelegate delegate, final String taskName, final SourceSet moduleSourceSet) {
        super(delegate, taskName, moduleSourceSet);
    }

    @Override
    public boolean isRunAllTask() {
        return true;
    }

    @Override
    @TaskAction
    public void run() {
        logger.warn("{} '{}' Running all XTC modules, even if they aren't configured to be run by default.", prefix, taskName);
        super.run();
    }
}

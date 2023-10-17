package org.xvm.plugin;

import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.xvm.plugin.XtcRuntimeExtension.XtcRunModule;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;

public class XtcRunAllTask extends XtcRunTask {

    @Inject
    public XtcRunAllTask(final XtcProjectDelegate project, final SourceSet moduleSourceSet) {
        super(project, moduleSourceSet);
    }

    @TaskAction
    public void run() {
        super.run();
    }

    @Override
    protected Collection<XtcRunModule> resolveModulesToRun() {
        final var moduleFiles = allCompiledModules();
        final var allModules = moduleFiles.stream().map(File::getName).map(this::createModule).toList();
        project.lifecycle("{} '{}' Resolved modules to run: {}", prefix, getName(), allModules.size());
        for (final var m : allModules) {
            project.lifecycle("{} '{}': {}", prefix, getName(), m);
        }
        return allModules;
    }
}

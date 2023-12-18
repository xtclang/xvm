package org.xtclang.plugin.tasks;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.xtclang.plugin.XtcProjectDelegate;

import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcProjectDelegate.incomingXtcModuleDependencies;

/**
 * Abstract class that represents and XTC Launcher execution (i.e. Compiler, Runner, Disassembler etc.),
 * anything that goes through the XTC Launcher to spawn or call different processes
 */

abstract class XtcLauncherTask extends XtcDefaultTask {
    protected final SourceSet sourceSet;

    protected XtcLauncherTask(final XtcProjectDelegate project, final SourceSet sourceSet) {
        super(project);
        this.sourceSet = sourceSet;
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getInputDeclaredDependencyModules() {
        return project.filesFrom(incomingXtcModuleDependencies(sourceSet)); // xtcModule and xtcModuleTest dependencies declared in the project dependency { scope section
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getInputXtcJavaToolsConfig() {
        return project.getProject().files(project.getProject().getConfigurations().getByName(XDK_CONFIG_NAME_JAVATOOLS_INCOMING));
    }
}

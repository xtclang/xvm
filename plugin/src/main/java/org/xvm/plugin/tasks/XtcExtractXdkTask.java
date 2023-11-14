package org.xvm.plugin.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RelativePath;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.xvm.plugin.XtcProjectDelegate;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP;
import static org.xvm.plugin.Constants.JAVATOOLS_ARTIFACT_ID;
import static org.xvm.plugin.Constants.XDK_VERSION_PATH;
import static org.xvm.plugin.Constants.XTC_CONFIG_NAME_JAVATOOLS_OUTGOING;
import static org.xvm.plugin.Constants.XTC_MODULE_FILE_EXTENSION;

@CacheableTask
public class XtcExtractXdkTask extends XtcDefaultTask {
    public static final String EXTRACT_TASK_NAME = "extractXdk";
    private static final String ARCHIVE_EXTENSION = "zip";
    private final XtcProjectDelegate project;
    private final String prefix;

    @Inject
    public XtcExtractXdkTask(final XtcProjectDelegate project) {
        super();
        this.project = project;
        this.prefix = project.prefix();
        setGroup(BUILD_GROUP);
        setDescription("Extract an XDK zip resource into build/xdk/common/ as a build dependency. This is an internal task, and it makes little sense to run it manually.");

        project.getConfigs().register(XTC_CONFIG_NAME_JAVATOOLS_OUTGOING, it -> {
            it.setCanBeConsumed(false);
            it.setCanBeResolved(true);
            it.setDescription("The xtcJavaToolsProvider configuration is used to resolve the javatools.jar from the XDK.");
        });
    }

    private static boolean isXdkArchive(final File file) {
        return ARCHIVE_EXTENSION.equals(XtcProjectDelegate.getFileExtension(file));
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getInputXdkArchive() {
        return project.filesFrom("xdkZip", "xdk");
    }

    @OutputDirectory
    public Provider<Directory> getOutputXtcModules() {
        return project.getXdkContentsDir();
    }

    @TaskAction
    public void extractXdk() {
        // The task is configured at this point. We should indeed have found a zip archive from
        // some xdkDistributionProvider somewhere.
        final var archives = project.filesFrom(true, "xdkZip", "xdk").filter(XtcExtractXdkTask::isXdkArchive);

        if (archives.isEmpty()) {
            project.info("{} Project does NOT depend on the XDK; {} is a nop.", prefix, getName());
            return;
        }

        // If there are no archives, we do not depend on the XDK.
        final var archiveFile = archives.getSingleFile();

        // TODO: Should probably add a configuration xtcJavaToolsProvider.
        project.getProject().copy(config -> {
            project.info("{} CopySpec: XDK archive file dependency: {}", prefix, archiveFile);
            config.from(project.getProject().zipTree(archiveFile));
            // We just include javatools.jar, the xtc modules in the XDK and the VERSION file
            // (for manual sanity checks, can be done programmatically later.)
            // We would include native launchers, etc., if we want to support native
            // launchers as well later, which some team members need for their development projects.
            config.include(
                    "**/*." + XTC_MODULE_FILE_EXTENSION,
                    "**/" + JAVATOOLS_ARTIFACT_ID + "*jar",
                    XDK_VERSION_PATH);
            config.eachFile(fileCopyDetails -> fileCopyDetails.setRelativePath(new RelativePath(true, fileCopyDetails.getName())));
            config.setIncludeEmptyDirs(false);
            config.into(getOutputXtcModules());
        });

        project.info("{} Finished unpacking XDK archive: {} -> {}.", prefix, archiveFile.getAbsolutePath(), getOutputXtcModules().get());
    }
}

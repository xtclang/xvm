package org.xtclang.plugin.tasks;

import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_INCOMING;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_INCOMING_ZIP;
import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_ARTIFACT_ID;
import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_ARTIFACT_SUFFIX;
import static org.xtclang.plugin.XtcPluginConstants.XDK_VERSION_PATH;
import static org.xtclang.plugin.XtcPluginConstants.XTC_MODULE_FILE_EXTENSION;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.getFileExtension;

import java.io.File;

import javax.inject.Inject;

import org.gradle.api.Project;
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

import org.xtclang.plugin.XtcProjectDelegate;

@CacheableTask
public abstract class XtcExtractXdkTask extends XtcDefaultTask {
    private static final String XDK_ARCHIVE_DEFAULT_EXTENSION = "zip";

    @Inject
    public XtcExtractXdkTask(final Project project) {
        super(project);
    }

    private static boolean isXdkArchive(final File file) {
        return XDK_ARCHIVE_DEFAULT_EXTENSION.equals(getFileExtension(file));
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getInputXdkArchive() {
        return filesFromConfigs(XDK_CONFIG_NAME_INCOMING_ZIP, XDK_CONFIG_NAME_INCOMING);
    }

    @OutputDirectory
    Provider<Directory> getOutputXtcModules() {
        return XtcProjectDelegate.getXdkContentsDir(project);
    }

    @TaskAction
    public void extractXdk() {
        super.executeTask();

        // The task is configured at this point. We should indeed have found a zip archive from some xdkDistributionProvider somewhere.
        final var archives = filesFromConfigs(true, XDK_CONFIG_NAME_INCOMING_ZIP, XDK_CONFIG_NAME_INCOMING).filter(XtcExtractXdkTask::isXdkArchive);

        if (archives.isEmpty()) {
            logger.info("{} Project does NOT depend on the XDK; {} is a nop.", prefix(), getName());
            return;
        }

        // If there are no archives, we do not depend on the XDK.
        final var archiveFile = archives.getSingleFile();
        final var prefix = prefix();

        project.copy(config -> {
            logger.info("{} CopySpec: XDK archive file dependency: {}", prefix, archiveFile);
            config.from(project.zipTree(archiveFile));
            config.include(
                    "**/*." + XTC_MODULE_FILE_EXTENSION,
                    "**/" + XDK_JAVATOOLS_ARTIFACT_ID + '*' + XDK_JAVATOOLS_ARTIFACT_SUFFIX,
                    XDK_VERSION_PATH);
            config.eachFile(fileCopyDetails -> fileCopyDetails.setRelativePath(new RelativePath(true, fileCopyDetails.getName())));
            config.setIncludeEmptyDirs(false);
            config.into(getOutputXtcModules());
        });

        logger.info("{} Finished unpacking XDK archive: {} -> {}.", prefix, archiveFile.getAbsolutePath(), getOutputXtcModules().get());
    }
}

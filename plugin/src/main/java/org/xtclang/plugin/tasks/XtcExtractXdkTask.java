package org.xtclang.plugin.tasks;

import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_ARTIFACT_ID;
import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_ARTIFACT_SUFFIX;
import static org.xtclang.plugin.XtcPluginConstants.XTC_MODULE_FILE_EXTENSION;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.getFileExtension;

import java.io.File;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RelativePath;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class XtcExtractXdkTask extends XtcDefaultTask {
    private static final String XDK_ARCHIVE_DEFAULT_EXTENSION = "zip";

    // Injected services for configuration cache compatibility
    private final FileSystemOperations fileSystemOperations;
    private final ArchiveOperations archiveOperations;

    @SuppressWarnings("ConstructorNotProtectedInAbstractClass")
    @Inject
    public XtcExtractXdkTask(final FileSystemOperations fileSystemOperations, final ArchiveOperations archiveOperations) {
        super();

        // Store injected services
        this.fileSystemOperations = fileSystemOperations;
        this.archiveOperations = archiveOperations;
    }

    private static boolean isXdkArchive(final File file) {
        return XDK_ARCHIVE_DEFAULT_EXTENSION.equals(getFileExtension(file));
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getInputXdkArchive();

    @OutputDirectory
    public abstract org.gradle.api.file.DirectoryProperty getOutputXtcModules();

    @TaskAction
    public void extractXdk() {
        executeTask();

        // The task is configured at this point. We should indeed have found a zip archive from some xdkDistributionProvider somewhere.
        final var archives = getInputXdkArchive().filter(XtcExtractXdkTask::isXdkArchive);
        if (archives.isEmpty()) {
            logger.info("[plugin] Project does NOT depend on the XDK; {} is a nop.", getName());
            return;
        }

        // If there are no archives, we do not depend on the XDK.
        final var archiveFile = archives.getSingleFile();
        // Remove prefix variable since we're using hardcoded [plugin]

        fileSystemOperations.copy(config -> {
            logger.info("[plugin] CopySpec: XDK archive file dependency: {}", archiveFile);
            config.from(archiveOperations.zipTree(archiveFile));
            config.include(
                    "**/*." + XTC_MODULE_FILE_EXTENSION,
                    "**/" + XDK_JAVATOOLS_ARTIFACT_ID + '*' + XDK_JAVATOOLS_ARTIFACT_SUFFIX);
            config.eachFile(fileCopyDetails -> fileCopyDetails.setRelativePath(new RelativePath(true, fileCopyDetails.getName())));
            config.setIncludeEmptyDirs(false);
            config.into(getOutputXtcModules());
        });

        logger.info("[plugin] Finished unpacking XDK archive: {} -> {}.", archiveFile.getAbsolutePath(), getOutputXtcModules().get());
    }
}

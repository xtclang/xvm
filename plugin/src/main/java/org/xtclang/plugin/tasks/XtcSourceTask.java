package org.xtclang.plugin.tasks;

import static org.xtclang.plugin.XtcPluginConstants.XTC_SOURCE_FILE_EXTENSION;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.hasFileExtension;

import java.io.File;

import java.util.Set;

import javax.inject.Inject;

import groovy.lang.Closure;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcCompilerExtension;
import org.xtclang.plugin.XtcProjectDelegate;

public abstract class XtcSourceTask extends XtcLauncherTask<XtcCompilerExtension> implements PatternFilterable {
    // This is just necessary since we assume some things about module definition source locations. It should not be exported.
    private static final String XDK_TURTLE_SOURCE_FILENAME = "mack.x";

    private final PatternFilterable patternSet;

    private ConfigurableFileCollection sourceFiles;

    @SuppressWarnings("this-escape")
    protected XtcSourceTask(final Project project) {
        super(project, XtcProjectDelegate.resolveXtcCompileExtension(project));
        //noinspection OverridableMethodCallDuringObjectConstruction
        this.patternSet = getPatternSetFactory().create();
        this.sourceFiles = objects.fileCollection();
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Inject
    protected Factory<PatternSet> getPatternSetFactory() {
        throw new UnsupportedOperationException("XtcSourceTask.getPatternSetFactory()");
    }

    @SuppressWarnings("unused")
    @Internal
    protected PatternFilterable getPatternSet() {
        return patternSet;
    }

    /**
     * Returns the source for this task, after the include and exclude patterns have been applied. Ignores source files which do not exist.
     *
     * <p>
     * The {@link PathSensitivity} for the sources is configured to be {@link PathSensitivity#ABSOLUTE}.
     * If your sources are less strict, please change it accordingly by overriding this method in your subclass.
     * </p>
     *
     * @return The source.
     */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getSource() {
        return sourceFiles.getAsFileTree().matching(patternSet);
    }

    // Get the source

    /**
     * Sets the source for this task.
     *
     * @param source The source.
     */
    public void setSource(final FileTree source) {
        setSource((Object)source);
    }

    /**
     * Sets the source for this task. The given source object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param source The source.
     */
    public void setSource(final Object source) {
        sourceFiles = objects.fileCollection().from(source);
    }

    /**
     * Adds some source to this task. The given source objects will be evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param sources The source to add
     * @return this
     */
    @SuppressWarnings("unused")
    public XtcSourceTask source(final Object... sources) {
        sourceFiles.from(sources);
        return this;
    }

    @Override
    public @NotNull XtcSourceTask include(final String @NotNull... includes) {
        patternSet.include(includes);
        return this;
    }

    @Override
    public @NotNull XtcSourceTask include(final @NotNull Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    @Override
    public @NotNull XtcSourceTask include(final @NotNull Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public @NotNull XtcSourceTask include(final @NotNull Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    @Override
    public @NotNull XtcSourceTask exclude(final String @NotNull... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    @Override
    public @NotNull XtcSourceTask exclude(final @NotNull Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    @Override
    public @NotNull XtcSourceTask exclude(final @NotNull Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public @NotNull XtcSourceTask exclude(final @NotNull Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    @Override
    @Internal
    public @NotNull Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    @Override
    public @NotNull XtcSourceTask setIncludes(final @NotNull Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    @Override
    @Internal
    public @NotNull Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    @Override
    public @NotNull XtcSourceTask setExcludes(final @NotNull Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    protected static boolean isXtcSourceFile(final File file) {
        // TODO: Previously we called a Launcher method to ensure this was a module, but all these files should be in the top
        //   level directory of a source set, and this means that xtc will assume they are all module definitions, and fail if this
        //   is not the case. We used to check for this in the plugin, but we really do not want the compile time dependency to
        //   the javatools.jar in the plugin, as the plugin comes in early. This would have bad side effects, like "clean" would
        //   need to build the javatools.jar, if it wasn't there, just to immediately delete it again.
        return file.isFile() && hasFileExtension(file, XTC_SOURCE_FILE_EXTENSION);
    }

    protected boolean isTopLevelXtcSourceFile(final SourceSet sourceSet, final File file) {
        return !file.isDirectory() && isXtcSourceFile(file) && isTopLevelSource(sourceSet, file);
    }

    protected boolean isTopLevelSource(final SourceSet sourceSet, final File file) {
        assert file.isFile();
        final var topLevelSourceDirs = new java.util.HashSet<>(sourceSet.getAllSource().getSrcDirs());
        final var dir = file.getParentFile();
        assert dir != null && dir.isDirectory();
        final var isTopLevelSrc = topLevelSourceDirs.contains(dir);
        logger.debug("{} Checking if {} is a module definition (currently, just checking if it's a top level file): {}",
            prefix(), file.getAbsolutePath(), isTopLevelSrc);
        if (isTopLevelSrc || XDK_TURTLE_SOURCE_FILENAME.equalsIgnoreCase(file.getName())) {
            if (overrideVerboseLogging) {
                logger.info("{} Found module definition: {}", prefix(), file.getAbsolutePath());
            }
            return true;
        }
        return false;
    }
}

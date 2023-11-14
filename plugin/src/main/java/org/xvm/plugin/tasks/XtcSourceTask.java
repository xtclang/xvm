package org.xvm.plugin.tasks;

import groovy.lang.Closure;
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
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.jetbrains.annotations.NotNull;
import org.xvm.plugin.XtcProjectDelegate;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;

public abstract class XtcSourceTask extends XtcDefaultTask implements PatternFilterable {
    private final PatternFilterable patternSet;

    private ConfigurableFileCollection sourceFiles;

    public XtcSourceTask(final XtcProjectDelegate project) {
        super(project);
        this.patternSet = getPatternSetFactory().create();
        this.sourceFiles = project.getObjects().fileCollection();
    }

    @Inject
    protected Factory<PatternSet> getPatternSetFactory() {
        throw new UnsupportedOperationException();
    }

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
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileTree getSource() {
        return sourceFiles.getAsFileTree().matching(patternSet);
    }

    /**
     * Sets the source for this task.
     *
     * @param source The source.
     * @since 4.0
     */
    public void setSource(final FileTree source) {
        setSource((Object) source);
    }

    /**
     * Sets the source for this task. The given source object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param source The source.
     */
    public void setSource(final Object source) {
        sourceFiles = project.getObjects().fileCollection().from(source);
    }

    /**
     * Adds some source to this task. The given source objects will be evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param sources The source to add
     * @return this
     */
    public XtcSourceTask source(final Object... sources) {
        sourceFiles.from(sources);
        return this;
    }

    @Override
    public @NotNull XtcSourceTask include(final String @NotNull ... includes) {
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

    @SuppressWarnings("rawtypes") // We have no choice but to inherit a raw parameter type. Disable warning.
    @Override
    public @NotNull XtcSourceTask include(final @NotNull Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    @Override
    public @NotNull XtcSourceTask exclude(final String @NotNull ... excludes) {
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

    @SuppressWarnings("rawtypes") // We have no choice but to inherit a raw parameter type. Disable warning.
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

    /**
     * Update the lastModified on all source files to 'now' in the epoch. This is probably overkill, as it is used
     * only for "forceRebuild", which really making the compileXtc<SourceSetName> tasks uncacheable and never up
     * to date during configuration, should be enough to accomplish. TODO: Verify this.
     */
    protected void touchAllSource() {
        getSource().forEach(src -> {
            final var before = src.lastModified();
            final var after = touch(src);
            project.info("{} *** File: {} (before: {}, after: {})", prefix, src.getAbsolutePath(), before, after);
        });
        project.info("{} Updated lastModified of {}.getSource() and resources to 'now' in the epoch.", prefix, getName());
    }

    private long touch(final File file) {
        return touch(file, System.currentTimeMillis());
    }

    private long touch(final File file, final long now) {
        final var oldLastModified = file.lastModified();
        if (!file.setLastModified(now)) {
            project.warn("{} Failed to update modification time stamp for file: {}", prefix, file.getAbsolutePath());
        }
        project.info("{} Touch file: {} (timestamp: {} -> {})", prefix, file.getAbsolutePath(), oldLastModified, now);
        assert(file.lastModified() == now);
        return now;
    }
}

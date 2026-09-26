package org.xtclang.plugin.internal;

import javax.inject.Inject;

import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;

import org.xtclang.plugin.XtcSourceDirectorySet;

/**
 * Compatibility adapter for the typed XTC source-set DSL.
 *
 * <p>Gradle 9.7.1 recognizes nested source directories through an instanceof check
 * against DefaultSourceDirectorySet, rather than the public SourceDirectorySet interface.
 * A public-API forwarding wrapper is treated as a collection of source files used as
 * directory paths, breaking allSource composition and configuration-cache storage.
 * Keep this internal dependency confined here until Gradle supports public implementations.
 */
public abstract class DefaultXtcSourceDirectorySet extends DefaultSourceDirectorySet implements XtcSourceDirectorySet {
    @SuppressWarnings("ConstructorNotProtectedInAbstractClass") // need public ctor for injection
    @Inject
    public DefaultXtcSourceDirectorySet(final SourceDirectorySet sourceDirectorySet) {
        super(sourceDirectorySet, DefaultTaskDependencyFactory.withNoAssociatedProject());
    }
}

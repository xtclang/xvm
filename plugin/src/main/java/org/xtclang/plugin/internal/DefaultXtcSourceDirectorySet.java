package org.xtclang.plugin.internal;

import javax.inject.Inject;

import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;

import org.xtclang.plugin.XtcSourceDirectorySet;

public abstract class DefaultXtcSourceDirectorySet extends DefaultSourceDirectorySet implements XtcSourceDirectorySet {
    @Inject
    public DefaultXtcSourceDirectorySet(final SourceDirectorySet sourceDirectorySet) {
        super(sourceDirectorySet, DefaultTaskDependencyFactory.withNoAssociatedProject());
    }
}

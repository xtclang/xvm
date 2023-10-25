package org.xvm.plugin;

import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;

import javax.inject.Inject;

public abstract class DefaultXtcSourceDirectorySet extends DefaultSourceDirectorySet implements XtcSourceDirectorySet{
    @Inject
    public DefaultXtcSourceDirectorySet(final SourceDirectorySet sourceDirectorySet) {
        super(sourceDirectorySet, DefaultTaskDependencyFactory.withNoAssociatedProject());
    }
}

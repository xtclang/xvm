package org.xtclang.plugin.internal;

import org.gradle.api.Project;
import org.xtclang.plugin.XtcExtension;

//TODO: We may want to add things extensions like xtcLangGitHub() here.
public class DefaultXtcExtension implements XtcExtension {
    private final Project project;

    public DefaultXtcExtension(final Project project) {
        this.project = project;
    }
}

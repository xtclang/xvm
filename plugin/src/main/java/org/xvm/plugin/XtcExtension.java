package org.xvm.plugin;

import org.gradle.api.artifacts.repositories.ArtifactRepository;

public interface XtcExtension {
    void printVersion();
    ArtifactRepository xtcLangOrg();
}

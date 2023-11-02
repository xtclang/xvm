package org.xvm.plugin;

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;

import java.io.File;

//TODO: We may want to add things extensions like xtcLangGitHub() here.
public class DefaultXtcExtension implements XtcExtension {
    private final Project project;
    private final String prefix;

    public DefaultXtcExtension(final Project project) {
        this.project = project;
        this.prefix = ProjectDelegate.prefix(project);
    }

    @Override
    public void printVersion() {
        var pluginUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
        var ver = JavaExecLauncher.readXdkVersionFromJar(project.getLogger(), prefix, new File(pluginUrl.getPath()));
        project.getLogger().lifecycle("{} XTC language version supported by plugin: {}", prefix, ver == null ? "[unresolved]" : ver);
    }

    public ArtifactRepository xtcLangOrg() {
        project.getLogger().warn("{} xtgLangOrg() is not yet implemented!", prefix);
        return project.getRepositories().mavenLocal();
    }
}

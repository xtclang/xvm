package org.xtclang.plugin.tasks;

import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.XtcPluginUtils;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.launchers.XtcLauncher;

import java.io.InputStream;
import java.io.OutputStream;

import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcProjectDelegate.incomingXtcModuleDependencies;

/**
 * Abstract class that represents and XTC Launcher execution (i.e. Compiler, Runner, Disassembler etc.),
 * anything that goes through the XTC Launcher to spawn or call different processes
 */

public abstract class XtcLauncherTask<T extends XtcLauncherTaskExtension> extends XtcDefaultTask implements XtcLauncherTaskExtension {
    protected final SourceSet sourceSet;

    // All inherited from launcher task extension and turned into input
    protected final Property<InputStream> stdin;
    protected final Property<OutputStream> stdout;
    protected final Property<OutputStream> stderr;
    protected final ListProperty<String> jvmArgs;
    protected final Property<Boolean> isVerbose;
    protected final Property<Boolean> isFork;
    protected final Property<Boolean> useNativeLauncher;
    protected final Property<Boolean> logOutputs;

    private final T ext;

    protected XtcLauncherTask(final XtcProjectDelegate delegate, final SourceSet sourceSet, final T ext) {
        super(delegate);
        this.sourceSet = sourceSet;
        this.ext = ext;
        this.stdin = objects.property(InputStream.class);
        this.stdout = objects.property(OutputStream.class);
        this.stderr = objects.property(OutputStream.class);
        if (ext.getStdin().isPresent()) {
            stdin.value(ext.getStdin());
        }
        if (ext.getStdout().isPresent()) {
            stdout.value(ext.getStdout());
        }
        if (ext.getStderr().isPresent()) {
            stderr.value(ext.getStderr()); // TODO maybe rename the properties to stadardnOutput, errorOutput etc to conform to Gradle name standard. Right now we cleary want them to be separated from any defaults, though, so we know our launcher tasks pick the correct configured streams.
        }
        this.jvmArgs = objects.listProperty(String.class).convention(ext.getJvmArgs());
        this.isVerbose = objects.property(Boolean.class).convention(ext.getVerbose());
        this.isFork = objects.property(Boolean.class).convention(ext.getFork());
        this.useNativeLauncher = objects.property(Boolean.class).convention(ext.getUseNativeLauncher());
        this.logOutputs = objects.property(Boolean.class).convention(ext.getLogOutputs());
    }

    protected abstract XtcLauncher<T, ? extends XtcLauncherTask<T>> createLauncher();

    @Internal
    protected T getExtension() {
        return ext;
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getInputDeclaredDependencyModules() {
        return delegate.filesFrom(incomingXtcModuleDependencies(sourceSet)); // xtcModule and xtcModuleTest dependencies declared in the project dependency { scope section
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getInputXtcJavaToolsConfig() {
        return delegate.getProject().files(delegate.getProject().getConfigurations().getByName(XDK_CONFIG_NAME_JAVATOOLS_INCOMING));
    }

    public boolean overridesStdin() {
        return stdin.isPresent();
    }

    public boolean overridesStdout() {
        return stdout.isPresent();
    }

    public boolean overridesStderr() {
        return stderr.isPresent();
    }

    @Optional
    @Input
    public Property<InputStream> getStdin() {
        return stdin;
    }

    @Optional
    @Input
    public Property<OutputStream> getStdout() {
        return stdout;
    }

    @Optional
    @Input
    public Property<OutputStream> getStderr() {
        return stderr;
    }

    @Override
    public void jvmArgs(final Object... args) {
        jvmArgs.addAll(XtcPluginUtils.argumentArrayToList(args));
    }

    @Override
    public void jvmArgs(final Iterable<? extends String> args) {
        jvmArgs.addAll(args);
    }

    @Override
    public void jvmArgs(final Provider<? extends Iterable<? extends String>> provider) {
        jvmArgs.addAll(provider);
    }

    @Override
    public void setJvmArgs(final Iterable<? extends String> elements) {
        jvmArgs.set(elements);
    }

    @Override
    public void setJvmArgs(final Provider<? extends Iterable<? extends String>> provider) {
        jvmArgs.set(provider);
    }

    @Input
    public Property<Boolean> getVerbose() {
        return isVerbose;
    }

    @Input
    public Property<Boolean> getFork() {
        return isFork;
    }

    @Input
    public Property<Boolean> getUseNativeLauncher() {
        return useNativeLauncher;
    }

    @Input
    public Property<Boolean> getLogOutputs() {
        return logOutputs;
    }

    @Optional
    @Input
    public ListProperty<String> getJvmArgs() {
        return jvmArgs;
    }

    @Input
    public Property<Boolean> getIsVerbose() {
        return isVerbose;
    }
}

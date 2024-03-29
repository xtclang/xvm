package org.xtclang.plugin.internal;

import java.io.InputStream;
import java.io.OutputStream;

import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import org.xtclang.plugin.ProjectDelegate;
import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.XtcPluginUtils;

public abstract class DefaultXtcLauncherTaskExtension implements XtcLauncherTaskExtension {
    private static final List<String> DEFAULT_JVM_ARGS = List.of("-ea");

    protected final Project project;
    protected final String prefix;
    protected final ObjectFactory objects;
    protected final Logger logger;

    protected final ListProperty<String> jvmArgs;
    protected final Property<Boolean> debug;
    protected final Property<Integer> debugPort;
    protected final Property<Boolean> debugSuspend;
    protected final Property<Boolean> verbose;
    protected final Property<Boolean> fork;
    protected final Property<Boolean> showVersion;
    protected final Property<Boolean> useNativeLauncher;
    protected final Property<InputStream> stdin;
    protected final Property<OutputStream> stdout;
    protected final Property<OutputStream> stderr;

    protected DefaultXtcLauncherTaskExtension(final Project project) {
        this.project = project;
        this.prefix = ProjectDelegate.prefix(project);
        this.objects = project.getObjects();
        this.logger = project.getLogger();

        final var env = System.getenv();

        // TODO: Consider replacing the debug configuration with the debug flags inherited directly from its JavaExec extension
        //   DSL, or at least reimplementing them so that they look the same for different kinds of launchers.
        this.debug = objects.property(Boolean.class).convention(Boolean.parseBoolean(env.getOrDefault("XTC_DEBUG", "false")));
        this.debugPort = objects.property(Integer.class).convention(Integer.parseInt(env.getOrDefault("XTC_DEBUG_PORT", "4711")));
        this.debugSuspend = objects.property(Boolean.class).convention(Boolean.parseBoolean(env.getOrDefault("XTC_DEBUG_SUSPEND", "true")));

        this.jvmArgs = objects.listProperty(String.class).convention(DEFAULT_JVM_ARGS);
        this.verbose = objects.property(Boolean.class).convention(false);
        this.fork = objects.property(Boolean.class).convention(true);
        this.showVersion = objects.property(Boolean.class).convention(false);
        this.useNativeLauncher = objects.property(Boolean.class).convention(false);
        this.stdin = objects.property(InputStream.class);
        this.stdout = objects.property(OutputStream.class);
        this.stderr = objects.property(OutputStream.class);
    }

    // TODO: Sort public methods in alphabetical order for all these files, remove where just inheritance that has
    //  been added to the superclass already if any are left, and put public methods first.
    @Override
    public Property<InputStream> getStdin() {
        return stdin;
    }

    @Override
    public Property<OutputStream> getStdout() {
        return stdout;
    }

    @Override
    public Property<OutputStream> getStderr() {
        return stderr;
    }

    @Override
    public Property<Boolean> getFork() {
        return fork;
    }

    @Override
    public Property<Boolean> getShowVersion() {
        return showVersion;
    }

    @Override
    public Property<Boolean> getUseNativeLauncher() {
        return useNativeLauncher;
    }

    @Override
    public Property<Boolean> getVerbose() {
        return verbose;
    }

    @Override
    public Property<Boolean> getDebug() {
        return debug;
    }

    @Override
    public Property<Integer> getDebugPort() {
        return debugPort;
    }

    @Override
    public Property<Boolean> getDebugSuspend() {
        return debugSuspend;
    }

    @Override
    public ListProperty<String> getJvmArgs() {
        return jvmArgs;
    }

    @Override
    public void jvmArg(final Provider<? extends String> arg) {
        jvmArgs(XtcPluginUtils.singleArgumentIterableProvider(project, arg));
    }

    @Override
    public void jvmArgs(final String... args) {
        jvmArgs(XtcPluginUtils.argumentArrayToList(args));
    }

    @Override
    public void jvmArgs(final Iterable<? extends String> elements) {
        jvmArgs.addAll(elements);
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

    public static boolean hasModifiedJvmArgs(final List<String> jvmArgs) {
        return !DEFAULT_JVM_ARGS.equals(jvmArgs);
    }
}

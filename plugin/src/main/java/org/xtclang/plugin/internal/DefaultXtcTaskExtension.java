package org.xtclang.plugin.internal;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.xtclang.plugin.ProjectDelegate;
import org.xtclang.plugin.XtcTaskExtension;

import java.util.Arrays;
import java.util.List;

public abstract class DefaultXtcTaskExtension implements XtcTaskExtension {
    private static final List<String> DEFAULT_JVM_ARGS = List.of("-ea");

    protected final Project project;
    protected final String prefix;
    protected final ObjectFactory objects;
    protected final Logger logger;

    protected final ListProperty<String> jvmArgs;
    protected final Property<Boolean> isVerbose;
    protected final Property<Boolean> isFork;
    protected final Property<Boolean> useNativeLauncher;
    protected final Property<Boolean> logOutputs;

    protected DefaultXtcTaskExtension(final Project project) {
        this.project = project;
        this.prefix = ProjectDelegate.prefix(project);
        this.objects = project.getObjects();
        this.logger = project.getLogger();
        this.jvmArgs = objects.listProperty(String.class).value(DEFAULT_JVM_ARGS);
        this.isVerbose = objects.property(Boolean.class).value(false);
        this.isFork = objects.property(Boolean.class).value(true);
        this.useNativeLauncher = objects.property(Boolean.class).value(false);
        this.logOutputs = objects.property(Boolean.class).value(true);
    }

    @Override
    public Property<Boolean> getFork() {
        return isFork;
    }

    @Override
    public Property<Boolean> getUseNativeLauncher() {
        return useNativeLauncher;
    }

    @Override
    public Property<Boolean> getLogOutputs() {
        return logOutputs;
    }

    @Override
    public Property<Boolean> getVerbose() {
        return isVerbose;
    }

    @Override
    public ListProperty<String> getJvmArgs() {
        return jvmArgs;
    }

    @Override
    public XtcTaskExtension setJvmArgs(final Iterable<?> jvmArgs) {
        this.jvmArgs.get().clear();
        return jvmArgs(jvmArgs);
    }

    @Override
    public XtcTaskExtension setJvmArgs(final Object... jvmArgs) {
        this.jvmArgs.get().clear();
        return jvmArgs(jvmArgs);
    }

    @Override
    public XtcTaskExtension jvmArgs(final Iterable<?> jvmArgs) {
        jvmArgs.forEach(jvmArg -> this.jvmArgs.add(jvmArg.toString()));
        return this;
    }

    @Override
    public XtcTaskExtension jvmArgs(final Object... jvmArgs) {
        Arrays.stream(jvmArgs).forEach(jvmArg -> this.jvmArgs.add(jvmArg.toString()));
        return this;
    }

    public static boolean hasModifiedJvmArgs(final List<String> jvmArgs) {
        return !DEFAULT_JVM_ARGS.equals(jvmArgs);
    }
}

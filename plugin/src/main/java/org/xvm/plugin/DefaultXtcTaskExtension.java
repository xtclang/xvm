package org.xvm.plugin;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

abstract class DefaultXtcTaskExtension implements XtcTaskExtension {

    protected final Project project;
    protected final String prefix;
    protected final ObjectFactory objects;
    protected final Logger logger;

    private final ListProperty<String> jvmArgs;

    private static final List<String> DEFAULT_JVM_ARGS = List.of("-ea"); // "-Xmx4G");

    protected DefaultXtcTaskExtension(final Project project) {
        this.project = project;
        this.prefix = ProjectDelegate.prefix(project);
        this.objects = project.getObjects();
        this.logger = project.getLogger();
        this.jvmArgs = project.getObjects().listProperty(String.class).value(new ArrayList<>(DEFAULT_JVM_ARGS));
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
}

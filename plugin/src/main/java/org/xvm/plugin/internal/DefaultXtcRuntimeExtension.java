package org.xvm.plugin.internal;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.xvm.plugin.XtcRuntimeExtension;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.xvm.plugin.Constants.XTC_DEFAULT_RUN_METHOD_NAME_PREFIX;

public class DefaultXtcRuntimeExtension extends DefaultXtcTaskExtension implements XtcRuntimeExtension {

    // TODO: Make it possible to add to the module path, both here and in the XTC compiler with explicit paths.
    //  well in the compiler, it just corresponds to the SourceSet and you can edit that already (except for
    //  maybe the XDK handling behind the scenes). Anyway I'll make it a common property for both environments.
    //  You never know.
    public static class DefaultXtcRunModule implements XtcRunModule {
        private final Property<String> moduleName; // mandatory
        private final Property<String> methodName; // TODO check that name works
        private final ListProperty<String> args;

        @Inject
        @SuppressWarnings("unused")
        public DefaultXtcRunModule(final Project project) {
            this(project, null);
        }

        public DefaultXtcRunModule(final Project project, final String moduleName) {
            this(project, moduleName, XTC_DEFAULT_RUN_METHOD_NAME_PREFIX, emptyList());
        }

        public DefaultXtcRunModule(final Project project, final String moduleName, final String method, final List<String> args) {
            final var objects = requireNonNull(project.getObjects());
            this.moduleName = objects.property(String.class);
            this.methodName = objects.property(String.class).value(requireNonNull(method));
            this.args = objects.listProperty(String.class).value(requireNonNull(args));
            if (moduleName != null) {
                this.moduleName.set(moduleName);
            }
        }

        static List<Object> getModuleInputs(final XtcRunModule module) {
            return List.of(module.getModuleName(), module.getMethodName(), module.getArgs());
        }

        @Override
        public Property<String> getModuleName() {
            return moduleName;
        }

        @Override
        public Property<String> getMethodName() {
            return methodName;
        }

        @Override
        public ListProperty<String> getArgs() {
            return args;
        }

        @Override
        public void args(final List<String> args) {
            args(args.toArray());
        }

        @Override
        public void args(final Object... args) {
            this.args.addAll(Arrays.stream(args).map(String::valueOf).toList());
        }

        @Override
        public boolean validate() {
            return moduleName.isPresent() && methodName.isPresent() && args.isPresent();
        }

        @Override
        public String toString() {
            return "[xtcRunModule: moduleName='" + (moduleName.isPresent() ? moduleName.get() : "none") + "', methodName='" + methodName.get() + "', args=" + args.get() + ']';
        }
    }

    private final Property<Boolean> showVersion;
    private final Property<Boolean> allowParallel;
    private final Property<Boolean> enableDebug; // TODO: We can use this to enable debug logging in the XTC runtime, or to run the stdin debugger in interactive mode, perhaps?
    private final ListProperty<XtcRunModule> modules;

    // Just used for inputs, and should not be set.

    @Inject
    public DefaultXtcRuntimeExtension(final Project project) {
        super(project);
        this.showVersion = objects.property(Boolean.class).value(false);
        this.modules = objects.listProperty(XtcRunModule.class).value(emptyList());
        this.allowParallel = objects.property(Boolean.class).value(false);
        this.enableDebug = objects.property(Boolean.class).value(false);
    }

    public static XtcRunModule createModule(final Project project, final String name) {
        return new DefaultXtcRunModule(project, name);
    }

    private XtcRunModule createModule(final String name) {
        return createModule(project, name);
    }

    /**
     * Check if there are module { ... } declarations without names. TODO: Can use mandatory flag
     * NOTE: This function expects that the configuration phase is finished and everything resolves.
     */
    @Override
    public List<XtcRunModule> validatedModules() {
        return modules.get().stream().filter(m -> {
            if (!m.validate()) {
                logger.error("{} ERROR: XTC run module without module name was declared: {}", prefix, m);
                throw new GradleException(prefix + " Invalid module configuration: " + m);
            }
            return true;
        }).toList();
    }

    private XtcRunModule addModule(final XtcRunModule runModule) {
        modules.add(runModule);
        return runModule;
    }

    @Override
    public XtcRunModule module(final Action<XtcRunModule> action) {
        final var runModule = project.getObjects().newInstance(DefaultXtcRunModule.class, project);
        action.execute(runModule);
        logger.info("{} Resolved xtcRunModule configuration: {}", prefix, runModule);
        return addModule(runModule);
    }

    @Override
    public void moduleName(final String name) {
        addModule(createModule(name));
    }

    @Override
    public XtcRuntimeExtension moduleNames(final String... names) {
        Arrays.asList(names).forEach(this::moduleName);
        return this;
    }

    @Override
    public ListProperty<XtcRunModule> getModules() {
        return modules;
    }

    @Override
    public List<String> getModuleNames() {
        return modules.get().stream().map(m -> m.getModuleName().get()).toList();
    }

    @Override
    public List<String> getModuleMethods() {
        return modules.get().stream().map(m -> m.getMethodName().get()).toList();
    }

    @Override
    public List<String> getModuleArgs() {
        final var list = modules.get().stream().map(m -> m.getArgs().get()).toList().stream().flatMap(Collection::stream).toList();
        logger.lifecycle("{} flatmap args: ${}", prefix, list);
        return list;
    }

    @Override
    public XtcRuntimeExtension setModuleNames(final List<String> moduleNames) {
        modules.get().clear();
        moduleNames.forEach(this::moduleName);
        return this;
    }

    @Override
    public XtcRuntimeExtension setModuleNames(final String... moduleNames) {
        return setModuleNames(Arrays.asList(moduleNames));
    }

    @Override
    public XtcRuntimeExtension setModules(final List<XtcRunModule> modules) {
        this.modules.get().clear();
        modules.forEach(this::addModule);
        return this;
    }

    @Override
    public XtcRuntimeExtension setModules(final XtcRunModule... modules) {
        return setModules(Arrays.asList(modules));
    }

    @Override
    public Property<Boolean> getAllowParallel() {
        return allowParallel;
    }

    @Override
    public Property<Boolean> getDebugEnabled() {
        return enableDebug;
    }

    @Override
    public ListProperty<Object> getModuleInputs() {
        return objects.listProperty(Object.class).value(modules.get().stream().map(DefaultXtcRunModule::getModuleInputs).toList());
    }

    @Override
    public Property<Boolean> getShowVersion() {
        return showVersion;
    }

    @Override
    public boolean isEmpty() {
        return modules.get().isEmpty();
    }
}

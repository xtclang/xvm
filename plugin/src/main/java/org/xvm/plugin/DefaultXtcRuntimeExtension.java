package org.xvm.plugin;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.xvm.plugin.Constants.XTC_DEFAULT_RUN_METHOD_NAME_PREFIX;

public class DefaultXtcRuntimeExtension extends DefaultXtcTaskExtension implements XtcRuntimeExtension {

    public static class DefaultXtcRunModule implements XtcRunModule {
        private final Property<String> moduleName; // mandatory
        private final Property<String> method;
        private final ListProperty<String> args;

        @Inject
        @SuppressWarnings("unused")
        public DefaultXtcRunModule(final Project project) {
            this(project, null);
        }

        public DefaultXtcRunModule(final Project project, final String moduleName) {
            this(project, moduleName, XTC_DEFAULT_RUN_METHOD_NAME_PREFIX, Collections.emptyList());
        }

        public DefaultXtcRunModule(final Project project, final String moduleName, final String method, final List<String> args) {
            final var objects = requireNonNull(project.getObjects());
            this.moduleName = objects.property(String.class);
            this.method = objects.property(String.class).value(requireNonNull(method));
            this.args = objects.listProperty(String.class).value(requireNonNull(args));
            if (moduleName != null) {
                this.moduleName.set(moduleName);
            }
        }

        @Override
        public Property<String> getModuleName() {
            return moduleName;
        }

        @Override
        public Property<String> getMethod() {
            return method;
        }

        @Override
        public ListProperty<String> getArgs() {
            return args;
        }

        @Override
        public boolean validate() {
            return moduleName.isPresent() && method.isPresent() && args.isPresent();
        }

        @Override
        public String toString() {
            return "[xtcRunModule: moduleName=" + (moduleName.isPresent() ? moduleName.get() : "NO MODULE") + ", method=" + method.get() + ", args=" + args.get() + ']';
        }
    }

    private final Property<Boolean> isVerbose;
    private final Property<Boolean> showVersion;
    private final ListProperty<XtcRunModule> modules;
    private final Property<Boolean> allowParallel;
    private final Property<Boolean> enableDebug;
    private final Property<Boolean> useNativeLauncher;

    @Inject
    public DefaultXtcRuntimeExtension(final Project project) {
        super(project);
        this.isVerbose = objects.property(Boolean.class).value(false);
        this.showVersion = objects.property(Boolean.class).value(false);
        this.modules = objects.listProperty(XtcRunModule.class).value(Collections.emptyList());
        this.allowParallel = objects.property(Boolean.class).value(false);
        this.enableDebug = objects.property(Boolean.class).value(false);
        this.useNativeLauncher = objects.property(Boolean.class).value(false);
    }

    static XtcRunModule createModule(final Project project, final String name) {
        return new DefaultXtcRunModule(project, name);
    }

    XtcRunModule createModule(final String name) {
        return createModule(project, name);
    }

    private void addModules(final Collection<File> modules) {
        modules.forEach(m -> addModule(createModule(m.getName())));
    }

    /**
     * Check if there are module { ... } declaretions without names. TODO: Can use mandatory flag
     *
     * @return
     */
    public boolean validateModules() {
        for (final var m : getModules().get()) {
            if (!m.validate()) {
                logger.error("{} ERROR: XTC run module without module name was declared.", prefix);
                return false;
            }
        }
        return true;
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
    public XtcRunModule moduleName(final String name) {
        return addModule(createModule(name));
    }

    @Override
    public XtcRuntimeExtension moduleNames(final String... names) {
        Arrays.asList(names).forEach(this::moduleName);
        return this;
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
        this.modules.value(modules);
        assert this.modules.get().size() == modules.size();
        return this;
    }

    @Override
    public Property<Boolean> getUseNativeLauncher() { return useNativeLauncher; }

    @Override
    public Property<Boolean> getAllowParallel() {
        return allowParallel;
    }

    @Override
    public Property<Boolean> getDebugEnabled() {
        return enableDebug;
    }

    @Override
    public ListProperty<XtcRunModule> getModules() {
        return modules;
    }

    @Override
    public Property<Boolean> getVerbose() {
        return isVerbose;
    }

    @Override
    public Property<Boolean> getShowVersion() {
        return showVersion;
    }
}

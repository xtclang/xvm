package org.xtclang.plugin.internal;

import java.util.List;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.XtcRuntimeExtension;

public abstract class DefaultXtcRuntimeExtension extends DefaultXtcLauncherTaskExtension implements XtcRuntimeExtension {
    private final ListProperty<@NotNull XtcRunModule> modules;
    private final Property<@NotNull Boolean> parallel;

    @Inject
    @SuppressWarnings("ConstructorNotProtectedInAbstractClass")
    public DefaultXtcRuntimeExtension(final ObjectFactory objects, final ProviderFactory providers) {
        super(objects, providers);
        this.modules = objects.listProperty(XtcRunModule.class).value(List.of());
        this.parallel = objects.property(Boolean.class).convention(false);
    }

    private XtcRunModule createModule(final String moduleName) {
        final var runModule = objects.newInstance(DefaultXtcRunModule.class);
        runModule.getModuleName().set(moduleName);
        return runModule;
    }

    private XtcRunModule addModule(final XtcRunModule runModule) {
        modules.add(runModule);
        return runModule;
    }

    @Override
    public XtcRunModule module(final Action<@NotNull XtcRunModule> action) {
        final var runModule = objects.newInstance(DefaultXtcRunModule.class);
        action.execute(runModule);
        return addModule(runModule);
    }

    @Override
    public void moduleName(final String name) {
        addModule(createModule(name));
    }

    @Override
    public void moduleNames(final String... names) {
        List.of(names).forEach(this::moduleName);
    }

    @Override
    public ListProperty<@NotNull XtcRunModule> getModules() {
        return modules;
    }

    @Override
    public void setModuleNames(final List<String> moduleNames) {
        modules.empty();
        moduleNames.forEach(this::moduleName);
    }

    @Override
    public void setModuleNames(final String... moduleNames) {
        setModuleNames(List.of(moduleNames));
    }

    @Override
    public void setModules(final List<XtcRunModule> modules) {
        this.modules.empty();
        modules.forEach(this::addModule);
    }

    @Override
    public void setModules(final XtcRunModule... modules) {
        setModules(List.of(modules));
    }

    @Override
    public Property<@NotNull Boolean> getParallel() {
        return parallel;
    }

    @Override
    public int size() {
        return modules.get().size();
    }
}
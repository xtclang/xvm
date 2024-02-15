package org.xtclang.plugin.internal;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.XtcRuntimeExtension;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;

public class DefaultXtcRuntimeExtension extends DefaultXtcLauncherTaskExtension implements XtcRuntimeExtension {
    // TODO: Make it possible to add to the module path, both here and in the XTC compiler with explicit paths.
    //  well in the compiler, it just corresponds to the SourceSet and you can edit that already (except for
    //  maybe the XDK handling behind the scenes). Anyway I'll make it a common property for both environments.
    //  You never know.

    /**
     * Should we enable the debugger for this tas+k? Note that the "classic" Java attach way of debugging
     * runs into some complications with Gradle. Here is a good source of information for debugging
     * a Gradle build with "classic Java style", and it may well involve reinvoking Gradle with
     * --no-daemon and --no-build-cache. See e.g.
     * "<a href="https://www.thomaskeller.biz/blog/2020/11/17/debugging-with-gradle/">...</a>"
     *
     * <p>
     * We are working on a more seamless approach, triggered on assert:debug in Ecstasy code, and
     * controlling the executing process. While Gradle eats and throws away STDIN in lots of
     * configurations, the "robust" way to make what we are trying to work, is to redirect
     * stdin and use it to talk to a debugger over the console. This requires, e.g. changing
     * the scope of a JavaExec so that it runs in the Gradle build process, with daemons enabled.
     * To have a "fits all" solution to temporarily fall back on the existing XTC debugger,
     * we would also need to create a Gradle compatible console handler and use it to display
     * output and request user input (debugger) commands. That should be possible just by starting
     * a compile or run task with special changes to configuration (e.g. fork = false, stdin = ...)
     * <p>
     * Ideally, we'd like full control over this in only one place, the build/run system with
     * Gradle/Maven, or completely in the XDK implementation. For the latter, which will also
     * help us break out and port current functionality to the WIP XTC Language Server, it makes
     * sense to add an abstraction layer for communication between the user and the debugger
     * process. This interface could run over sockets or whatever is available, and would
     * contain one implementation of the interface that implements the current stdout/stdin
     * mode, that is the only way to talk to the XTC debugger ATM.
     */
    private final ListProperty<XtcRunModule> modules;

    @Inject
    public DefaultXtcRuntimeExtension(final Project project) {
        super(project);
        this.modules = objects.listProperty(XtcRunModule.class).value(emptyList());
        // Check for a command line module as
    }

    public static XtcRunModule createModule(final Project project, final String moduleName) {
        return new DefaultXtcRunModule(project, moduleName);
    }

    public static XtcRunModule createModule(final Project project, final String moduleName, final String moduleMethod, final List<String> moduleArgs) {
        return new DefaultXtcRunModule(project, moduleName, moduleMethod, moduleArgs);
    }

    private XtcRunModule createModule(final String moduleName) {
        return createModule(project, moduleName);
    }

    private XtcRunModule addModule(final XtcRunModule runModule) {
        logger.info("{} Adding module {}", prefix, runModule);
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
        // TODO:
        // This is actually potential for a bug due to confusing syntax.. The module action also adds the runmodule, if it contains a moduleName(...) then it's not a setter'
        // We should do:
        //   moduleName("a")
        //   moduleName("b")
        // outside a closure. If we do it inside a closure, that means add extra modules names!
        // and so on. If we combine module {
        addModule(createModule(name));
    }

    @Override
    public void moduleNames(final String... names) {
        Arrays.asList(names).forEach(this::moduleName);
    }

    @Override
    public ListProperty<XtcRunModule> getModules() {
        return modules;
    }

    @Override
    public void setModuleNames(final List<String> moduleNames) {
        modules.get().clear();
        moduleNames.forEach(this::moduleName);
    }

    @Override
    public void setModuleNames(final String... moduleNames) {
        setModuleNames(Arrays.asList(moduleNames));
    }

    @Override
    public void setModules(final List<XtcRunModule> modules) {
        this.modules.get().clear();
        modules.forEach(this::addModule);
    }

    @Override
    public void setModules(final XtcRunModule... modules) {
        setModules(Arrays.asList(modules));
    }

    @Override
    public int size() {
        return modules.get().size();
    }
}

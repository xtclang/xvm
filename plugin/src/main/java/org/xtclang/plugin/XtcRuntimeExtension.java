package org.xtclang.plugin;

import java.util.List;

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;

import org.jetbrains.annotations.NotNull;

/**
 * This is the "xtcRun" extension. It can be used to set up common runtime
 * behavior on a per-project level, similar to e.g. the "application" configuration
 * in the Gradle built-in plugins. If you want a project to use the default run task
 * semantics, and run a specific sequence of modules, this is where they are specified.
 * <p>
 * As with every other extension, by convention, it should be possible to change, extend
 * or override parts of it on individual run task level, so you should be able to create
 * a specialized run task with, e.g. verbose mode on, even though it's not on for
 * the entire project. You should also be able to create a specialized run task which
 * alters the sequence of modules to run, runs them in parallel with special logic
 * or e.g. on-the-fly compiles things it may need from other places than the current
 * project environment. Of course, if you create non-standard functionality, and it
 * affects inputs or outputs required for a task, it is your responsibility to declare
 * them.
 */
public interface XtcRuntimeExtension extends XtcLauncherTaskExtension {

    /**
     * Return the plugin internal representation of a module execution config. This
     * is a tuple with a module name (mandatory), a method name for the execution
     * to start with (defaults to "run"), and a list of arguments to pass to the
     * launcher that performs the execution. The arguments, as per Gradle convention,
     * should be possible to be configured lazily as a provider, so they can be calculated
     * at the time of execution, and not at the time of configuration.
     * <p>
     * TODO: This is not supported everywhere yet, but please feel free to add DSL logic
     *   for that, where you find it missing, and unit tests to make sure it works.
     *
     * @param action Build DSL run module configuration
     * @return XtcRunModule instance
     */
    XtcRunModule module(Action<@NotNull XtcRunModule> action);

    ListProperty<@NotNull XtcRunModule> getModules();

    void moduleName(String name);

    void moduleNames(String... modules);

    void setModules(List<XtcRunModule> modules);

    void setModules(XtcRunModule... modules);

    void setModuleNames(List<String> moduleNames);

    void setModuleNames(String... moduleNames);

    int size();

    /**
     * Does this extension declare any modules to be resolved and executed?
     * @return true if module is empty, with nothing declared, false otherwise.
     */
    default boolean isEmpty() {
        return size() == 0;
    }
}

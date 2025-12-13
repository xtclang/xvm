package org.xtclang.plugin.launchers;

import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcRunTask;
import org.xtclang.plugin.tasks.XtcTestTask;

/**
 * Strategy for executing XTC launcher tasks (compile or run).
 * Implementations support both compile and run execution.
 */
public interface ExecutionStrategy {

    ExecutionMode getMode();

    /**
     * Execute a compile task using this strategy.
     *
     * @param task The compile task to execute
     * @return Exit code (0 for success, non-zero for failure)
     */
    int execute(XtcCompileTask task);

    /**
     * Execute a run task for a specific module using this strategy.
     *
     * @param task The run task to execute
     * @param runConfig The module configuration (contains moduleName, methodName, args)
     * @return Exit code (0 for success, non-zero for failure)
     */
    int execute(XtcRunTask task, XtcRunModule runConfig);

    /**
     * Execute a test task for a specific module using this strategy.
     *
     * @param task The test task to execute
     * @param runConfig The module configuration (contains moduleName, methodName, args)
     * @return Exit code (0 for success, non-zero for failure)
     */
    int execute(XtcTestTask task, XtcRunModule runConfig);
}

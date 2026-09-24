package org.xtclang.plugin.launchers;

import java.util.List;

import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;

import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.runtime.PersistentRuntimeBuildService;
import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcRunTask;
import org.xtclang.plugin.tasks.XtcTestTask;

/** Explicit opt-in execution in a worker that survives build completion. */
public final class PersistentStrategy implements ExecutionStrategy {
    private final Logger logger;
    private final String java;
    private final Provider<PersistentRuntimeBuildService> service;

    public PersistentStrategy(final Logger logger, final String java, final Provider<PersistentRuntimeBuildService> service) {
        this.logger = logger;
        this.java = java;
        this.service = service;
    }

    @Override
    public ExecutionMode getMode() { return ExecutionMode.PERSISTENT; }

    @Override
    public int execute(final XtcCompileTask task) {
        return service.get().execute(task.resolveLauncherRuntime(), List.copyOf(task.getInputXdkContents().getFiles()),
            java, task.getJvmArgs().get(), DirectStrategy.createCompileRequest(task), logger);
    }

    @Override
    public int execute(final XtcRunTask task, final XtcRunModule config) {
        rejectJit(task);
        return service.get().execute(task.resolveLauncherRuntime(), DirectStrategy.executionModules(task),
            java, task.getJvmArgs().get(), DirectStrategy.createRunRequest(task, config), logger);
    }

    @Override
    public int execute(final XtcTestTask task, final XtcRunModule config) {
        rejectJit(task);
        return service.get().execute(task.resolveLauncherRuntime(), DirectStrategy.executionModules(task),
            java, task.getJvmArgs().get(), DirectStrategy.createTestRequest(task, config), logger);
    }

    private static void rejectJit(final XtcRunTask task) {
        if (task.getJit().get()) {
            throw new UnsupportedOperationException("PERSISTENT currently supports interpreter execution; use ATTACHED for JIT");
        }
    }
}

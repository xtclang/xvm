package org.xvm.plugin;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.LogLevel;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Class used to keep track of the state of the ongoing build for an XTC
 * project. We can also query it about the state of the build in general.
 * <p>
 * For example, we have sanity checks here, so that we can assert on the
 * lifecycle phase we are in, and if we are in configuration or evaluation.
 */
// TODO: Use flow instead of buildListeners
@SuppressWarnings("unused")
public class StateListeners extends ProjectDelegate<Void, Void> {
    private final XtcBuildListener buildListener;
    private final XtcTaskListener taskListener;
    private final LogLevel logLevel;

    private boolean settingsEvaluated;
    private boolean projectsEvaluated;
    private boolean projectsLoaded; // loaded from settints, but no projects are evaluated yet
    private BuildResult buildResult;

    public StateListeners(final Project project) {
        this(project, LogLevel.INFO);
    }

    public StateListeners(final Project project, final LogLevel logLevel) {
        super(project);
        this.buildListener = new XtcBuildListener();
        this.taskListener = new XtcTaskListener();
        this.logLevel = logLevel;
    }

    @Override
    protected Void apply(final Void ignored) {
        gradle.addBuildListener(buildListener);
        gradle.getTaskGraph().addTaskExecutionGraphListener(taskListener);
        return null;
    }

    public boolean areSettingsEvaluated() {
        return settingsEvaluated;
    }

    public boolean areProjectsEvaluated() {
        return projectsEvaluated;
    }

    public boolean areProjectsLoaded() {
        return projectsLoaded;
    }

    public boolean isBuildFinished() {
        return buildResult != null;
    }

    public boolean isConfigurationPhaseFinished() {
        return projectsEvaluated;
    }

    public boolean isExecutionPhaseFinished() {
        return projectsEvaluated;
    }

    public boolean isTaskExecuted(final Task task) {
        return isTaskExecuted(task.getName());
    }

    public boolean isTaskExecuted(final String name) {
        return taskListener.isTaskExecuted(name);
    }

    private class XtcBuildListener implements BuildListener {
        @Override
        public void settingsEvaluated(@NotNull final Settings settings) {
            final var buildScript = settings.getBuildscript();
            final var sourceFile = buildScript.getSourceFile();
            log(logLevel, "{}, settingsEvaluated (build script: {}, source: {})", prefix, buildScript, sourceFile);
            settingsEvaluated = true;
        }

        @Override
        public void projectsLoaded(@NotNull final Gradle gradle) {
            log(logLevel, "{} Projects are now loaded (created from settings, but not configured).", prefix);
            projectsLoaded = true;
        }

        @Override
        public void projectsEvaluated(final @NotNull Gradle gradle) {
            log(logLevel, "{} Projects are now evaluated (configuration phase is over).", prefix);
            projectsEvaluated = true;
        }

        @Override
        public void buildFinished(@NotNull final BuildResult result) {
            log(logLevel, "{} Build is now finished (execution phase is over).", prefix);
            buildResult = result;
        }
    }

    private class XtcTaskListener implements TaskExecutionGraphListener {
        private final Map<String, Set<Task>> dependencyMap = new HashMap<>();

        public boolean isTaskExecuted(final String taskName) {
            checkPopulated();
            return dependencyMap.containsKey(taskName);
        }

        @Override
        public void graphPopulated(final TaskExecutionGraph graph) {
            graph.getAllTasks().forEach(task -> dependencyMap.put(task.getName(), graph.getDependencies(task)));
            logTaskGraph();
        }

        private void logTaskGraph() {
            log(logLevel, "{} Task execution graph populated with {} tasks: {}",
                    prefix, tasks.size(), dependencyMap.keySet());

            dependencyMap.forEach((taskName, taskDeps) -> {
                log(logLevel, "{} {} (executed: {}), dependencies: {}", prefix, taskName, isTaskExecuted(taskName), taskDeps.isEmpty() ? "(none)" : taskDeps.size());
                taskDeps.forEach(d -> log(logLevel, "{}    {} <- {} (executed: {})", prefix, taskName, getDependencyNotation(d), d.getState().getExecuted()));
            });
        }

        private void checkPopulated() {
            if (dependencyMap.isEmpty()) {
                throw new IllegalStateException("Task execution graph has not been populated yet.");
            }
        }

        private String getDependencyNotation(final Task dependsOn) {
            final var dependsOnProjectName = dependsOn.getProject().getName();
            final var dependsOnTaskName = dependsOn.getName();
            if (prefix.equals(dependsOnProjectName)) {
                return dependsOnTaskName;
            }
            return dependsOnProjectName + ':' + dependsOnTaskName;
        }
    }
}

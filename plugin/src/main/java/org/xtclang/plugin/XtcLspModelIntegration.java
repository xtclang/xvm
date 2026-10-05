package org.xtclang.plugin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcLspModelTask;
import org.xtclang.plugin.tasks.XtcLspSourceSetTask;

/** Configuration-time wiring for a portable model shared by Community IntelliJ and VS Code. */
final class XtcLspModelIntegration {
    private XtcLspModelIntegration() {}

    static void register(final Project project, final SourceSet sourceSet,
                         final TaskProvider<XtcCompileTask> compile,
                         final TaskProvider<Copy> resources) {
        final var root = project.getRootProject();
        final var rootTasks = root.getTasks();
        final var aggregate = rootTasks.findByName("exportXtcLspModel") == null
            ? rootTasks.register("exportXtcLspModel", XtcLspModelTask.class, task -> {
                task.setGroup("IDE");
                task.setDescription("Export evaluated XTC compiler inputs for language servers");
                task.getModel().set(root.getLayout().getProjectDirectory().file(".gradle/xtc/lsp-model.json"));
            }) : rootTasks.named("exportXtcLspModel", XtcLspModelTask.class);
        final var prepare = rootTasks.findByName("prepareXtcLspModel") == null
            ? rootTasks.register("prepareXtcLspModel", task -> {
                task.setGroup("IDE");
                task.setDescription("Prepare processed XTC resources and refresh the IDE input model");
                task.dependsOn(aggregate);
            }) : rootTasks.named("prepareXtcLspModel");
        final var model = project.getTasks().register(sourceSet.getTaskName("export", "XtcLspInputs"), XtcLspSourceSetTask.class, task -> {
            final var compilation = compile.get();
            final var xtc = (XtcSourceDirectorySet) sourceSet.getExtensions().getByName("xtc");
            final var buildId = root.getProjectDir().toURI() + "#";
            task.getProjectId().set(buildId + project.getPath());
            task.getProjectPath().set(project.getPath());
            task.getProjectDirectory().set(project.getProjectDir().toURI().toString());
            task.getBuildFile().set(project.getBuildFile().toURI().toString());
            task.getSourceSetName().set(sourceSet.getName());
            task.getResourceTask().set(resources.get().getPath());
            final var sourceDirectories = xtc.getSourceDirectories();
            final var resourceDirectories = sourceSet.getResources().getSourceDirectories();
            task.getSourceRoots().set(sourceDirectories.getElements().map(files -> files.stream().map(file -> file.getAsFile().toURI().toString()).toList()));
            task.getResourceSourceRoots().set(resourceDirectories.getElements().map(files -> files.stream().map(file -> file.getAsFile().toURI().toString()).toList()));
            // Use the exact destination read by compileXtc, including user Copy/filter/rename setup.
            // Metadata alone does not execute the Copy task; prepareXtcLspModel requests it explicitly.
            task.getResourceRoots().set(project.provider(() ->
                List.of(resources.get().getDestinationDir().toURI().toString())));
            task.getSourceFiles().from(compilation.getSource());
            final var configurations = sourceSet.getName().equals(SourceSet.TEST_SOURCE_SET_NAME)
                ? List.of(project.getConfigurations().getByName("xtcModule"), project.getConfigurations().getByName("xtcModuleTest"))
                : List.of(project.getConfigurations().getByName(XtcProjectDelegate.incomingXtcModuleDependencies(sourceSet)));
            final var buildPath = root.getBuildTreePath();
            final var buildDirectories = Stream.concat(
                Stream.of(Map.entry(buildPath, root.getProjectDir().toURI().toString())),
                root.getGradle().getIncludedBuilds().stream().map(included ->
                    Map.entry(":" + included.getName(), included.getProjectDir().toURI().toString())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first));
            configurations.forEach(configuration -> task.getProjectDependencies().addAll(
                configuration.getIncoming().getResolutionResult().getRootComponent().map(component ->
                    component.getDependencies().stream().filter(ResolvedDependencyResult.class::isInstance)
                        .map(ResolvedDependencyResult.class::cast).map(dependency -> dependency.getSelected().getId())
                        .filter(ProjectComponentIdentifier.class::isInstance).map(ProjectComponentIdentifier.class::cast)
                        .filter(id -> buildDirectories.containsKey(id.getBuild().getBuildPath()))
                        .map(id -> buildDirectories.get(id.getBuild().getBuildPath()) + '#' + id.getProjectPath())
                        .distinct().sorted().toList())));
            final var mainOutput = XtcProjectDelegate.getXtcSourceSetOutputDirectory(project,
                XtcProjectDelegate.getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME));
            configurations.forEach(configuration -> task.getModulePath().from(configuration.getIncoming().artifactView(view ->
                view.componentFilter(id -> !(id instanceof ProjectComponentIdentifier component) || !buildDirectories.containsKey(component.getBuild().getBuildPath())))
                .getFiles().filter(file -> !file.equals(mainOutput.get().getAsFile()))));
            task.getReport().set(project.getLayout().getBuildDirectory().file("xtc/lsp/" + sourceSet.getName() + ".json"));
            task.mustRunAfter(resources);
        });
        aggregate.configure(task -> task.getReports().from(model.flatMap(XtcLspSourceSetTask::getReport)));
        prepare.configure(task -> task.dependsOn(resources));
    }
}

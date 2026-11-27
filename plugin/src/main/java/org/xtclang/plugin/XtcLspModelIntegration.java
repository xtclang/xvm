package org.xtclang.plugin;

import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_INCOMING;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_INCOMING_ZIP;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.Project;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.provider.Provider;
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
            task.getBuildTreePath().set(project.getBuildTreePath());
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
            // What the project's xtcRun and xtcTest run its modules with, which runXtc and testXtc take unless their own
            // configuration changes it, for a language server's embedded runs. The extensions are read, not the tasks, which
            // the export neither realizes nor runs, so that no task's configuration can fail an import; read once the build
            // is configured, and never so that a value that cannot be read fails the export: XtcLspRunSettings leaves it out
            // and says so.
            final var runExtension = XtcProjectDelegate.resolveXtcRuntimeExtension(project);
            final var testExtension = XtcProjectDelegate.resolveXtcTestExtension(project);
            final var run = project.provider(() -> XtcLspRunSettings.of(runExtension, true));
            final var test = project.provider(() -> XtcLspRunSettings.of(testExtension, false));
            task.getRunModuleMethods().set(run.map(XtcLspRunSettings::methods));
            task.getRunModuleArgs().set(run.map(XtcLspRunSettings::arguments));
            task.getRunJvmArgs().set(run.map(XtcLspRunSettings::jvmArgs));
            task.getRunProblems().set(run.map(XtcLspRunSettings::problems));
            task.getTestJvmArgs().set(test.map(XtcLspRunSettings::jvmArgs));
            task.getTestProblems().set(test.map(XtcLspRunSettings::problems));
            // The XDK that the project's compileXtc, runXtc and testXtc take, so that a language server can tell whether it
            // is its own. The distribution of one from a repository, or of a file dependency, is read, not extracted, as
            // far as it can be fetched; one that a build of the build tree makes is neither built nor read for the export,
            // nor is the directory that an earlier build extracted an XDK into; one that does not resolve says why. No
            // failure to resolve or fetch it fails the export.
            final var xdk = selectedXdk(project);
            final var libraries = XtcProjectDelegate.getXdkContentsDir(project);
            task.getXdkVersion().set(xdk.map(SelectedXdk::version));
            task.getXdkLibraries().set(xdk.flatMap(selected -> libraries.map(directory -> directoryUri(directory.getAsFile()))));
            task.getXdkProject().set(xdk.map(SelectedXdk::project));
            task.getXdkProblem().set(xdk.map(SelectedXdk::problem));
            Stream.of(XDK_CONFIG_NAME_INCOMING_ZIP, XDK_CONFIG_NAME_INCOMING).map(project.getConfigurations()::getByName)
                .forEach(configuration -> {
                    task.getXdkArchive().from(configuration.getIncoming().artifactView(view -> {
                        view.lenient(true);
                        view.componentFilter(ModuleComponentIdentifier.class::isInstance);
                    }).getFiles());
                    configuration.getDependencies().withType(FileCollectionDependency.class)
                        .forEach(dependency -> task.getXdkArchive().from(dependency.getFiles()));
                });
            // A problem, such as a download that failed, must not outlive the import that met it: the configuration
            // cache would keep what was resolved. The export of one writes a new token here, which the next import reads
            // as it configures the build, so that it configures it again and resolves the XDK anew.
            final var retry = project.getLayout().getBuildDirectory().file("xtc/lsp/" + sourceSet.getName() + ".xdk-retry");
            task.getXdkRetry().set(project.getProviders().fileContents(retry).getAsText().getOrElse(""));
            task.getXdkRetryMarker().set(retry);
            task.getReport().set(project.getLayout().getBuildDirectory().file("xtc/lsp/" + sourceSet.getName() + ".json"));
            task.mustRunAfter(resources);
        });
        aggregate.configure(task -> task.getReports().from(model.flatMap(XtcLspSourceSetTask::getReport)));
        prepare.configure(task -> task.dependsOn(resources));
    }

    /**
     * The XDK that {@code project}'s {@code xdkDistribution} dependency selects, or else its {@code xdk} dependency, as
     * the extraction of the XDK takes either, or asks for where it does not resolve; that of a file dependency, which
     * names no version; none without one.
     */
    private static Provider<SelectedXdk> selectedXdk(final Project project) {
        final var configurations = project.getConfigurations();
        final var archives = configurations.getByName(XDK_CONFIG_NAME_INCOMING_ZIP);
        final var directories = configurations.getByName(XDK_CONFIG_NAME_INCOMING);
        final var distribution = archives.getIncoming().getResolutionResult().getRootComponent();
        final var contents = directories.getIncoming().getResolutionResult().getRootComponent();
        final var filed = Stream.of(archives, directories)
            .anyMatch(configuration -> !configuration.getDependencies().withType(FileCollectionDependency.class).isEmpty());
        return distribution.flatMap(archive -> contents.map(directory -> SelectedXdk.of(archive).or(() -> SelectedXdk.of(directory))
            .orElse(filed ? new SelectedXdk(null, null, null) : null)));
    }

    /** {@code directory}'s file URI, which ends with a slash also before the directory exists. */
    private static String directoryUri(final File directory) {
        final var uri = directory.toURI().toString();
        return uri.endsWith("/") ? uri : uri + '/';
    }

    /**
     * An XDK that a project's dependency selects or asks for: its {@code version}; the path in the build tree of the
     * {@code project} that makes it, for one that a build of the build tree makes rather than a repository has; and the
     * {@code problem}, for one that does not resolve.
     */
    private record SelectedXdk(String version, String project, String problem) {
        /** The XDK that {@code root}'s first dependency selects, or asks for where it does not resolve, if it has one. */
        static Optional<SelectedXdk> of(final ResolvedComponentResult root) {
            return root.getDependencies().stream().findFirst().map(dependency -> {
                if (dependency instanceof ResolvedDependencyResult resolved) {
                    final var selected = resolved.getSelected();
                    final var version = selected.getModuleVersion() == null ? Project.DEFAULT_VERSION : selected.getModuleVersion().getVersion();
                    return new SelectedXdk(version,
                        selected.getId() instanceof ProjectComponentIdentifier built ? built.getBuildTreePath() : null, null);
                }
                final var failure = ((UnresolvedDependencyResult) dependency).getFailure().getMessage();
                return new SelectedXdk(dependency.getRequested() instanceof ModuleComponentSelector asked ? asked.getVersion() : Project.DEFAULT_VERSION,
                    null, "the import could not resolve it (" + (failure == null ? "no reason given" : failure.lines().findFirst().orElse("")) + ')');
            });
        }
    }
}

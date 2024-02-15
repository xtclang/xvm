package org.xtclang.plugin;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.xtclang.plugin.internal.DefaultXtcCompilerExtension;
import org.xtclang.plugin.internal.DefaultXtcExtension;
import org.xtclang.plugin.internal.DefaultXtcRuntimeExtension;
import org.xtclang.plugin.internal.DefaultXtcSourceDirectorySet;
import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcExtractXdkTask;
import org.xtclang.plugin.tasks.XtcRunTask;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.Category.LIBRARY;
import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE;
import static org.gradle.api.plugins.ApplicationPlugin.APPLICATION_GROUP;
import static org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME;
import static org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP;
import static org.xtclang.plugin.XtcPluginConstants.UNSPECIFIED;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_ARTIFACT_JAVATOOLS_FATJAR;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_CONTENTS;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_INCOMING;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_INCOMING_ZIP;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_OUTGOING;
import static org.xtclang.plugin.XtcPluginConstants.XDK_EXTRACT_TASK_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XDK_LIBRARY_ELEMENT_TYPE;
import static org.xtclang.plugin.XtcPluginConstants.XDK_LIBRARY_ELEMENT_TYPE_XDK_CONTENTS;
import static org.xtclang.plugin.XtcPluginConstants.XDK_VERSION_FILE_TASK_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XDK_VERSION_GROUP_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XDK_VERSION_PATH;
import static org.xtclang.plugin.XtcPluginConstants.XDK_VERSION_TASK_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_CONFIG_NAME_INCOMING;
import static org.xtclang.plugin.XtcPluginConstants.XTC_CONFIG_NAME_OUTGOING;
import static org.xtclang.plugin.XtcPluginConstants.XTC_DEFAULT_RUN_METHOD_NAME_PREFIX;
import static org.xtclang.plugin.XtcPluginConstants.XTC_EXTENSION_NAME_COMPILER;
import static org.xtclang.plugin.XtcPluginConstants.XTC_EXTENSION_NAME_RUNTIME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_LANGUAGE_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_SOURCE_FILE_EXTENSION;
import static org.xtclang.plugin.XtcPluginConstants.XTC_SOURCE_SET_DIRECTORY_ROOT_NAME;
import static org.xtclang.plugin.XtcPluginUtils.capitalize;

/**
 * Base class for the Gradle XTC Plugin in a project context.
 */
public class XtcProjectDelegate extends ProjectDelegate<Void, Void> {

    @SuppressWarnings("unused")
    public XtcProjectDelegate(final Project project) {
        this(project, null);
    }

    public XtcProjectDelegate(final Project project, final AdhocComponentWithVariants component) {
        super(project, null, component);
        // TODO: Fix the JavaTools resolution code, which is a bit hacky right now.
        //   Enable calling the Launcher from the plugin to e.g. verify if an .x file defines a module
        //     instead of relying on "top .x file level"-layout for module definitions.
    }

    private static Set<String> resolveHiddenTaskNames(final TaskContainer tasks) {
        return new HashSet<>() { {
                addAll(Set.of("jar", "classes"));
                addAll(tasks.stream().map(Task::getName).filter(name -> name.endsWith("java")).collect(Collectors.toSet()));
            }
        };
    }

    private void hideAndDisableTask(final String taskName) {
        tasks.getByName(taskName, task -> {
            // TODO: Just recreate a better lifecycle specific to XTC instead. This only adds complexity for now.
            logger.info("{} Hiding and disabling internal task: '{}' (dependencies are still maintained).", prefix, taskName);
            task.setGroup(null);
            task.setEnabled(false);
        });
    }

    private void applyJavaPlugin() {
        project.getPluginManager().apply(JavaPlugin.class);
        // At the moment we piggyback on the extended build LifeCycle provided
        // by the JavaPlugin, as well as the source sets, and other things that
        // should really be language independent in Gradle, but arent (yet).
        // However, tasks like "jar" and similar Java specific tasks, should
        // be invisible to the user, as possible, in order to create less
        // confusion. This is by no means a rare pattern in Gradle plugins,
        // but we still don't have to like it.
        //
        // TODO: Given enough spare cycles, we will probably create our own
        //   our own, fully XTC native, life cycle, with source set, and minimal
        //   changes to semantics where applicable (for example, Java resources
        //   are not compiled into the class file, and are taken from the build
        //   director/processResources task outputs. Changing this little piece
        //   to semantically conform to what XTC does should not be hard, but we
        //   haven't had the cycles to figure out why the changed build graph
        //   from doing that isn't 100% compatible with our builds.

        resolveHiddenTaskNames(tasks).forEach(this::hideAndDisableTask);
    }

    /**
     * Register a SoftwareComponent for XTC projects. We will use this like
     * components["java"] is currently used for publishing Java artifacts.
     */
    private void createXtcComponents() {
        project.getComponents().add(component);
    }

    /**
     * This method, "apply", is a delegate target call for an XTC project delegating plugin
     */
    @Override
    public Void apply(final Void args) {
        applyJavaPlugin();
        createXtcComponents();

        // Add xtc extension.
        // TODO: Later move any non-specific task flags, like "fork = <boolean>" here, and it will be applied to all tasks.
        resolveXtcExtension();

        // Ensure extensions for configuring the xtc and xec exist.
        resolveXtcCompileExtension(project);
        resolveXtcRuntimeExtension(project);

        // This is all config phase. Warn if a project isn't versioned when the XTC plugin is applied, so that we
        // are sure no skew/version conflicts exist for inter-module dependencies and cross publication.
        checkProjectIsVersioned();
        createDefaultSourceSets();
        createXtcDependencyConfigs();
        createRunTask();

        createJavaToolsConfig();
        createResolutionStrategy();
        createVersioningTasks();

        return null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " (plugin: " + getPluginUrl() + ')';
    }

    public URL getPluginUrl() {
        project.getRepositories().add(project.getRepositories().mavenCentral());
        return pluginUrl;
    }

    @SuppressWarnings("UnusedReturnValue")
    public XtcExtension resolveXtcExtension() {
        return ensureExtension(project, XTC_LANGUAGE_NAME, DefaultXtcExtension.class);
    }

    public static XtcCompilerExtension resolveXtcCompileExtension(final Project project) {
        // TODO: Separate extensions for separate tasks, or just a global xtcRun applied to all? Decide later if we are per-sourceSet. It's not necessarily better or something we need.
        return ensureExtension(project, XTC_EXTENSION_NAME_COMPILER, DefaultXtcCompilerExtension.class);
    }

    public static XtcRuntimeExtension resolveXtcRuntimeExtension(final Project project) {
        // TODO: Separate extensions for separate tasks, or just a global xtcRun applied to all? Decide later if we are per-sourceSet. It's not necessarily better or something we need.
        return ensureExtension(project, XTC_EXTENSION_NAME_RUNTIME, DefaultXtcRuntimeExtension.class);
    }

    public static SourceSet resolveSourceSet(final Project project, final String name) {
        return getSourceSets(project).getByName(name);
    }

    private static String getXtcSourceDirectoryRootPath(final SourceSet sourceSet) {
        return "src/" + sourceSet.getName() + '/' + XTC_SOURCE_SET_DIRECTORY_ROOT_NAME;
    }

    @SuppressWarnings({"SameParameterValue", "unused"})
    static String locationFor(final Class<?> clazz) {
        return clazz.getProtectionDomain().getCodeSource().getLocation().toString();
    }

    // Configuration name for incoming dependencies, like xtcModule.
    public static String incomingXtcModuleDependencies(final SourceSet sourceSet) {
        return SourceSet.isMain(sourceSet) ? XTC_CONFIG_NAME_INCOMING : XTC_CONFIG_NAME_INCOMING + capitalize(sourceSet.getName());
    }

    @SuppressWarnings("unused")
    static String outgoingXtcModules(final SourceSet sourceSet) {
        return SourceSet.isMain(sourceSet) ? XTC_CONFIG_NAME_OUTGOING : XTC_CONFIG_NAME_OUTGOING + capitalize(sourceSet.getName());
    }

    public static Provider<Directory> getXdkContentsDir(final Project project) {
        return project.getLayout().getBuildDirectory().dir("xtc/xdk/lib");
    }

    public Provider<Directory> getXdkContentsDir() {
        return getXdkContentsDir(project);
    }

    public static FileCollection getXtcSourceSetOutput(final Project project, final SourceSet sourceSet) {
        return project.getLayout().getBuildDirectory().files(XTC_LANGUAGE_NAME + '/' + sourceSet.getName() + "/lib");
    }

    public static Provider<Directory> getXtcSourceSetOutputDirectory(final Project project, final SourceSet sourceSet) {
        return project.getLayout().getBuildDirectory().dir(XTC_LANGUAGE_NAME + '/' + sourceSet.getName() + "/lib");
    }

    public static Provider<Directory> getXtcResourceOutputDirectory(final Project project, final SourceSet sourceSet) {
        return project.getLayout().getBuildDirectory().dir(XTC_LANGUAGE_NAME + '/' + sourceSet.getName() + "/resources");
    }

    public static String getCompileTaskName(final SourceSet sourceSet) {
        return sourceSet.getCompileTaskName(XTC_LANGUAGE_NAME);
    }

    public static String getRunTaskName() {
        return XTC_DEFAULT_RUN_METHOD_NAME_PREFIX + capitalize(XTC_LANGUAGE_NAME);
    }

    private static String getClassesTaskName(final SourceSet sourceSet) {
        return isMainSourceSet(sourceSet) ? "classes" : sourceSet.getName() + "Classes";
    }

    @SuppressWarnings("unused")
    private static String getProcessResourcesTaskName(final SourceSet sourceSet) {
        return isMainSourceSet(sourceSet) ? "processXtcResources" : "process" + capitalize(sourceSet.getName()) + "XtcResources";
    }

    private static boolean isMainSourceSet(final SourceSet sourceSet) {
        return MAIN_SOURCE_SET_NAME.equals(sourceSet.getName());
    }

    /**
     * Create a compile task with a source set. This subclasses a source task, and will add as source
     * the "xtc" extension of a source set, regardless of its name, e.g. sourceSets,main.xtc.
     * TODO: Resources?
     */
    private TaskProvider<XtcCompileTask> createCompileTask(final SourceSet sourceSet) {
        final var compileTaskName = getCompileTaskName(sourceSet);
        final var compileTask = tasks.register(compileTaskName, XtcCompileTask.class, project);
        final var forceRebuild = resolveXtcCompileExtension(project).getForceRebuild();
        //final var processResources = tasks.getByName(getProcessResourcesTaskName(sourceSet));

        // Always create a main and test compile task (maybe skip if source set doesn't exist)
        // Main task: tell it to operate on the main source set, test task on the test source set .
        //   Add for non main tests - also make sure it knows it should have other source set outputs as the module path input.
        compileTask.configure(task -> {
            task.setGroup(BUILD_GROUP);
            task.setDescription("Compile an XTC source set, similar to the JavaCompile task for Java.");
            task.dependsOn(XDK_EXTRACT_TASK_NAME);
            // TODO Fix more exact processResources semantics so that we use the build as resource path, and not the src. This works for first merge.
            if (forceRebuild.get()) {
                logger.warn("{} WARNING: '{}' Force rebuild is true for this compile task. Task is flagged as always stale/non-cacheable.", prefix(projectName, compileTaskName), compileTaskName);
                considerNeverUpToDate(task);
            }

            task.setSource(sourceSet.getExtensions().getByName(XTC_LANGUAGE_NAME));
            //task.doLast(t -> {
            //    // This happens during task execution, after the config phase.
            //    logger.info("{} Finished. Outputs in: {}", prefix(projectName, compileTaskName), t.getOutputs().getFiles().getAsFileTree());
            //    sourceSet.getOutput().getAsFileTree().forEach(it -> logger.info("{} compileXtc sourceSet output: {}", prefix, it));
            //});
        });

        /*
        final var processResourcesTask = tasks.register(getProcessResourcesTaskName(sourceSet), task -> {
            task.setDescription("Process resources for an XTC source set, similar to the processResources task for Java but before compile")
            if (forceRebuild.get()) {
                considerNeverUpToDate(task);
            }

            //task.doLast(t -> logger.info("{} Finished. Outputs in: {}", prefix(projectName, task.getName()), t.getOutputs().getFiles().getAsFileTree()));
        });*/

        // Find the "classes" task in the Java build life cycle that we reuse, and set the dependency correctly. This should
        // wire in process resources too, but for some reason it seems to work differently. Basically this goes to the
        // "assemble" task, but we want to reuse some of the Java life cycle internally.
        tasks.getByName(getClassesTaskName(sourceSet)).dependsOn(compileTask);


        logger.info("{} Mapping source set to compile task: {} -> {}", prefix, sourceSet.getName(), compileTaskName);
        logger.info("{} Registered and configured compile task for sourceSet: {}", prefix, sourceSet.getName());

        return compileTask;
    }

    /**
     * Create the XTC run task. If there are no explicit modules in the xtcRun config, we don't create it,
     * or we log an error or something. The run task will depend on the compile task, and make sure an XTC
     * module in the source set is compiled.
     *
     * @return the task provider of the rn task.
     */
    // TODO: Move to static factory in run task?
    private TaskProvider<XtcRunTask> createRunTask() {
        final var runTaskName = getRunTaskName();
        // The run task depends on all compile tasks, for all source sets these days.
        final var compileTaskNames = getSourceSets(project).stream().map(sourceSet -> sourceSet.getCompileTaskName(XTC_LANGUAGE_NAME));
        final var runTask = tasks.register(runTaskName, XtcRunTask.class, project);
        runTask.configure(task -> {
            task.setGroup(APPLICATION_GROUP);
            task.setDescription("Run an XTC program with a configuration supplying the module path(s).");
            task.dependsOn(XDK_EXTRACT_TASK_NAME);
            compileTaskNames.forEach(task::dependsOn); // It's important to remember to depend on compile.
            logger.info("{} Configured, dependency to tasks: {} -> {}", prefix, XDK_EXTRACT_TASK_NAME, compileTaskNames);
        });
        logger.info("{} Created task: '{}'", prefix, runTask.getName());
        return runTask;
    }

    private String getSemanticVersion() {
        final var group = project.getGroup().toString();
        final var version = project.getVersion().toString();
        if (group.isEmpty() || Project.DEFAULT_VERSION.equals(version)) {
            logger.error("{} Has not been properly versioned (group={}, version={})", prefix, group, version);
        }
        return group + ':' + projectName + ':' + version;
    }

    private void createVersioningTasks() {
        tasks.register(XDK_VERSION_TASK_NAME, task -> {
            task.setGroup(XDK_VERSION_GROUP_NAME);
            task.setDescription("Display XTC version for project, and sanity check its application.");
            task.doLast(t -> logger.info("{} XTC (version '{}'); Semantic Version: '{}'",
                prefix(projectName, XDK_VERSION_TASK_NAME), project.getVersion(), getSemanticVersion()));
        });

        tasks.register(XDK_VERSION_FILE_TASK_NAME, task -> {
            task.setGroup(XDK_VERSION_GROUP_NAME);
            task.setDescription("Generate a file containing the XDK/XTC semantic version under the build tree.");
            final var version = buildDir.file(XDK_VERSION_PATH);
            task.getOutputs().file(version);
            task.doLast(t -> {
                final var semanticVersion = getSemanticVersion();
                final var file = version.get().getAsFile();
                logger.info("{} Writing version information: '{}' to '{}'", prefix(projectName, XDK_VERSION_FILE_TASK_NAME), semanticVersion, file.getAbsolutePath());
                try {
                    Files.writeString(file.toPath(), semanticVersion + System.lineSeparator());
                } catch (final IOException e) {
                    throw buildException(e, "I/O error when writing VERSION file: '{}'.", e.getMessage());
                }
            });
        });
    }

    public static SourceSet getMainSourceSet(final Project project) {
        return getSourceSets(project).getByName(MAIN_SOURCE_SET_NAME);
    }

    public static SourceSetContainer getSourceSets(final Project project) {
        return getJavaExtensionContainer(project).getSourceSets();
    }

    private void createXtcDependencyConfigs() {
        for (final SourceSet sourceSet : getJavaExtensionContainer(project).getSourceSets()) {
            createXtcDependencyConfigs(sourceSet);
        }
        createXdkDependencyConfigs();
    }

    // Attributes for anything that consumes or produces xtc files to a source set output directory
    private void addXtcModuleAttributes(final Configuration config) {
        config.attributes(it -> {
            it.attribute(CATEGORY_ATTRIBUTE, objects.named(Category.class, LIBRARY));
            it.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, xtcModuleLibraryElementName(config)));
        });
    }

    // Attributes for anything that consumes or produces xtc files / javatools.jar from/to a directory
    private void addXdkContentsAttributes(final Configuration config) {
        config.attributes(it -> {
            it.attribute(CATEGORY_ATTRIBUTE, objects.named(Category.class, LIBRARY));
            it.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, XDK_LIBRARY_ELEMENT_TYPE_XDK_CONTENTS));
        });
    }

    private void addJavaToolsContentsAttributes(final Configuration config) {
        config.attributes(it -> {
            it.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
            it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, XDK_CONFIG_NAME_ARTIFACT_JAVATOOLS_FATJAR));
        });
    }

    private void createXtcDependencyConfigs(final SourceSet sourceSet) {
        final var compileTask = createCompileTask(sourceSet);
        //final var runAllTask = createRunTask(sourceSet, XtcRunAllTask.class);

        logger.info("{} Created compile task for sourceSet '{}' -> '{}'.", prefix, sourceSet.getName(), compileTask.getName());

        final var xtcModuleConsumerConfig = incomingXtcModuleDependencies(sourceSet);
        final var xtcModuleProducerConfig = outgoingXtcModules(sourceSet);

        @SuppressWarnings("unused") final var xtcModule = configs.register(xtcModuleConsumerConfig, config -> {
            config.setDescription("Configuration that contains location of the .xtc file created by other entities, so that they can be declared as dependencies.");
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
            addXtcModuleAttributes(config);
        });

        final var xtcModuleProvider = configs.register(xtcModuleProducerConfig, config -> {
            config.setDescription("Configuration that contains location of the .xtc files produced by this project build.");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(true);
            config.setVisible(false);
            addXtcModuleAttributes(config);
        });

        // Tell the system that the system may produce an artifact, which is the output directory for this sourceSet,
        // that will contain all xtc files built by the compile task for this source set. This makes it possible for
        // other entities to declare an xtcModule dependency, which will force the directory to be refreshed to
        // up-to-date artifacts, i.e. xtc module files generated by a compileXtc/compileXtcTest task for someone else's
        // source set.
        project.artifacts(artifactHandler -> {
            // This is already the output of the compile task.
            // But we need to declare this artifact in the xdkModuleProvider if we want someone to use the xtcModule consumer
            // and get the build directory (also forcing it to be built since it depends on the compileTask)
            final var location = getXtcSourceSetOutputDirectory(project, sourceSet);
            artifactHandler.add(xtcModuleProvider.getName(), location, artifact -> {
                logger.info("{} Adding outgoing artifact {}; builtBy {}.", prefix, location.get(), compileTask.getName());
                artifact.builtBy(compileTask);
                artifact.setType(ArtifactTypeDefinition.DIRECTORY_TYPE);
            });
        });

        // Ensure that any produced XTC module files are publishable if we publish the xtcComponent.
        // (symmetrical to e.g. jar files and the "java" component)
        // List.of(XTC_COMPONENT_VARIANT_COMPILE, XTC_COMPONENT_VARIANT_RUNTIME).forEach(v -> component.addVariantsFromConfiguration(xtcModuleProvider.get(), new JavaConfigurationVariantMapping(v, true)));
    }

    private void createXdkDependencyConfigs() {
        final var extractTask = tasks.register(XDK_EXTRACT_TASK_NAME, XtcExtractXdkTask.class, project);

        configs.register(XDK_CONFIG_NAME_JAVATOOLS_OUTGOING, it -> {
            it.setCanBeConsumed(false);
            it.setCanBeResolved(true);
            it.setDescription("The xdkJavaToolsProvider configuration is used to resolve the javatools.jar from the XDK.");
        });

        // Configuration for anyone needing a zipped artifact of the XDK, because apparently we can't have library elements.
        configs.register(XDK_CONFIG_NAME_INCOMING_ZIP, config -> {
            config.setDescription("Configuration specifying dependencies on a particular XDK distribution.");
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
        });

        // Configurations for anyone needing a zipped artifact of the XDK in the includedBuild world, because apparently the library elements are needed.
        configs.register(XDK_CONFIG_NAME_INCOMING, config -> {
            config.setDescription("Configuration specifying dependencies on a particular XDK distribution.");
            // TODO: can we keep the unpacked modules added here as well after unpack task has been run?
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
            config.attributes(it -> {
                it.attribute(CATEGORY_ATTRIBUTE, objects.named(Category.class, LIBRARY));
                it.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, XDK_LIBRARY_ELEMENT_TYPE));
            });
        });

        // This is the consumer side - a dependency for anyone needing the XDK for runtime or compile
        // time stuff. This one wants a directory with the XDK in it.  That directory is built by the
        // extractXdk task, that needs to know where the zip file is.
        //
        // By making contents consume only, and adding a dependency to the extract task from run and
        // config task, we should be able to guarantee that the configuration contains an extracted XDK.
        // We hoped to self-resolve this. This can still possibly be done by adding a resolvable
        // extension to it.
        //
        // "xdkContents" config (resolvable, someone else has created the consumable, likely the XDK build.)
        configs.register(XDK_CONFIG_NAME_CONTENTS, config -> {
            config.setDescription("Configuration that consumes the contents of an XDK, i.e. .xtc module files and javatools.jar");
            config.setCanBeResolved(false); // resolution forces someone to find and unzip an XDK for us.
            config.setCanBeConsumed(true);
            addXdkContentsAttributes(config);
        });

        project.artifacts(artifactHandler -> {
            final var location = getXdkContentsDir();
            artifactHandler.add(XDK_CONFIG_NAME_CONTENTS, location, artifact -> {
                logger.info("{} Adding outgoing XDK contents artifact to project {} ({}) builtBy {} (dir).", prefix, projectName, location.get(), extractTask.getName());
                artifact.builtBy(extractTask);
                artifact.setType(ArtifactTypeDefinition.DIRECTORY_TYPE);
            });
        });
    }

    private XtcSourceDirectorySet createXtcSourceDirectorySet(final String parentName, final String parentDisplayName) {
        final String name = parentDisplayName + '.' + XTC_LANGUAGE_NAME;
        final String displayName = parentDisplayName + ' ' + XTC_LANGUAGE_NAME + " source";
        logger.info("{} Creating XTC source directory set from (parentName: {} parentDisplayName: {}, name: {}, displayName: {})", prefix, parentName, parentDisplayName, name, displayName);

        final ObjectFactory objects = project.getObjects();
        final var xtcSourceDirectorySet = objects.newInstance(DefaultXtcSourceDirectorySet.class, objects.sourceDirectorySet(name, displayName));

        xtcSourceDirectorySet.getFilter().include("**/*" + XTC_SOURCE_FILE_EXTENSION);

        return xtcSourceDirectorySet;
    }

    private void createDefaultSourceSets() {
        for (final SourceSet sourceSet : getSourceSets(project)) {
            logger.info("{} Creating and adding XTC source directory to inherited Java source set: {}", prefix, sourceSet.getName());
            // Create a source directory set named "xtc" for this existing source set.
            final var sourceSetName = sourceSet.getName();
            // Create the xtcSourceDirectorySet
            final var xtcSourceDirectorySet = createXtcSourceDirectorySet(sourceSet.getName(), ((DefaultSourceSet)sourceSet).getDisplayName());
            // Create the source set output, so that we can add processed resources and build source code (.xtc module) locations to it.
            final SourceSetOutput output = sourceSet.getOutput();
            // Add the "xtc" source set.
            sourceSet.getExtensions().add(XtcSourceDirectorySet.class, XTC_LANGUAGE_NAME, xtcSourceDirectorySet);
            // Add the directory "src/<sourceSetName>/x" to the source set (convention)
            final var srcDir = getXtcSourceDirectoryRootPath(sourceSet);
            xtcSourceDirectorySet.srcDir(srcDir);
            // Add all sources from the xtc source directory to the sourceSet during resolution.
            sourceSet.getAllSource().source(xtcSourceDirectorySet);
            // Add output directories for modules (compile<sourceSetName>Xtc output) and resources
            // (sourceSet.output.resourcesDir) to the task, so that dependencies will work.
            final var outputModules = getXtcSourceSetOutputDirectory(project, sourceSet);
            final var outputResources = getXtcResourceOutputDirectory(project, sourceSet);
            logger.info("{} Configured sourceSets.{}.outputModules  : {}", prefix, sourceSetName, outputModules);
            logger.info("{} Configured sourceSets.{}.outputResources  : {}", prefix, sourceSetName, outputResources.get());
            output.dir(outputResources); // TODO is this really correct? We have the resource dir as a special property in the sourceSetOutput already?
            output.dir(outputModules);
            output.setResourcesDir(outputResources);
        }
    }

    private void createResolutionStrategy() {
        configs.all(config -> {
            logger.info("{} Config '{}'; evaluating dependency resolutions", prefix, config.getName());
            config.getResolutionStrategy().eachDependency(dependency -> {
                final var request = dependency.getRequested();
                logger.info("{} Config '{}'    Requests dependency (artifact: {}, moduleId: {})", prefix, config.getName(), requestToNotation(request), request.getModule());
            });
        });
    }

    // TODO: Shouldn't be really just look in our xdkJavaTools (consumer) and the XDK? (And add the XDK to the javatools consumer config?)
    private void createJavaToolsConfig() {
        // TODO: The xdk should be an xdkJavaTools provider. Declare as such in ExtractXdkTask. This should remove a large amount of version handling code.
        final var xdkJavaTools = configs.register(XDK_CONFIG_NAME_JAVATOOLS_INCOMING, config -> {
            config.setDescription("Configuration that resolves and consumes Java bridge/tool dependencies");
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
            addJavaToolsContentsAttributes(config);
        });

        logger.info("{} Created {} config and added dependencies.", prefix, xdkJavaTools.getName());
    }

    private static String xtcModuleLibraryElementName(final Configuration config) {
        return XTC_LANGUAGE_NAME + (config.getName().contains("Test") ? "-test" : "");
    }

    private static JavaPluginExtension getJavaExtensionContainer(final Project project) {
        /*
         * The Java sourceSets and the application of the Java plugin modifies the life cycle.
         * We may have to extend the compileClasspath and runtimeClasspath for the Java plugin with XTC
         * stuff to get the compilation properly hooked up, but this seems to work right now:
         */
        final var container = project.getExtensions().findByType(JavaPluginExtension.class);
        if (container == null) {
            throw XtcProjectDelegate.buildException(project.getLogger(), prefix(project), "Internal error; was expected to have a Java extension container.");
        }
        return container;
    }

    private static String requestToNotation(final ModuleVersionSelector request) {
        return String.format("%s:%s:%s", request.getGroup(), request.getName(), request.getVersion());
    }

    private void checkProjectIsVersioned() {
        if (UNSPECIFIED.equalsIgnoreCase(project.getVersion().toString())) {
            logger.lifecycle("WARNING: Project {} has unspecified version.", prefix);
        }
    }
}

package org.xvm.plugin;

import org.gradle.api.Project;
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
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;
import org.xvm.plugin.internal.DefaultXtcCompilerExtension;
import org.xvm.plugin.internal.DefaultXtcExtension;
import org.xvm.plugin.internal.DefaultXtcRuntimeExtension;
import org.xvm.plugin.internal.DefaultXtcSourceDirectorySet;
import org.xvm.plugin.tasks.XtcCompileTask;
import org.xvm.plugin.tasks.XtcExtractXdkTask;
import org.xvm.plugin.tasks.XtcRunAllTask;
import org.xvm.plugin.tasks.XtcRunTask;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.Category.LIBRARY;
import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE;
import static org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME;
import static org.xvm.plugin.Constants.EMPTY_FILE_SET;
import static org.xvm.plugin.Constants.UNSPECIFIED;
import static org.xvm.plugin.Constants.XDK_CONFIG_NAME_CONTENTS;
import static org.xvm.plugin.Constants.XDK_LIBRARY_ELEMENT_TYPE;
import static org.xvm.plugin.Constants.XDK_LIBRARY_ELEMENT_TYPE_XDK_CONTENTS;
import static org.xvm.plugin.Constants.XDK_VERSION_PATH;
import static org.xvm.plugin.Constants.XTC_COMPONENT_VARIANT_COMPILE;
import static org.xvm.plugin.Constants.XTC_COMPONENT_VARIANT_RUNTIME;
import static org.xvm.plugin.Constants.XTC_CONFIG_NAME_INCOMING;
import static org.xvm.plugin.Constants.XTC_CONFIG_NAME_INCOMING_TEST;
import static org.xvm.plugin.Constants.XTC_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xvm.plugin.Constants.XTC_CONFIG_NAME_MODULE_DEPENDENCY;
import static org.xvm.plugin.Constants.XTC_CONFIG_NAME_OUTGOING;
import static org.xvm.plugin.Constants.XTC_CONFIG_NAME_OUTGOING_TEST;
import static org.xvm.plugin.Constants.XTC_DEFAULT_RUN_METHOD_NAME_PREFIX;
import static org.xvm.plugin.Constants.XTC_EXTENSION_NAME_COMPILER;
import static org.xvm.plugin.Constants.XTC_EXTENSION_NAME_RUNTIME;
import static org.xvm.plugin.Constants.XTC_LANGUAGE_NAME;
import static org.xvm.plugin.Constants.XTC_SOURCE_FILE_EXTENSION;
import static org.xvm.plugin.Constants.XTC_SOURCE_SET_DIRECTORY_ROOT_NAME;
import static org.xvm.plugin.Constants.XTC_VERSION_FILE_TASK_NAME;
import static org.xvm.plugin.Constants.XTC_VERSION_GROUP_NAME;
import static org.xvm.plugin.Constants.XTC_VERSION_TASK_NAME;
import static org.xvm.plugin.tasks.XtcExtractXdkTask.EXTRACT_TASK_NAME;

/**
 * Base class for the Gradle XTC Plugin in a project context.
 */
public class XtcProjectDelegate extends ProjectDelegate<Void, Void> {

    // TODO add a logger mode where a more detailed output is written.

    public XtcProjectDelegate(final Project project) {
        this(project, null);
    }

    public XtcProjectDelegate(final Project project, final AdhocComponentWithVariants component) {
        super(project, component);

        // TODO: Fix the JavaTools resolution code, which is a bit hacky right now.
        //   Enable calling the Launcher from the plugin to e.g. verify if an .x file defines a module
        //     instead of relying on "top .x file level"-layout for module definitions.
    }

    /**
     * This apply method is a delegate target call for an XTC project delegating plugin
     */
    @Override
    public Void apply(final Void args) {
        project.getPluginManager().apply(JavaPlugin.class);
        project.getComponents().add(component);

        // Add xtc extension.
        // TODO: Later move any non-specific task flags, like "fork = <boolean>" here, and it will be applied to all tasks.
        xtcExtension();

        // Ensure extensions for configuring the xtc and xec exist.
        xtcCompileExtension();
        xtcRuntimeExtension();

        // This is all config phase. Warn if a project isn't versioned when the XTC plugin is applied, so that we
        // are sure no skew/version conflicts exist for inter-module dependencies and cross publication.
        checkProjectIsVersioned();
        createDefaultSourceSets();
        createXtcDependencyConfigs();

        createJavaToolsConfig();
        createResolutionStrategy();
        createVersioningTasks();

        info("{} [xtc-plugin] Finished {}::apply; Successfully applied XTC Plugin to project: '{}:{}:{}')", prefix, getClass().getSimpleName(), project.getGroup(), projectName, project.getVersion());
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
    public XtcExtension xtcExtension() {
        return ensureExtension(XTC_LANGUAGE_NAME, DefaultXtcExtension.class);
    }

    public XtcCompilerExtension xtcCompileExtension() {
        return ensureExtension(XTC_EXTENSION_NAME_COMPILER, DefaultXtcCompilerExtension.class);
    }

    public XtcRuntimeExtension xtcRuntimeExtension() {
        return ensureExtension(XTC_EXTENSION_NAME_RUNTIME, DefaultXtcRuntimeExtension.class);
    }

    private String getCompileTaskName(final SourceSet sourceSet) {
        return sourceSet.getCompileTaskName(XTC_LANGUAGE_NAME);
    }

    private String getRunTaskName(final SourceSet sourceSet, final boolean isAll) {
        final var sourceSetName = sourceSet.getName();
        final var isMain = MAIN_SOURCE_SET_NAME.equals(sourceSetName);
        final var sb = new StringBuilder();
        sb.append(XTC_DEFAULT_RUN_METHOD_NAME_PREFIX);
        if (isAll) {
            sb.append("All");
        }
        if (!isMain) {
            sb.append(capitalize(sourceSetName));
        }
        sb.append(capitalize(XTC_LANGUAGE_NAME));
        return sb.toString();
    }

    /**
     * Create the XTC run task. If there are no explicit modules in the xtcRun config, we don't create it,
     * or we log an error or something. The run task will depend on the compile task, and make sure an xtc
     * module in the source set is compiled.
     *
     * @param sourceSet the source set (typically main or test), but can be customized through the standard
     *                  mechanisms, of course.
     * @return the task provider of the rn task.
     */
    private TaskProvider<XtcRunTask> createRunTask(final SourceSet sourceSet) {
        final var runTaskName = getRunTaskName(sourceSet, false);
        final var runTask = tasks.register(runTaskName, XtcRunTask.class, this, sourceSet);
        info("{} Created run task: {}", prefix, runTask.getName());
        return runTask;
    }

    private TaskProvider<XtcRunAllTask> createRunAllTask(final SourceSet sourceSet) {
        final var runAllTaskName = getRunTaskName(sourceSet, true);
        final var runAllTask = tasks.register(runAllTaskName, XtcRunAllTask.class, this, sourceSet);
        info("{} Created runAll task: {}", prefix, runAllTask.getName());
        return runAllTask;
    }

    private TaskProvider<XtcCompileTask> createCompileTask(final SourceSet sourceSet) {
        final var compileTaskName = getCompileTaskName(sourceSet);
        final var compileTask = tasks.register(compileTaskName, XtcCompileTask.class, this, sourceSet);
        final var classes = MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()) ? "classes" : sourceSet.getName() + "Classes";
        project.getTasks().getByName(classes).dependsOn(compileTask);
        info("{} Mapping source set to compile task: {} -> {}", prefix, sourceSet.getName(), compileTaskName);
        return compileTask;
    }

    private String getSemanticVersion() {
        final var group = project.getGroup().toString();
        final var version = project.getVersion().toString();
        if (group.isEmpty() || Project.DEFAULT_VERSION.equals(version)) {
            error("{} Has not been properly versioned (group={}, version={})", prefix, group, version);
        }
        return group + ':' + projectName + ':' + version;
    }

    private void createVersioningTasks() {
        tasks.register(XTC_VERSION_TASK_NAME, task -> {
            task.setGroup(XTC_VERSION_GROUP_NAME);
            task.setDescription("Display XTC version for project, and sanity check its application.");
            task.doLast(t -> lifecycle("{} '{}' XTC Semantic Version: '{}'",
                prefix, t.getName(), project.getVersion(), getSemanticVersion()));
        });

        tasks.register(XTC_VERSION_FILE_TASK_NAME, task -> {
            task.setGroup(XTC_VERSION_GROUP_NAME);
            task.setDescription("Generate a file containing the XDK/XTC version under the build tree.");
            final var version = buildDir.file(XDK_VERSION_PATH);
            task.getOutputs().file(version);
            task.doLast(t -> {
                final var semanticVersion = getSemanticVersion();
                lifecycle("{} Writing version information: {}", prefix, semanticVersion);
                try {
                    Files.writeString(version.get().getAsFile().toPath(), semanticVersion + System.lineSeparator());
                } catch (final IOException e) {
                    throw buildException("I/O error when writing version file: '" + e.getMessage() + '\'', e);
                }
            });
        });
    }

    private SourceSetContainer getSourceSets() {
        return getJavaExtensionContainer().getSourceSets();
    }

    private void createXtcDependencyConfigs() {
        for (final SourceSet sourceSet : getJavaExtensionContainer().getSourceSets()) {
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

    private void addJarContentsAttributes(final Configuration config) {
        config.attributes(it -> {
            it.attribute(CATEGORY_ATTRIBUTE, objects.named(Category.class, LIBRARY));
            it.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LibraryElements.JAR));
        });
    }

    private void createXtcDependencyConfigs(final SourceSet sourceSet) {
        final var compileTask = createCompileTask(sourceSet);
        final var runTask = createRunTask(sourceSet);
        final var runAllTask = createRunAllTask(sourceSet);

        info("{} Created compile and run tasks for sourceSet '{}' -> '{}', '{}' and '{}'.",
                prefix, sourceSet.getName(), compileTask.getName(), runTask.getName(), runAllTask.getName());

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
            final var location = getXtcCompilerOutputDirModules(sourceSet);
            artifactHandler.add(xtcModuleProvider.getName(), location, artifact -> {
                info("{} Adding outgoing artifact {}; builtBy {}.", prefix, location.get(), compileTask.getName());
                artifact.builtBy(compileTask);
                artifact.setType(ArtifactTypeDefinition.DIRECTORY_TYPE);
            });
        });

        // Ensure that any produced XTC module files are publishable if we publish the xtcComponent.
        // (symmetrical to e.g. jar files and the "java" component)
        List.of(XTC_COMPONENT_VARIANT_COMPILE, XTC_COMPONENT_VARIANT_RUNTIME)
            .forEach(v -> component.addVariantsFromConfiguration(xtcModuleProvider.get(), new JavaConfigurationVariantMapping(v, true)));
    }

    private void createXdkDependencyConfigs() {
        final var extractTask = tasks.register(EXTRACT_TASK_NAME, XtcExtractXdkTask.class, this);

        // Configuration for anyone needing a zipped artifact of the XDK, because apparently we can't have library elements.
        configs.register("xdkZip", config ->  {
            config.setDescription("Configuration specifying dependencies on a particular XDK distribution.");
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
        });

        // Configurations for anyone needing a zipped artifact of the XDK in the includedBuild world, because apparently the library elements are needed.
        configs.register("xdk", config -> {
            config.setDescription("Configuration specifying dependencies on a particular XDK distribution.");
            // TODO: can we keep the unpacked modules added here as well after unpack task has been run?
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
            config.attributes(it -> {
                it.attribute(CATEGORY_ATTRIBUTE, objects.named(Category.class, LIBRARY));
                it.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, XDK_LIBRARY_ELEMENT_TYPE));
            });
        });

        // This is the consumer side - a dependency for anyone needing the XDK for runtime or compile time stuff.
        // This one wants a directory with the XDK in it.  That directory is built by the extractXdk task, that needs to know where the zip file is.
        //
        // By making contents consume only, and adding a dependency to the extract task from run and config task, we should be able to guarantee that
        // the configuration contains an extracted XDK. We hoped to self resolve this. This can still possibly be done by adding a resolvable
        // extension to it.
        // TODO register, not create?
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
                info("{} Adding outgoing XDK contents artifact to project {} ({}) builtBy {} (dir).", prefix, projectName, location.get(), extractTask.getName());
                artifact.builtBy(extractTask);
                artifact.setType(ArtifactTypeDefinition.DIRECTORY_TYPE);
            });
        });
    }

    private XtcSourceDirectorySet createXtcSourceDirectorySet(final String parentName, final String parentDisplayName) {
        final String name = parentDisplayName + '.' + XTC_LANGUAGE_NAME;
        final String displayName = parentDisplayName + ' ' + XTC_LANGUAGE_NAME + " source";
        info("{} Creating XTC source directory set from (parentName: {} parentDisplayName: {}, name: {}, displayName: {})",
                prefix,
                parentName,
                parentDisplayName,
                name,
                displayName);

        final ObjectFactory objects = project.getObjects();
        final var xtcSourceDirectorySet = objects.newInstance(
                DefaultXtcSourceDirectorySet.class,
                objects.sourceDirectorySet(name, displayName));

        xtcSourceDirectorySet.getFilter().include("**/*" + XTC_SOURCE_FILE_EXTENSION);

        return xtcSourceDirectorySet;
    }

    private void createDefaultSourceSets() {
        for (final SourceSet sourceSet : getSourceSets()) {
            info("{} Creating adding XTC source directory to inherited Java source set: {}", prefix, sourceSet.getName());
            // Create an "xtc" source directory set for this existing source set.
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
            final var outputModules = getXtcCompilerOutputDirModules(sourceSet);
            final var outputResources = getXtcCompilerOutputResourceDir(sourceSet);
            info("{} Configured sourceSets.{}.outputModules  : {}", prefix, sourceSetName, outputModules.get());
            info("{} Configured sourceSets.{}.outputResources  : {}", prefix, sourceSetName, outputResources.get());
            output.dir(outputResources); // TODO is this really correct? We have the resource dir as a special property in the sourceSetOutput already?
            output.dir(outputModules);
            output.setResourcesDir(outputResources);
        }
    }

    private void createResolutionStrategy() {
        configs.all(config -> {
            info("{} Config '{}'; evaluating dependency resolutions", prefix, config.getName());
            config.getResolutionStrategy().eachDependency(dependency -> {
                final var request = dependency.getRequested();
                info("{} Config '{}'    Requests dependency (artifact: {}, moduleId: {})",
                        prefix, config.getName(), requestToNotation(request), request.getModule());
            });
        });
    }

    // TODO: Shouldn't be really just look in our xtcJavaTools (consumer) and the XDK? (And add the XDK to the javatools consumer config?)
    private void createJavaToolsConfig() {
        // TODO: The xdk should be an xtcJavaTools provider. Declare as such in ExtractXdkTask. This should remove a large amount of version handling code.
        final var xtcJavaTools = configs.register(XTC_CONFIG_NAME_JAVATOOLS_INCOMING, config -> {
            config.setDescription("Configuration that resolves and consumes Java bridge/tool dependencies");
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
            addJarContentsAttributes(config);
        });

        info("{} Created {} config and added dependencies.", prefix, xtcJavaTools);
    }

    private static String xtcModuleLibraryElementName(final Configuration config) {
        return XTC_LANGUAGE_NAME + (config.getName().contains("Test") ? "-test" : "");
    }

    private JavaPluginExtension getJavaExtensionContainer() {
        /*
         * The Java sourceSets and the application of the Java plugin modifies the life cycle.
         * We may have to extend the compileClasspath and runtimeClasspath for the Java plugin with XTC
         * stuff to get the compilation properly hooked up, but this seems to work right now:
         */
        final var container = extensions.findByType(JavaPluginExtension.class);
        if (container == null) {
            throw buildException("Internal error; was expected to have a Java extension container.");
        }
        return container;
    }

    private static String requestToNotation(final ModuleVersionSelector request) {
        return String.format("%s:%s:%s", request.getGroup(), request.getName(), request.getVersion());
    }

    private void checkProjectIsVersioned() {
        if (UNSPECIFIED.equalsIgnoreCase(project.getVersion().toString())) {
            lifecycle("WARNING: Project {} has unspecified version.", prefix);
        }
    }

    String getXtcSourceDirectoryRootPath(final SourceSet sourceSet) {
        return "src/" + sourceSet.getName() + '/' + XTC_SOURCE_SET_DIRECTORY_ROOT_NAME;
    }

    @SuppressWarnings({"SameParameterValue", "unused"})
    static String locationFor(final Class<?> clazz) {
        return clazz.getProtectionDomain().getCodeSource().getLocation().toString();
    }

    public static String incomingXtcModuleDependencies(final SourceSet sourceSet) {
        return sourceSet.getName().equals(MAIN_SOURCE_SET_NAME) ? XTC_CONFIG_NAME_INCOMING : XTC_CONFIG_NAME_INCOMING_TEST;
    }

    @SuppressWarnings("unused")
    static String outgoingXtcModules(final SourceSet sourceSet) {
        return sourceSet.getName().equals(MAIN_SOURCE_SET_NAME) ? XTC_CONFIG_NAME_OUTGOING : XTC_CONFIG_NAME_OUTGOING_TEST;
    }

    public static Provider<Directory> getXdkContentsDir(final Project project) {
        return project.getLayout().getBuildDirectory().dir("xdk/common/lib");
    }

    public Provider<Directory> getXdkContentsDir() {
        return getXdkContentsDir(project);
    }

    public Provider<Directory> getXtcCompilerOutputDirModules(final SourceSet sourceSet) {
        return buildDir.dir("xdk/" + sourceSet.getName() + "/lib");
    }

    public Provider<Directory> getXtcCompilerOutputResourceDir(final SourceSet sourceSet) {
        return buildDir.dir("xdk/" + sourceSet.getName() + "/resources");
    }

    Set<File> resolveFiles(final FileCollection files) {
        return files.isEmpty() ? EMPTY_FILE_SET : files.getAsFileTree().getFiles();
    }

    Set<File> resolveDirectories(final Set<File> files) {
        return files.stream().map(this::requireFile).map(f -> requireNonNull(f.getParentFile())).collect(Collectors.toUnmodifiableSet());
    }

    private File requireFile(final File file) {
        if (file.isDirectory()) {
            throw buildException("File tree check failed; " + file.getAbsolutePath() + " is a directory.");
        }
        return file;
    }

    @SuppressWarnings("unused")
    Set<File> resolveFiles(final Provider<Directory> dirProvider) {
        return resolveFiles(project.files(dirProvider));
    }

    Set<File> resolveDirectories(final Provider<Directory> dirProvider) {
        return resolveDirectories(resolveFiles(project.files(dirProvider)));
    }

    // TODO use a builder pattern instead. Add xtcModule dependencies, XDK modules, and (for a runner), any output from the compile task in the local project.
    public Set<File> resolveModulePath(final String identifier, final FileCollection inputXtcModules) {
        info("{} Adding RESOLVED configurations from: {}", prefix, inputXtcModules.getFiles());
        final var map = new HashMap<String, Set<File>>();

        // All xtc modules and resources from our xtcModule dependencies declared in the project
        map.put(XTC_CONFIG_NAME_MODULE_DEPENDENCY, resolveFiles(inputXtcModules));
        // All contents of the XDK. We can reduce that to a directory, since we know the structure, and that it's one directory

        map.put(XDK_CONFIG_NAME_CONTENTS, resolveDirectories(getXdkContentsDir()));

        // All source set output modules. Again - it's unclear which ones we are interested in, but we can add the directories
        // to the XEC / XTC module path and let the xec/xtc sort that out.
        for (final var sourceSet : getSourceSets()) {
            final var name = capitalize(sourceSet.getName());
            final var modules = getXtcCompilerOutputDirModules(sourceSet);
            // xtcMain - Normally the only one we need to use
            // xtcMainFiles - This is used to generate runAll task contents.
            map.put("xtc" + name, resolveDirectories(modules));
        }

        map.forEach((k, v) -> info("{} '{}' Resolved files: {}", prefix, k, v));
        info("{} '{}' Resolving module path:", prefix, identifier);
        return verifyModulePath(identifier, map);
    }

    @NotNull
    private Set<File> verifyModulePath(final String identifier, final Map<String, Set<File>> map) {
        final var prefix = prefix(identifier);
        info("{} ModulePathMap: [{} keys and {} values]",
                prefix,
                map.keySet().size(),
                map.values().stream().mapToInt(Set::size).sum());

        final var modulePathList = new ArrayList<File>();
        map.forEach((provider, files) -> {
            info("{}     Module path from: '{}':", prefix, provider);
            if (files.isEmpty()) {
                info("{}         (empty)", prefix);
            }
            files.forEach(f -> info("{}         {}", prefix, f.getAbsolutePath()));

            modulePathList.addAll(files.stream().filter(f -> {
                if (f.isDirectory()) {
                    info("{} Adding directory to module path ({}).", prefix, f.getAbsolutePath());
                } else if (!isXtcBinary(f)) {
                    warn("{} Has a non .xtc module file on the module path ({}). Was this intended?", prefix, f.getAbsolutePath());
                    return false;
                }
                return true;
            }).toList());
        });

        final Set<File> modulePathSet = modulePathList.stream().collect(Collectors.toUnmodifiableSet());
        final int modulePathListSize = modulePathList.size();
        final int modulePathSetSize = modulePathSet.size();

        // Check that we don't have name collisions with the same dependency declared in several places.
        if (modulePathListSize != modulePathSetSize) {
            warn("{} There are {} duplicated modules on the full module path.", prefix, modulePathListSize - modulePathSetSize);
        }

        checkDuplicatesInModulePaths(modulePathSet);

        // Check that all modules on path are XTC files.
        info("{} Final module path: {}", prefix, modulePathSet);
        return modulePathSet;
    }

    private void checkDuplicatesInModulePaths(final Set<File> modulePathSet) {
        for (final File module : modulePathSet) {
            // find modules with the same name (or TODO: with the same identity)
            if (module.isDirectory()) {
                // TODO, sanity check directories later. The only cause of concern are identical ones, and that is not fatal, but may merit a warning.
                //  The Set data structure already takes care of silently removing them, however.
                continue;
            }
            final List<File> dupes = modulePathSet.stream().filter(File::isFile).filter(f -> f.getName().equals(module.getName())).toList();
            assert (!dupes.isEmpty());
            if (dupes.size() != 1) {
                final String msg = "ERROR: a dependency with the same name is defined in " +
                        dupes.size() +
                        " locations on the module path: " +
                        dupes;
                throw buildException(msg);
            }
        }
    }
}

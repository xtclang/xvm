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
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecException;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static java.util.Objects.requireNonNull;
import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.Category.LIBRARY;
import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE;
import static org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME;
import static org.xvm.plugin.Constants.EMPTY_FILE_SET;
import static org.xvm.plugin.Constants.JAR_MANIFEST_PATH;
import static org.xvm.plugin.Constants.JAVATOOLS_ARTIFACT_ID;
import static org.xvm.plugin.Constants.LOAD_ON_DEMAND_FILENAMES;
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
import static org.xvm.plugin.Constants.XTC_MODULE_FILE_EXTENSION;
import static org.xvm.plugin.Constants.XTC_SOURCE_FILE_EXTENSION;
import static org.xvm.plugin.Constants.XTC_SOURCE_SET_DIRECTORY_ROOT_NAME;
import static org.xvm.plugin.Constants.XTC_VERSIONFILE_TASK_NAME;
import static org.xvm.plugin.Constants.XTC_VERSION_GROUP_NAME;
import static org.xvm.plugin.Constants.XTC_VERSION_TASK_NAME;
import static org.xvm.plugin.XtcExtractXdkTask.EXTRACT_TASK_NAME;

/**
 * Base class for the Gradle XTC Plugin in a project context.
 */
public class XtcProjectDelegate extends ProjectDelegate {

    private static final boolean ALLOW_LOAD_ON_DEMAND = Boolean.getBoolean("org.xvm.plugin.allowLoadOnDemand");

    private final StateListeners listeners;
    private final Set<String> loadOnDemand;
    private final OnDemandLibraryLoader onDemandLoader;

    public XtcProjectDelegate(final Project project, final AdhocComponentWithVariants component) {
        super(project, component);
        this.onDemandLoader = new OnDemandLibraryLoader(this);
        this.listeners = new StateListeners(project);
        // TODO: Fix the JavaTools resolution code, which is a big hacky right now.
        // TODO: After that, enable dynamic javatools.jar loading at runtime, making possible:
        //  1) To avoid the JVM fork for xtcCompile and xtcRun, which should speed up build time.
        //  2) To enable calling the Launcher from the plugin to e.g. verify if an .x file defines a module
        //     instead of relying on "top .x file level"-layout for module definitions.
        this.loadOnDemand = ALLOW_LOAD_ON_DEMAND ? LOAD_ON_DEMAND_FILENAMES : Set.of();
    }

    /**
     * This apply method is a delegate target call for an XTC project delegating plugin
     */
    @Override
    public void apply() {
        listeners.apply();
        project.getPluginManager().apply(JavaPlugin.class); // TODO: Enough with base plugin?
        project.getComponents().add(component);

        // Ensure extensions for configuring the xtc and xec exist.
        xtcCompileExtension();

        // TODO: modules dsl for runner
        xtcRuntimeExtension();

        // This is all config phase. Warn if a project isn't versioned when the XTC plugin is applied, so that we
        // are sure no skew/version conflicts exist for inter-module dependencies and cross publication.
        checkProjectIsVersioned();
        createDefaultSourceSets();
        createXtcDependencyConfigs();

        createJavaToolsConfig();
        createResolutionStrategy();
        createVersioningTasks();

        lifecycle("{} Finished {}::apply; Successfully applied XTC Plugin to project (version: '{}:{}:{}')", prefix, getClass().getSimpleName(), project.getGroup(), project.getName(), project.getVersion());
    }

    public URL getPluginUrl() {
        return pluginUrl;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " (plugin: " + getPluginUrl() + ')';
    }

    @SuppressWarnings("unused")
    StateListeners getListeners() {
        return listeners;
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
        final var classes = "main".equals(sourceSet.getName()) ? "classes" : sourceSet.getName() + "Classes";
        project.getTasks().getByName(classes).dependsOn(compileTask);
        info("{} Mapping source set to compile task: {} -> {}", prefix, sourceSet.getName(), compileTaskName);
        return compileTask;
    }

    private String getSemanticVersion() {
        return project.getGroup().toString() + ':' + project.getName() + ':' + project.getVersion();
    }

    private void createVersioningTasks() {
        tasks.register(XTC_VERSION_TASK_NAME, task -> {
            task.setGroup(XTC_VERSION_GROUP_NAME);
            task.setDescription("Display XTC version for plugin, and sanity check its application.");
            task.doLast(t -> lifecycle("{} '{}' XTC Semantic Version: '{}'",
                prefix, t.getName(), project.getVersion(), getSemanticVersion()));
        });

        tasks.register(XTC_VERSIONFILE_TASK_NAME, task -> {
            task.setGroup(XTC_VERSION_GROUP_NAME);
            task.setDescription("Generate a file containing the XDK/XTC version under the build tree.");
            final var version = buildDir.dir(XDK_VERSION_PATH.toLowerCase());
            task.getOutputs().dir(version);
            task.doLast(t -> {
                final var semanticVersion = getSemanticVersion();
                lifecycle("{} Writing version information: {}", prefix, semanticVersion);
                try {
                    final File dest = version.get().getAsFile();
                    if (dest.exists() && dest.isDirectory() || dest.mkdirs()) {
                        Files.writeString(version.get().file(XDK_VERSION_PATH).getAsFile().toPath(), semanticVersion);
                        return;
                    }
                    throw buildException("Failed to access and/or create destination directory: " + dest.getAbsolutePath());
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
                info("Adding outgoing XDK contents artifact to project {} {} builtBy {} (dir).", prefix, location.get(), extractTask.getName());
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
            output.dir(outputResources); // TODO is this really correct? We have the resource dir as a special property in the ourceSetOutput already?
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
         * stuff to get the compilation properly hooked up.
         *
         *   Tasks:
         *      processResources
         *      processTestResources
         *      clean
         *      clean<TaskName>
         *      compileJava <- tasks that contribute to compilation classpath, including jars on classpath via project eps>
         *      classes <- compileJava, processResources
         *      compileTestJava <- classes, tasks that contribute to testClassPath
         *      testClasses <- compileTestJava, processTestResources
         *      test <- testClasses, and all tasks that produce the runtime classpath
         *
         *   SourceSet Tasks:
         *      compile<SourceSet>Java <- all tasks that contribute to compilation classpath
         *      process<SourceSet>Resources
         *      <sourceSet>classes <- compileSourceSetJava, processSourceSetResources
         *
         *   Lifecycle Tasks:
         *      assemble <- jar
         *      check <- test (aggregate platform verification tasks)
         *      build <- check, assemble
         *      buildNeeded <- build and buildNeeded in projects that are testRuntimeClasspath dependencies
         *      buildDependents <- build and buildDependent tasks in all project that have this project ad a dependency in their testRuntimeClasspath
         *      build<ConfigName> - task rule, depends on all tasks that generate artifacts attached to the named <ConfigName> configuration
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
            project.getLogger().lifecycle("WARNING: project {} has unspecified version.", prefix);
        }
    }

    String getXtcSourceDirectoryRootPath(final SourceSet sourceSet) {
        return "src/" + sourceSet.getName() + '/' + XTC_SOURCE_SET_DIRECTORY_ROOT_NAME;
    }

    @SuppressWarnings({"SameParameterValue", "unused"})
    static String locationFor(final Class<?> clazz) {
        return clazz.getProtectionDomain().getCodeSource().getLocation().toString();
    }

    static String incomingXtcModuleDependencies(final SourceSet sourceSet) {
        return sourceSet.getName().equals(MAIN_SOURCE_SET_NAME) ? XTC_CONFIG_NAME_INCOMING : XTC_CONFIG_NAME_INCOMING_TEST;
    }

    @SuppressWarnings("unused")
    static String outgoingXtcModules(final SourceSet sourceSet) {
        return sourceSet.getName().equals(MAIN_SOURCE_SET_NAME) ? XTC_CONFIG_NAME_OUTGOING : XTC_CONFIG_NAME_OUTGOING_TEST;
    }

    @SuppressWarnings("UnusedReturnValue")
    ExecResult execLauncher(final String identifier, final String mainClassName, final CommandLine args, final List<String> jvmArgs) {
        // We could actually get away with resolving javatools this late, even if we have never heard of it.
        // This should be inside an executing task.

        final var javaToolsJar = resolveJavaTools(); //project.resolvedJavaTools(xtcModulePath);
        if (javaToolsJar == null) {
            throw buildException("Failed to resolve javatools.jar in any classpath.");
        }
        info("{} '{}' {}; Using javatools.jar in classpath from: {}", prefix, identifier, mainClassName, javaToolsJar.getAbsolutePath());

        //warn("TODO: We should be able to redirect task output to some default place.");
        // TODO: Make an execlauncher task.

        final var sb = new StringBuilder("java");
        jvmArgs.forEach(arg -> sb.append(' ').append(arg));
        sb.append(" -cp ").append(javaToolsJar.getAbsolutePath());
        sb.append(' ').append(mainClassName);
        args.toList().forEach(arg -> sb.append(' ').append(arg));
        lifecycle("{} {} exec: '{}'", prefix, identifier, sb.toString());

        // TODO, avoid fork by dynamically loading javatools.jar into this classpath and using reflection.
        final var result = project.getProject().javaexec(spec -> {
            spec.classpath(javaToolsJar);
            spec.getMainClass().set(mainClassName);
            spec.args(args.toList());
            spec.jvmArgs(jvmArgs);
        });

        final var exitValue = result.getExitValue();
        info("{} '{}' JavaExec return value: {}", prefix, mainClassName, exitValue);
        try {
            return result.rethrowFailure();
        } catch (final ExecException e) {
            throw buildException("Error running XTC Launcher (result: " + result + ')', e);
        }
    }

    static boolean hasFileExtension(final File file, final String extension) {
        return getFileExtension(file).equalsIgnoreCase(extension);
    }

    static String getFileExtension(final File file) {
        final String name = file.getName();
        final int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot + 1);
    }

    private boolean isJavaToolsJar(final File file) {
        final boolean ok = "jar".equalsIgnoreCase(getFileExtension(file)) &&
                file.getName().startsWith(JAVATOOLS_ARTIFACT_ID) &&
                readXdkVersionFromJar(file) != null;
        info("{} isJavaToolsJar({}) = {}", prefix, file.getAbsolutePath(), ok);
        return ok;
    }

    boolean isXtcBinary(final File file) {
        return isXtcBinary(file, true);
    }

    @SuppressWarnings("SameParameterValue")
    boolean isXtcBinary(final File file, final boolean checkMagic) {
        if (!file.exists() || !file.isFile() || !hasFileExtension(file, XTC_MODULE_FILE_EXTENSION)) {
            return false;
        }
        if (!checkMagic) {
            return true;
        }
        try (final var dis = new DataInputStream(new FileInputStream(file))) {
            final long magic = dis.readInt() & 0xffff_ffffL;
            if (magic != Constants.XTC_MAGIC) {
                error("{} File '{}' should have started with magic value 0x{} (read: 0x{})", prefix, file.getAbsolutePath(), Long.toHexString(Constants.XTC_MAGIC), Long.toHexString(magic));
            }
            return true;
        } catch (final IOException e) {
            error("{} Error parsing XTC_MAGIC: {}", prefix, e.getMessage());
            return false;
        }
    }

    Provider<Directory> getXdkContentsDir() {
        return buildDir.dir("xdk/common/lib");
    }

    Provider<Directory> getXtcCompilerOutputDirModules(final SourceSet sourceSet) {
        return buildDir.dir("xdk/" + sourceSet.getName() + "/lib");
    }

    Provider<Directory> getXtcCompilerOutputResourceDir(final SourceSet sourceSet) {
        return buildDir.dir("xdk/" + sourceSet.getName() + "/resources");
    }

    FileCollection filesFrom(final String... configNames) {
        return filesFrom(false, configNames);
    }

    FileCollection filesFrom(final boolean shouldBeResolved, final String... configNames) {
        info("{} Resolving filesFrom config: {}", prefix, Arrays.asList(configNames));
        assert shouldBeResolved || !listeners.isConfigurationPhaseFinished();
        FileCollection fc = objects.fileCollection();
        for (final var name : configNames) {
            final Configuration config = configs.getByName(name);
            final var files = project.files(config);
            info("{} Scanning file collection: filesFrom: {} {}, files: {}", prefix, name, config.getState(), files.getFiles());
            fc = fc.plus(files);
        }
        fc.getAsFileTree().forEach(it -> info("{}  RESOLVED fileTree '{}'", prefix, it.getAbsolutePath()));
        return fc;
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
    Set<File> resolveModulePath(final String identifier, final FileCollection inputXtcModules) {
        info("{} Adding RESOLVED configurations from: {}", prefix, inputXtcModules.getFiles());
        final var map = new HashMap<String, Set<File>>();

        // All xtc modules and resources from our xtcModule dependencies declared in the project
        map.put(XTC_CONFIG_NAME_MODULE_DEPENDENCY, resolveFiles(inputXtcModules));
        // All contents of the XDK. We can reduce that to a directory, since we know the structure, and that it's one directory

        map.put("xdkContents", resolveDirectories(getXdkContentsDir()));
        //map.put("xdkContentFiles", resolveFiles(getXdkContentsDir()));

        // All source set output modules. Again - it's unclear which ones we are interested in, but we can add the directories
        // to the XEC / XTC module path and let the xec/xtc sort that out.
        for (final var sourceSet : getSourceSets()) {
            // TODO: Temporarily, put just the directories in there for locally compiled modules. Several may be executable, which is ambig
            //    map.put(XTC_LANGUAGE_NAME + capitalize(sourceSet.getName()), resolveFiles(getXtcCompilerOutputDirModules(sourceSet)));
            final var name = capitalize(sourceSet.getName());
            final var modules = getXtcCompilerOutputDirModules(sourceSet);
            // xtcMain - Normally the only one we need to use
            // xtcMainFiles - This is used to generate runAll task contents.
            map.put("xtc" + name, resolveDirectories(modules));
            //map.put("xtc" + name + "Files", resolveFiles(modules));
        }

        info("{} '{}' Resolving module path:", prefix, identifier);
        return verifyModulePath(identifier, map);
    }

    @NotNull
    private Set<File> verifyModulePath(final String identifier, final Map<String, Set<File>> map) {
        final var prefix = prefix(identifier);
        info("{} ModulePathMap = {}", prefix, map);

        final var modulePathList = new ArrayList<File>();
        map.forEach((provider, files) -> {
            info("{}     module path from: '{}':", prefix, provider);
            if (files.isEmpty()) {
                info("{}         (empty)", prefix);
            }
            files.forEach(f -> info("{}         {}", prefix, f.getAbsolutePath()));

            modulePathList.addAll(files.stream().filter(f -> {
                if (f.isDirectory()) {
                    info("{} Adding directory to module path ({}).", prefix, f.getAbsolutePath());
                    return true;
                }
                final boolean isValidXtcModule = isXtcBinary(f);
                if (!isValidXtcModule) {
                    warn("{} Has a non .xtc module file on the module path ({}). Was this intended?", prefix, f.getAbsolutePath());
                }
                return isValidXtcModule;
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

    private boolean isJarFile(final File file) {
        try (final ZipFile zip = new ZipFile(file)) {
            return zip.getEntry(JAR_MANIFEST_PATH) != null;
        } catch (final IOException e) {
            throw buildException("Failed to read jar file: " + file.getAbsolutePath() + " (is the format correct?)");
        }
    }

    private boolean shouldLoadOnDemand(final File file) {
        return loadOnDemand.contains(file.getName());
    }

    // TODO: Move the resolution into the task hierarchy, perhaps? The javatools is something resolved by the run task, really.
    private File processJar(final File file) {
        assert file.exists();
        if (isJarFile(file) && shouldLoadOnDemand(file)) {
            info("{} Adding JavaTools '{}' to classpath", prefix, file.getAbsolutePath());
            onDemandLoader.addToClasspath(file);
            try {
                final Class<?> clazz = Class.forName("org.xvm.tool.Launcher");
                lifecycle("{} Loaded class dynamically at runtime: {}", prefix, clazz.getName());
            } catch (final ClassNotFoundException e) {
                throw buildException("Exception during dynamic class loading", e);
            }
        }
        return file;
    }

    // TODO: This is very kludgy, we should really just make sure that there is an xtcJavaTools resolvable config
    //  in every project, that resolves to a present javatools.jar, in the case of XDK dependencies, to the one
    //  in the XDK build folder. Otherwise, i.e. when working in the XDK, there should be an included build for
    //  the Javatools project, and everyone who wants to use the javatools from there. It is not Gradle best
    //  practice to add the javatools dependency to the project in the plugin, and considered bad form. But simplifying
    //  this and making sure everything works, will come after the initial merge of the XDK plugin
    //
    //  as for time being it works.
    private File resolveJavaTools() {
        assert listeners.isConfigurationPhaseFinished();
        if (!listeners.isConfigurationPhaseFinished()) {
            throw buildException("Internal error; resolveJavaTools() called before configuration phase is finished.");
        }

        // TODO: Way too complicated.
        final var javaToolsFromConfig =
                filesFrom(true, XTC_CONFIG_NAME_JAVATOOLS_INCOMING).filter(this::isJavaToolsJar);
        final var javaToolsFromXdk =
                project.getProject().fileTree(getXdkContentsDir()).filter(this::isJavaToolsJar);

        final File resolvedFromConfig = javaToolsFromConfig.isEmpty() ? null : javaToolsFromConfig.getSingleFile();
        final File resolvedFromXdk = javaToolsFromXdk.isEmpty() ? null : javaToolsFromXdk.getSingleFile();
        if (resolvedFromConfig == null && resolvedFromXdk == null) {
            throw buildException("ERROR: Failed to receive javatools.jar from any configuration or dependency.");
        }

        info("""
            {} Check for javatools.jar in {} config and XDK dependency, if present.
            {}     Resolved to: [xtcJavaTools: {}, xdkContents: {}]
            """.trim(),
            prefix, XTC_CONFIG_NAME_JAVATOOLS_INCOMING,
            prefix, resolvedFromConfig, resolvedFromXdk);

        final String versionConfig = readXdkVersionFromJar(resolvedFromConfig);
        final String versionXdk = readXdkVersionFromJar(resolvedFromXdk);
        if (resolvedFromConfig != null && resolvedFromXdk != null) {
            if (!versionConfig.equals(versionXdk) || !identical(resolvedFromConfig, resolvedFromXdk)) {
                warn("{} Different javatools in resolved files, preferring the non-XDK version: {}", prefix, resolvedFromConfig.getAbsolutePath());
                return processJar(resolvedFromConfig);
            }
        }

        if (resolvedFromConfig != null) {
            assert resolvedFromXdk == null;
            info("{} Resolved unique javatools.jar from config/artifacts/dependencies: {} (version: {})", prefix, resolvedFromConfig.getAbsolutePath(), versionConfig);
            return processJar(resolvedFromConfig);
        }

        info("{} Resolved unique javatools.jar from XDK: {} (version: {})", prefix, resolvedFromXdk.getAbsolutePath(), versionXdk);
        return processJar(resolvedFromXdk);
    }

    private String readXdkVersionFromJar(final File jar) {
        if (jar == null) {
            return null;
        }
        try (final var jis = new JarInputStream(new FileInputStream(jar))) {
            final Manifest mf = jis.getManifest();
            final Object mainClass = mf.getMainAttributes().get(Attributes.Name.MAIN_CLASS);
            final Object implVersion = mf.getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION);
            if (mainClass == null || implVersion == null) {
                return null;
            }
            info("{} Detected valid javatools.jar: {} (XTC Manifest Version: {})", prefix, jar.getAbsolutePath(), implVersion);
            return implVersion.toString();
        } catch (final IOException e) {
            error("{} Expected " + jar.getAbsolutePath() + " to be a jar file, with a readable manifest: {}", prefix, e.getMessage());
            return null;
        }
    }

    private boolean identical(final File f1, final File f2) {
        try {
            final long mismatch = Files.mismatch(requireNonNull(f1).toPath(), requireNonNull(f2).toPath());
            if (mismatch == -1L) {
                return true;
            }
            warn("{} resolves multiple javatools.jar that are different: ({} != {}, mismatch at byte: {})", prefix, f1.getAbsolutePath(), f2.getAbsolutePath(), mismatch);
            final long l1 = f1.length();
            final long l2 = f2.length();
            if (l1 != l2) {
                warn("{}   {} bytes != {} bytes", prefix, f1.length(), f2.length());
            }
            final long lm1 = f1.lastModified();
            final long lm2 = f2.lastModified();
            if (lm1 != lm2) {
                warn("{}   {} lastModified != {} lastModified?", prefix, f1.lastModified(), f2.lastModified());
            }
            return false;
        } catch (final IOException e) {
            throw buildException(e.getMessage(), e);
        }
    }

    private static String capitalize(final String string) {
        return Character.toUpperCase(string.charAt(0)) + string.substring(1);
    }

    public XtcCompilerExtension xtcCompileExtension() {
        return ensureExtension(XTC_EXTENSION_NAME_COMPILER, DefaultXtcCompilerExtension.class);
    }

    public XtcRuntimeExtension xtcRuntimeExtension() {
        return ensureExtension(XTC_EXTENSION_NAME_RUNTIME, DefaultXtcRuntimeExtension.class);
    }
}

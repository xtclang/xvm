package org.xtclang.plugin.tasks;

import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.launchers.BuildThreadLauncher;
import org.xtclang.plugin.launchers.JavaExecLauncher;
import org.xtclang.plugin.launchers.NativeBinaryLauncher;
import org.xtclang.plugin.launchers.XtcLauncher;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.xtclang.plugin.XtcPluginConstants.EMPTY_FILE_COLLECTION;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_CONTENTS;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcPluginConstants.XTC_CONFIG_NAME_MODULE_DEPENDENCY;
import static org.xtclang.plugin.XtcPluginConstants.XTC_LANGUAGE_NAME;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.isValidXtcModule;
import static org.xtclang.plugin.XtcPluginUtils.argumentArrayToList;
import static org.xtclang.plugin.XtcPluginUtils.capitalize;
import static org.xtclang.plugin.XtcPluginUtils.singleArgumentIterableProvider;
import static org.xtclang.plugin.XtcProjectDelegate.incomingXtcModuleDependencies;

/**
 * Abstract class that represents and XTC Launcher execution (i.e. Compiler, Runner, Disassembler etc.),
 * anything that goes through the XTC Launcher to spawn or call different processes
 */
public abstract class XtcLauncherTask<E extends XtcLauncherTaskExtension> extends XtcDefaultTask implements XtcLauncherTaskExtension {
    protected final SourceSet sourceSet;

    // All inherited from launcher task extension and turned into input
    protected final Property<InputStream> stdin;
    protected final Property<OutputStream> stdout;
    protected final Property<OutputStream> stderr;
    protected final ListProperty<String> jvmArgs;
    protected final Property<Boolean> isVerbose;
    protected final Property<Boolean> isFork;
    protected final Property<Boolean> showVersion;
    protected final Property<Boolean> useNativeLauncher;

    protected final E ext;

    protected XtcLauncherTask(final XtcProjectDelegate delegate, final String taskName, final SourceSet sourceSet, final E ext) {
        super(delegate, taskName);
        this.sourceSet = sourceSet;
        this.ext = ext;
        this.stdin = objects.property(InputStream.class);
        this.stdout = objects.property(OutputStream.class);
        this.stderr = objects.property(OutputStream.class);
        if (ext.getStdin().isPresent()) {
            stdin.set(ext.getStdin());
        }
        if (ext.getStdout().isPresent()) {
            stdout.set(ext.getStdout());
        }
        if (ext.getStderr().isPresent()) {
            stderr.set(ext.getStderr()); // TODO maybe rename the properties to standardOutput, errorOutput etc to conform to Gradle name standard. Right now we clearly want them to be separated from any defaults, though, so we know our launcher tasks pick the correct configured streams.
        }
        this.jvmArgs = objects.listProperty(String.class).convention(ext.getJvmArgs());
        this.isVerbose = objects.property(Boolean.class).convention(ext.getVerbose());
        this.isFork = objects.property(Boolean.class).convention(ext.getFork());
        this.showVersion = objects.property(Boolean.class).convention(ext.getShowVersion());
        this.useNativeLauncher = objects.property(Boolean.class).convention(ext.getUseNativeLauncher());
    }

    @Internal
    protected E getExtension() {
        return ext;
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getInputDeclaredDependencyModules() {
        return delegate.filesFrom(incomingXtcModuleDependencies(sourceSet)); // xtcModule and xtcModuleTest dependencies declared in the project dependency { scope section
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getInputXtcJavaToolsConfig() {
        return project.files(project.getConfigurations().getByName(XDK_CONFIG_NAME_JAVATOOLS_INCOMING));
    }

    public boolean hasStdinRedirect() {
        return stdin.isPresent();
    }

    public boolean hasStdoutRedirect() {
        return stdout.isPresent();
    }

    public boolean hasStderrRedirect() {
        return stderr.isPresent();
    }

    @SuppressWarnings("unused")
    public boolean hasOutputRedirects() {
        return hasStdoutRedirect() || hasStderrRedirect();
    }

    @Optional
    @Input
    public Property<InputStream> getStdin() {
        return stdin;
    }

    @Optional
    @Input
    public Property<OutputStream> getStdout() {
        return stdout;
    }

    @Optional
    @Input
    public Property<OutputStream> getStderr() {
        return stderr;
    }

    @Override
    public void jvmArg(final Provider<? extends String> arg) {
        jvmArgs(singleArgumentIterableProvider(project, arg));
    }

    @Override
    public void jvmArgs(final String... args) {
        jvmArgs.addAll(argumentArrayToList(args));
    }

    @Override
    public void jvmArgs(final Iterable<? extends String> args) {
        jvmArgs.addAll(args);
    }

    @Override
    public void jvmArgs(final Provider<? extends Iterable<? extends String>> provider) {
        jvmArgs.addAll(provider);
    }

    @Override
    public void setJvmArgs(final Iterable<? extends String> elements) {
        jvmArgs.set(elements);
    }

    @Override
    public void setJvmArgs(final Provider<? extends Iterable<? extends String>> provider) {
        jvmArgs.set(provider);
    }

    @Input
    public Property<Boolean> getVerbose() {
        return isVerbose;
    }

    @Input
    public Property<Boolean> getFork() {
        return isFork;
    }

    @Input
    public Property<Boolean> getShowVersion() {
        return showVersion;
    }

    @Input
    public Property<Boolean> getUseNativeLauncher() {
        return useNativeLauncher;
    }

    @Optional
    @Input
    public ListProperty<String> getJvmArgs() {
        return jvmArgs;
    }

    @Input
    public Property<Boolean> getIsVerbose() {
        return isVerbose;
    }

    @Internal
    public abstract String getJavaLauncherClassName();

    @Internal
    public abstract String getNativeLauncherCommandName();

    protected ExecResult handleExecResult(final ExecResult result) {
        final int exitValue = result.getExitValue();
        if (exitValue != 0) {
            getLogger().error("{} terminated abnormally (exitValue: {}). Rethrowing exception.", prefix, exitValue);
        }
        result.rethrowFailure();
        result.assertNormalExitValue();
        return result;
    }

    protected XtcLauncher<E, ? extends XtcLauncherTask<E>> createLauncher() {
        if (getUseNativeLauncher().get()) {
            logger.info("{} Task '{}' created XTC launcher: native executable.", prefix, taskName);
            return new NativeBinaryLauncher<>(project, this);
        } else if (getFork().get()) {
            logger.info("{} Task '{}' created XTC launcher: Java process forked from build.", prefix, taskName);
            return new JavaExecLauncher<>(project, this);
        } else {
            logger.warn("{} WARNING: Task '{}' created XTC launcher: Running launcher in the same thread as the build process. This is not recommended for production use.", prefix, taskName);
            return new BuildThreadLauncher<>(project, this);
        }
    }

    protected Set<File> resolveModulePath(final FileCollection inputXtcModules) {
        return resolveModulePath(inputXtcModules, false);
        //final var modulePathFiles = resolveModulePath(inputXtcModules, false);
        //final var modulePathFilesAllSource = resolveModulePath(inputXtcModules, true);
        //System.err.println(" ** " + modulePathFiles);
        //System.err.println(" ** " + modulePathFilesAllSource);
        //assert modulePathFiles.equals(modulePathFilesAllSource) : "An XTC Task should never resolve all project source sets differently than its own source set.";
        //return modulePathFilesAllSource;
    }

    private Set<File> resolveModulePath(final FileCollection inputXtcModules, final boolean includeAllSourceSets) {
        logger.info("{} Adding RESOLVED configurations from: {}", prefix, inputXtcModules.getFiles());
        final var map = new HashMap<String, Set<File>>();

        // All xtc modules and resources from our xtcModule dependencies declared in the project
        map.put(XTC_CONFIG_NAME_MODULE_DEPENDENCY, resolveFiles(inputXtcModules));

        // All contents of the XDK. We can reduce that to a directory, since we know the structure, and that it's one directory
        map.put(XDK_CONFIG_NAME_CONTENTS, resolveDirectories(delegate.getXdkContentsDir()));

        // TODO: It's probably always enough to traverse the per-task sourceSet, and will save time.
        //   The option to iterate over all source sets is still there for completeness, but will most
        //   likely go away. ATM we just want to merge a working Gradle XTC Plugin that handles dependencies
        //   in a well-tested manner, though.
        final List<SourceSet> sourceSets = includeAllSourceSets ? List.copyOf(delegate.getSourceSets()) : List.of(sourceSet);
        for (final var sourceSet : sourceSets) {
            final var name = capitalize(sourceSet.getName());
            final var modules = delegate.getXtcCompilerOutputDirModules(sourceSet);
            // xtcMain - Normally the only one we need to use
            // xtcMainFiles - This is used to generate runAll task contents.
            map.put(XTC_LANGUAGE_NAME + name, resolveDirectories(modules));
        }

        map.forEach((k, v) -> logger.info("{} Resolved files: {} -> {}", prefix, k, v));
        logger.info("{} Resolving module path:", prefix);
        return verifyModulePath(map);
    }

    private Set<File> verifyModulePath(final Map<String, Set<File>> map) {
        logger.info("{} ModulePathMap: [{} keys and {} values]", prefix, map.keySet().size(), map.values().stream().mapToInt(Set::size).sum());

        final var modulePathList = new ArrayList<File>();
        map.forEach((provider, files) -> {
            logger.info("{}     Module path from: '{}':", prefix, provider);
            if (files.isEmpty()) {
                logger.info("{}         (empty)", prefix);
            }
            files.forEach(f -> logger.info("{}         {}", prefix, f.getAbsolutePath()));

            modulePathList.addAll(files.stream().filter(f -> {
                if (f.isDirectory()) {
                    logger.info("{} Adding directory to module path ({}).", prefix, f.getAbsolutePath());
                } else if (!isValidXtcModule(f)) {
                    logger.warn("{} Has a non .xtc module file on the module path ({}). Was this intended?", prefix, f.getAbsolutePath());
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
            logger.warn("{} There are {} duplicated modules on the full module path.", prefix, modulePathListSize - modulePathSetSize);
        }

        checkDuplicatesInModulePaths(modulePathSet);

        // Check that all modules on path are XTC files.
        logger.info("{} Final module path: {}", prefix, modulePathSet);
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
                throw buildException("A dependency with the same name is defined in more than one ({}) location on the module path.", dupes.size());
            }
        }
    }

    public static Set<File> resolveFiles(final FileCollection files) {
        return files.isEmpty() ? EMPTY_FILE_COLLECTION : files.getAsFileTree().getFiles();
    }

    public static Set<File> resolveDirectories(final Set<File> files) {
        return files.stream().map(f -> requireNonNull(f.getParentFile())).collect(Collectors.toUnmodifiableSet());
    }

    @SuppressWarnings("unused")
    protected Set<File> resolveFiles(final Provider<Directory> dirProvider) {
        return resolveFiles(project.files(dirProvider));
    }

    protected Set<File> resolveDirectories(final Provider<Directory> dirProvider) {
        return resolveDirectories(resolveFiles(project.files(dirProvider)));
    }
}

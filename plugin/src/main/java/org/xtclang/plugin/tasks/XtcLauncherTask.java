package org.xtclang.plugin.tasks;

import static java.util.Objects.requireNonNull;

import static org.xtclang.plugin.XtcPluginConstants.EMPTY_FILE_COLLECTION;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_CONTENTS;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcPluginConstants.XTC_CONFIG_NAME_MODULE_DEPENDENCY;
import static org.xtclang.plugin.XtcPluginConstants.XTC_LANGUAGE_NAME;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.isValidXtcModule;
import static org.xtclang.plugin.XtcPluginUtils.argumentArrayToList;
import static org.xtclang.plugin.XtcPluginUtils.singleArgumentIterableProvider;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
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

/**
 * Abstract class that represents and XTC Launcher execution (i.e. Compiler, Runner, Disassembler etc.),
 * anything that goes through the XTC Launcher to spawn or call different processes
 */
public abstract class XtcLauncherTask<E extends XtcLauncherTaskExtension> extends XtcDefaultTask implements XtcLauncherTaskExtension {

    // All inherited from launcher task extension and turned into input
    protected final Property<InputStream> stdin;
    protected final Property<OutputStream> stdout;
    protected final Property<OutputStream> stderr;
    protected final ListProperty<String> jvmArgs;
    protected final Property<Boolean> debug;
    protected final Property<Integer> debugPort;
    protected final Property<Boolean> debugSuspend;
    protected final Property<Boolean> verbose;
    protected final Property<Boolean> fork;
    protected final Property<Boolean> showVersion;
    protected final Property<Boolean> useNativeLauncher;

    protected final E ext;

    protected XtcLauncherTask(final Project project, final E ext) {
        super(project);

        this.ext = ext;

        this.debug = objects.property(Boolean.class).convention(ext.getDebug());
        this.debugPort = objects.property(Integer.class).convention(ext.getDebugPort());
        this.debugSuspend = objects.property(Boolean.class).convention(ext.getDebugSuspend());

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
            stderr.set(ext.getStderr()); // TODO maybe rename the properties to standardOutput, errorOutput etc to conform to Gradle name standard. Right now
            // we clearly want them to be separated from any defaults, though, so we know our launcher tasks pick the correct configured streams.
        }

        this.jvmArgs = objects.listProperty(String.class).convention(ext.getJvmArgs());

        this.verbose = objects.property(Boolean.class).convention(ext.getVerbose());
        this.fork = objects.property(Boolean.class).convention(ext.getFork());
        this.showVersion = objects.property(Boolean.class).convention(ext.getShowVersion());
        this.useNativeLauncher = objects.property(Boolean.class).convention(ext.getUseNativeLauncher());
    }

    @Override
    public void executeTask() {
        super.executeTask();
    }

    @Override
    public boolean hasVerboseLogging() {
        return super.hasVerboseLogging() || verbose.get();
    }

    @Internal
    protected E getExtension() {
        return ext;
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

    @Input
    @Override
    public Property<Boolean> getDebug() {
        return debug;
    }

    @Input
    @Override
    public Property<Integer> getDebugPort() {
        return debugPort;
    }

    @Input
    @Override
    public Property<Boolean> getDebugSuspend() {
        return debugSuspend;
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
        return verbose;
    }

    @Input
    public Property<Boolean> getFork() {
        return fork;
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

    @Internal
    public abstract String getJavaLauncherClassName();

    @Internal
    public abstract String getNativeLauncherCommandName();

    protected <R extends ExecResult> R handleExecResult(final R result) {
        final int exitValue = result.getExitValue();
        if (exitValue != 0) {
            getLogger().error("{} terminated abnormally (exitValue: {}). Rethrowing exception.", prefix(), exitValue);
        }
        result.rethrowFailure();
        result.assertNormalExitValue();
        return result;
    }

    protected XtcLauncher<E, ? extends XtcLauncherTask<E>> createLauncher() {
        final var prefix = prefix();
        if (getUseNativeLauncher().get()) {
            logger.info("{} Created XTC launcher: native executable.", prefix);
            return new NativeBinaryLauncher<>(project, this);
        } else if (getFork().get()) {
            logger.info("{} Created XTC launcher: Java process forked from build.", prefix);
            return new JavaExecLauncher<>(project, this);
        } else {
            logger.warn("{} WARNING: Created XTC launcher: Running launcher in the same thread as the build process. This is not recommended for production",
                    prefix);
            return new BuildThreadLauncher<>(project, this);
        }
    }

    protected List<File> resolveFullModulePath() {
        final var map = new HashMap<String, Set<File>>();

        final Set<File> xdkContents = resolveDirectories(XtcProjectDelegate.getXdkContentsDir(project));
        map.put(XDK_CONFIG_NAME_CONTENTS, xdkContents);

        final Set<File> xtcModuleDeclarations = resolveFiles(getXtcModuleDependencies());
        map.put(XTC_CONFIG_NAME_MODULE_DEPENDENCY, xtcModuleDeclarations);

        for (final var sourceSet : getDependentSourceSets()) {
            final Set<File> sourceSetOutput = resolveDirectories(XtcProjectDelegate.getXtcSourceSetOutputDirectory(project, sourceSet));
            map.put(XTC_LANGUAGE_NAME + sourceSet.getName(), sourceSetOutput);
        }

        logger.info("{} Compilation/runtime full module path resolved as: ", prefix());
        map.forEach((k, v) -> logger.info("{}     Resolved files: {} -> {}", prefix(), k, v));
        return verifiedModulePath(map);
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.ABSOLUTE)
    Provider<Directory> getInputXdkContents() {
        return XtcProjectDelegate.getXdkContentsDir(project);
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getXtcModuleDependencies() {
        final List<SourceSet> sourceSets = getDependentSourceSets();
        final List<String> xtcDependencyConfigNames = sourceSets.stream().map(XtcProjectDelegate::incomingXtcModuleDependencies).toList();
        final FileCollection xtcDependencyConfigs = filesFromConfigs(xtcDependencyConfigNames);
        logger.info("{} Incoming XTC module dependencies for source sets (execution phase: {}): {} -> {}", prefix(), isBeingExecuted(), sourceSets,
                    xtcDependencyConfigNames);
        return xtcDependencyConfigs;
    }

    @Internal
    protected List<SourceSet> getDependentSourceSets() {
        return XtcProjectDelegate.getSourceSets(project).stream().toList();
    }

    protected List<File> verifiedModulePath(final Map<String, Set<File>> map) {
        final var prefix = prefix();
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
        // We sort the module path on File.compareTo, to make it deterministic between configurations.
        return modulePathSet.stream().sorted().toList();
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
            assert !dupes.isEmpty();
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

    private static char yesOrNo(final boolean value) {
        return value ? 'y' : 'n';
    }

    protected final List<String> resolveJvmArgs() {
        final var list = new ArrayList<>(getJvmArgs().get());
        if (getDebug().get()) {
            list.add(String.format("-Xrunjdwp:transport=dt_socket,server=y,suspend=%c,address=%d", yesOrNo(getDebugSuspend().get()), getDebugPort().get()));
            logger.lifecycle("{} Added debug argument: {}", prefix(), jvmArgs.get());
        }
        return Collections.unmodifiableList(list);
    }
}

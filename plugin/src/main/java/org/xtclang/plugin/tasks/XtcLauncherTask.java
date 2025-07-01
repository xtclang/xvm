package org.xtclang.plugin.tasks;

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
import org.gradle.api.model.ObjectFactory;
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
    protected Property<InputStream> stdin;
    protected Property<OutputStream> stdout;
    protected Property<OutputStream> stderr;
    protected ListProperty<String> jvmArgs;
    protected Property<Boolean> debug;
    protected Property<Integer> debugPort;
    protected Property<Boolean> debugSuspend;
    protected Property<Boolean> verbose;
    protected Property<Boolean> fork;
    protected Property<Boolean> showVersion;
    protected Property<Boolean> useNativeLauncher;

    protected transient E ext; // transient to avoid configuration cache issues

    @SuppressWarnings("this-escape")
    protected XtcLauncherTask(final E ext) {
        super();
        this.ext = ext;
    }
    
    // Removed initializeProperties - now using lazy initialization in getters
    
    @Override
    public void executeTask() {
        super.executeTask();
    }


    @Override
    public boolean hasVerboseLogging() {
        return super.hasVerboseLogging() || getVerbose().get();
    }

    @Internal
    protected E getExtension() {
        if (ext == null) {
            // For subclasses that override this method, this will never be called
            throw new IllegalStateException("Extension not initialized");
        }
        return ext;
    }

    private FileCollection inputXtcJavaToolsConfig;
    
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getInputXtcJavaToolsConfig() {
        if (inputXtcJavaToolsConfig == null) {
            inputXtcJavaToolsConfig = getObjects().fileCollection();
            // Configuration will be added lazily when project is available
        }
        return inputXtcJavaToolsConfig;
    }

    public boolean hasStdinRedirect() {
        return getStdin().isPresent();
    }

    public boolean hasStdoutRedirect() {
        return getStdout().isPresent();
    }

    public boolean hasStderrRedirect() {
        return getStderr().isPresent();
    }

    @SuppressWarnings("unused")
    public boolean hasOutputRedirects() {
        return hasStdoutRedirect() || hasStderrRedirect();
    }

    @Input
    @Override
    public Property<Boolean> getDebug() {
        if (debug == null) {
            debug = getObjects().property(Boolean.class).convention(false);
        }
        return debug;
    }

    @Input
    @Override
    public Property<Integer> getDebugPort() {
        if (debugPort == null) {
            debugPort = getObjects().property(Integer.class).convention(5005);
        }
        return debugPort;
    }

    @Input
    @Override
    public Property<Boolean> getDebugSuspend() {
        if (debugSuspend == null) {
            debugSuspend = getObjects().property(Boolean.class).convention(true);
        }
        return debugSuspend;
    }

    @Optional
    @Input
    public Property<InputStream> getStdin() {
        if (stdin == null) {
            stdin = getObjects().property(InputStream.class);
        }
        return stdin;
    }

    @Optional
    @Input
    public Property<OutputStream> getStdout() {
        if (stdout == null) {
            stdout = getObjects().property(OutputStream.class);
        }
        return stdout;
    }

    @Optional
    @Input
    public Property<OutputStream> getStderr() {
        if (stderr == null) {
            stderr = getObjects().property(OutputStream.class);
        }
        return stderr;
    }

    @Override
    public void jvmArg(final Provider<? extends String> arg) {
        if (getProject() != null) {
            jvmArgs(singleArgumentIterableProvider(getProject(), arg));
        } else {
            // During construction, just add the provider directly
            getJvmArgs().add(arg);
        }
    }

    @Override
    public void jvmArgs(final String... args) {
        getJvmArgs().addAll(argumentArrayToList(args));
    }

    @Override
    public void jvmArgs(final Iterable<? extends String> args) {
        getJvmArgs().addAll(args);
    }

    @Override
    public void jvmArgs(final Provider<? extends Iterable<? extends String>> provider) {
        getJvmArgs().addAll(provider);
    }

    @Override
    public void setJvmArgs(final Iterable<? extends String> elements) {
        getJvmArgs().set(elements);
    }

    @Override
    public void setJvmArgs(final Provider<? extends Iterable<? extends String>> provider) {
        getJvmArgs().set(provider);
    }

    @Input
    public Property<Boolean> getVerbose() {
        if (verbose == null) {
            verbose = getObjects().property(Boolean.class).convention(false);
        }
        return verbose;
    }

    @Input
    public Property<Boolean> getFork() {
        if (fork == null) {
            fork = getObjects().property(Boolean.class).convention(true);
        }
        return fork;
    }

    @Input
    public Property<Boolean> getShowVersion() {
        if (showVersion == null) {
            showVersion = getObjects().property(Boolean.class).convention(false);
        }
        return showVersion;
    }

    @Input
    public Property<Boolean> getUseNativeLauncher() {
        if (useNativeLauncher == null) {
            useNativeLauncher = getObjects().property(Boolean.class).convention(false);
        }
        return useNativeLauncher;
    }

    @Optional
    @Input
    public ListProperty<String> getJvmArgs() {
        if (jvmArgs == null) {
            jvmArgs = getObjects().listProperty(String.class);
        }
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
            getLogger().info("{} Created XTC launcher: native executable.", prefix);
            return new NativeBinaryLauncher<>(getProject(), this);
        } else if (getFork().get()) {
            getLogger().info("{} Created XTC launcher: Java process forked from build.", prefix);
            return new JavaExecLauncher<>(getProject(), this);
        } else {
            getLogger().warn("{} WARNING: Created XTC launcher: Running launcher in the same thread as the build process. This is not recommended for production",
                    prefix);
            return new BuildThreadLauncher<>(getProject(), this);
        }
    }

    protected List<File> resolveFullModulePath() {
        final var map = new HashMap<String, Set<File>>();

        final Set<File> xdkContents = resolveDirectories(XtcProjectDelegate.getXdkContentsDir(getProject()));
        map.put(XDK_CONFIG_NAME_CONTENTS, xdkContents);

        final Set<File> xtcModuleDeclarations = resolveFiles(getXtcModuleDependencies());
        map.put(XTC_CONFIG_NAME_MODULE_DEPENDENCY, xtcModuleDeclarations);

        for (final var sourceSet : getDependentSourceSets()) {
            final Set<File> sourceSetOutput = resolveDirectories(XtcProjectDelegate.getXtcSourceSetOutputDirectory(getProject(), sourceSet));
            map.put(XTC_LANGUAGE_NAME + capitalize(sourceSet.getName()), sourceSetOutput);
        }

        getLogger().info("{} Compilation/runtime full module path resolved as: ", prefix());
        map.forEach((k, v) -> getLogger().info("{}     Resolved files: {} -> {}", prefix(), k, v));
        return verifiedModulePath(map);
    }

    private Provider<Directory> inputXdkContents;
    
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    Provider<Directory> getInputXdkContents() {
        if (inputXdkContents == null) {
            inputXdkContents = getLayout().getBuildDirectory().dir("xtc/xdk/lib");
        }
        return inputXdkContents;
    }

    private FileCollection xtcModuleDependencies;
    
    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getXtcModuleDependencies() {
        if (xtcModuleDependencies == null) {
            xtcModuleDependencies = getObjects().fileCollection();
            // Dependencies will be configured lazily when project is available
        }
        return xtcModuleDependencies;
    }

    @Internal
    protected List<SourceSet> getDependentSourceSets() {
        if (getProject() == null) {
            return Collections.emptyList();
        }
        return XtcProjectDelegate.getSourceSets(getProject()).stream().toList();
    }

    protected List<File> verifiedModulePath(final Map<String, Set<File>> map) {
        final var prefix = prefix();
        getLogger().info("{} ModulePathMap: [{} keys and {} values]", prefix, map.keySet().size(), map.values().stream().mapToInt(Set::size).sum());

        final var modulePathList = new ArrayList<File>();
        map.forEach((provider, files) -> {
            getLogger().info("{}     Module path from: '{}':", prefix, provider);
            if (files.isEmpty()) {
                getLogger().info("{}         (empty)", prefix);
            }
            files.forEach(f -> getLogger().info("{}         {}", prefix, f.getAbsolutePath()));

            modulePathList.addAll(files.stream().filter(f -> {
                if (f.isDirectory()) {
                    getLogger().info("{} Adding directory to module path ({}).", prefix, f.getAbsolutePath());
                } else if (!isValidXtcModule(f)) {
                    getLogger().warn("{} Has a non .xtc module file on the module path ({}). Was this intended?", prefix, f.getAbsolutePath());
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
            getLogger().warn("{} There are {} duplicated modules on the full module path.", prefix, modulePathListSize - modulePathSetSize);
        }

        checkDuplicatesInModulePaths(modulePathSet);

        // Check that all modules on path are XTC files.
        getLogger().info("{} Final module path: {}", prefix, modulePathSet);
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
        return resolveFiles(getProject().files(dirProvider));
    }

    protected Set<File> resolveDirectories(final Provider<Directory> dirProvider) {
        return resolveDirectories(resolveFiles(getProject().files(dirProvider)));
    }

    private static char yesOrNo(final boolean value) {
        return value ? 'y' : 'n';
    }

    protected final List<String> resolveJvmArgs() {
        final var list = new ArrayList<>(getJvmArgs().get());
        if (getDebug().get()) {
            list.add(String.format("-Xrunjdwp:transport=dt_socket,server=y,suspend=%c,address=%d", yesOrNo(getDebugSuspend().get()), getDebugPort().get()));
            getLogger().lifecycle("{} Added debug argument: {}", prefix(), getJvmArgs().get());
        }
        return Collections.unmodifiableList(list);
    }
}

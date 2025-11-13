package org.xtclang.plugin.launchers;

import static java.util.Objects.requireNonNull;

import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_CONTENTS;
import static org.xtclang.plugin.XtcPluginConstants.XTC_CONFIG_NAME_MODULE_DEPENDENCY;
import static org.xtclang.plugin.XtcPluginConstants.XTC_LANGUAGE_NAME;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.isValidXtcModuleSafe;
import static org.xtclang.plugin.XtcPluginUtils.capitalize;

import java.io.File;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.tasks.XtcLauncherTask;

/**
 * Resolves and validates the module path for XTC launcher tasks.
 * Handles XDK contents, custom module paths, XTC module dependencies, and source set outputs.
 */
public class ModulePathResolver {
    private final Logger logger;
    private final ObjectFactory objects;
    private final Provider<@NotNull Directory> xdkContentsDir;
    private final Map<String, Provider<@NotNull Directory>> sourceSetOutputDirs;
    private final ConfigurableFileCollection customModulePath;
    private final Provider<@NotNull FileCollection> xtcModuleDependencies;

    public ModulePathResolver(final XtcLauncherTask<?> task) {
        this.logger = task.getLogger();
        this.objects = task.getObjects();
        this.xdkContentsDir = task.getInputXdkContents();
        this.sourceSetOutputDirs = task.getSourceSetOutputDirs();
        this.customModulePath = task.getModulePath();
        this.xtcModuleDependencies = task.getXtcModuleDependenciesProvider();
    }

    public List<File> resolveFullModulePath() {
        final var map = new HashMap<String, Set<File>>();

        final Set<File> xdkContents = resolveDirectories(xdkContentsDir);
        map.put(XDK_CONFIG_NAME_CONTENTS, xdkContents);

        // If custom module path is specified, use it instead of xtcModule dependencies
        // This supports aggregator projects that collect modules in a custom location
        if (!customModulePath.isEmpty()) {
            final Set<File> customModulePathSet = resolveAsDirectories(customModulePath);
            map.put("customModulePath", customModulePathSet);
        } else {
            // Use xtcModule dependencies only when no custom module path is set
            final Set<File> xtcModuleDeclarations = resolveFiles(xtcModuleDependencies.get());
            map.put(XTC_CONFIG_NAME_MODULE_DEPENDENCY, xtcModuleDeclarations);
        }

        for (final var entry : sourceSetOutputDirs.entrySet()) {
            final String sourceSetName = entry.getKey();
            final Provider<@NotNull Directory> outputDir = entry.getValue();
            final Set<File> sourceSetOutput = resolveDirectories(outputDir);
            map.put(XTC_LANGUAGE_NAME + capitalize(sourceSetName), sourceSetOutput);
        }

        logger.info("[plugin] Compilation/runtime full module path resolved as: ");
        map.forEach((k, v) -> logger.info("[plugin]     Resolved files: {} -> {}", k, v));
        return verifiedModulePath(map);
    }

    private List<File> verifiedModulePath(final Map<String, Set<File>> map) {
        logger.info("[plugin] ModulePathMap: [{} keys and {} values]", map.size(), map.values().stream().mapToInt(Set::size).sum());

        final var modulePathList = new ArrayList<File>();
        map.forEach((provider, files) -> {
            logger.info("[plugin]     Module path from: '{}':", provider);
            if (files.isEmpty()) {
                logger.info("[plugin]         (empty)");
            }
            files.forEach(f -> logger.info("[plugin]         {}", f.getAbsolutePath()));

            modulePathList.addAll(files.stream().filter(f -> {
                if (f.isDirectory()) {
                    logger.info("[plugin] Adding directory to module path ({}).", f.getAbsolutePath());
                } else if (!isValidXtcModuleSafe(f, logger)) {
                    logger.warn("[plugin] Has a non .xtc module file on the module path ({}). Was this intended?", f.getAbsolutePath());
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
            logger.warn("[plugin] There are {} duplicated modules on the full module path.", modulePathListSize - modulePathSetSize);
        }

        checkDuplicatesInModulePaths(modulePathSet);

        // Check that all modules on path are XTC files.
        logger.info("[plugin] Final module path: {}", modulePathSet);
        // We sort the module path on File.compareTo, to make it deterministic between configurations.
        return modulePathSet.stream().sorted().toList();
    }

    private static void checkDuplicatesInModulePaths(final Set<File> modulePathSet) {
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
                throw new GradleException("[plugin] A dependency with the same name is defined in more than one (" + dupes.size() + ") location on the module path.");
            }
        }
    }

    public static Set<File> resolveFiles(final FileCollection files) {
        return files.isEmpty() ? Collections.emptySet() : files.getAsFileTree().getFiles();
    }

    public static Set<File> resolveDirectories(final Set<File> files) {
        return files.stream().map(f -> requireNonNull(f.getParentFile())).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Resolves a FileCollection to a set of directories, preserving directories as-is
     * instead of expanding them to their contents. For files, returns their parent directory.
     */
    public static Set<File> resolveAsDirectories(final FileCollection files) {
        if (files.isEmpty()) {
            return Collections.emptySet();
        }
        return files.getFiles().stream()
                .map(f -> f.isDirectory() ? f : requireNonNull(f.getParentFile()))
                .collect(Collectors.toUnmodifiableSet());
    }

    @SuppressWarnings("unused")
    protected Set<File> resolveFiles(final Provider<@NotNull Directory> dirProvider) {
        return resolveFiles(objects.fileCollection().from(dirProvider));
    }

    protected Set<File> resolveDirectories(final Provider<@NotNull Directory> dirProvider) {
        return resolveDirectories(resolveFiles(objects.fileCollection().from(dirProvider)));
    }
}
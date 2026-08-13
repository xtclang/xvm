package org.xvm.tool;

import java.io.File;
import java.io.IOException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.stream.Collectors;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.tool.LauncherOptions.BundlerOptions;

import static org.xvm.util.Handy.quoted;
import static org.xvm.util.Severity.ERROR;
import static org.xvm.util.Severity.INFO;
import static org.xvm.util.Severity.WARNING;

/**
 * The "bundle" command:
 * <p>
 *  java org.xvm.tool.Bundler [-L repo(s)] [-o output] [--main module] [module_name_or_file ...]
 * <p>
 * Merges compiled modules into a single multi-module .xtc file (a "bundle"). The resulting file is
 * a self-contained module repository: the runner and compiler can resolve every bundled module from
 * it by name, e.g. {@code xec -L app.bundle.xtc app.bundle.xtc}.
 * <p>
 * With no explicit module selection, every non-system module found on the module path is bundled.
 * System (xtclang.org) modules are never bundled implicitly; they remain external fingerprint
 * dependencies, resolved from the XDK at run time, exactly as with a lib directory.
 */
public class Bundler extends Launcher<BundlerOptions> {

    /**
     * The domain suffix of the system/XDK modules, which are excluded from implicit bundling.
     */
    private static final String SYSTEM_DOMAIN_SUFFIX = ".xtclang.org";

    /**
     * Bundler constructor for programmatic use.
     *
     * @param options     pre-configured bundler options
     * @param console     representation of the terminal within which this command is run, or null
     * @param errListener optional ErrorListener to receive errors, or null for no delegation
     */
    public Bundler(BundlerOptions options, Console console, ErrorListener errListener) {
        super(options, console, errListener);
    }

    /**
     * Entry point from the OS. Delegates to Launcher.
     *
     * @param asArg command line arguments
     */
    static void main(String[] asArg) {
        Launcher.main(insertCommand(CMD_BUNDLE, asArg));
    }

    @Override
    protected int process() {
        var opts = options();
        var repo = configureLibraryRepo(opts.getModulePath());
        checkErrors("repository setup");

        // select the modules to bundle, keyed (and ordered) by qualified module name
        var selection = selectModules(repo);
        checkErrors("module selection");

        var moduleMain = determineMainModule(selection);
        checkErrors("main module determination");

        // merge everything into one container; merge() upgrades fingerprints to real modules and
        // synthesizes fingerprints for any dependencies left external, so selection order is
        // irrelevant here
        var bundle = new FileStructure(moduleMain, /*fSynthesize*/ false);
        selection.values().stream()
                .filter(module -> module != moduleMain)
                .forEach(module -> bundle.merge(module, /*fSynthesize*/ false, /*fTakeFile*/ false));

        // non-main real modules of a persistent container are embedded modules, not primaries
        bundle.children().stream()
                .filter(module -> !module.isFingerprint() && !module.isMainModule())
                .forEach(ModuleStructure::markEmbedded);

        reportExternalDependencies(bundle, repo, selection.keySet());
        assert bundle.validateModuleConstants();

        var fileOut   = resolveOutputFile(moduleMain);
        var dirParent = fileOut.getAbsoluteFile().getParentFile();
        if (dirParent != null && !dirParent.exists() && !dirParent.mkdirs()) {
            log(ERROR, "Unable to create the output directory {}", dirParent);
            return checkErrors("bundle output");
        }
        try {
            bundle.writeTo(fileOut);
        } catch (IOException e) {
            log(ERROR, e, "Failure writing the bundle to {}", fileOut);
            return checkErrors("bundle output");
        }

        log(INFO, "Wrote {} ({} bytes) containing {} modules: {}", fileOut, fileOut.length(),
                selection.size(), String.join(", ", selection.keySet()));
        return checkErrors("bundle");
    }

    /**
     * Resolve the modules to bundle: the explicitly selected ones (module names or .xtc files), or
     * every non-system module on the module path if nothing was selected explicitly.
     *
     * @param repo  the module repository built from the module path
     *
     * @return the selected modules, keyed by qualified module name, in stable selection order
     */
    private Map<String, ModuleStructure> selectModules(ModuleRepository repo) {
        var selection = new LinkedHashMap<String, ModuleStructure>();
        var explicit  = options().getModuleSelection();

        if (explicit.isEmpty()) {
            var includeSystem = options().isIncludeSystem();
            repo.getModuleNames().stream()
                    .filter(name -> includeSystem || !name.endsWith(SYSTEM_DOMAIN_SUFFIX))
                    .sorted()
                    .forEach(name -> selection.put(name, repo.loadModule(name)));
            if (selection.isEmpty()) {
                log(ERROR, "No modules to bundle: the module path contains no non-system modules");
            }
            return selection;
        }

        for (var spec : explicit) {
            var module = ModuleInfo.isExplicitCompiledFile(spec)
                    ? loadModuleFile(new File(spec))
                    : repo.loadModule(spec);
            if (module == null) {
                log(ERROR, "Unable to load module {} from the module path or file system", quoted(spec));
            } else {
                selection.put(module.getIdentityConstant().getName(), module);
            }
        }
        return selection;
    }

    private ModuleStructure loadModuleFile(File file) {
        try {
            return new FileStructure(file).getModule();
        } catch (Exception e) {
            log(ERROR, e, "Failure reading module file {}", file);
            return null;
        }
    }

    /**
     * Determine the bundle's main module: the one named by --main, or the only selected module
     * that no other selected module depends on.
     *
     * @param selection  the selected modules, keyed by qualified module name
     *
     * @return the main module, or null if it could not be determined (an error has been logged)
     */
    private ModuleStructure determineMainModule(Map<String, ModuleStructure> selection) {
        var optMain = options().getMainModule();
        if (optMain.isPresent()) {
            var module = selection.get(optMain.get());
            if (module == null) {
                log(ERROR, "The specified main module {} is not among the bundled modules: {}",
                        quoted(optMain.get()), String.join(", ", selection.keySet()));
            }
            return module;
        }

        // a root is a selected module that no OTHER selected module imports; dependency name
        // sets are collected once per module up front (collectDependencies walks structures)
        var depNamesByModule = selection.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue()
                        .collectDependencies().keySet().stream()
                        .map(ModuleConstant::getName)
                        .collect(Collectors.toSet())));

        var roots = selection.entrySet().stream()
                .filter(entry -> depNamesByModule.entrySet().stream()
                        .noneMatch(other -> !other.getKey().equals(entry.getKey())
                                && other.getValue().contains(entry.getKey())))
                .map(Map.Entry::getValue)
                .toList();

        if (roots.size() == 1) {
            return roots.getFirst();
        }

        var candidates = roots.stream()
                .map(module -> module.getIdentityConstant().getName())
                .collect(Collectors.joining(", "));
        log(ERROR, roots.isEmpty()
                        ? "No main module candidate found (circular imports?); specify one with --main"
                        : "Ambiguous main module; specify one with --main from: {}",
                candidates);
        return null;
    }

    /**
     * Report the bundle's remaining external dependencies, and warn about modules that are present
     * on the module path but were not selected for bundling.
     */
    private void reportExternalDependencies(FileStructure bundle, ModuleRepository repo,
                                            Set<String> bundled) {
        var external = bundle.children().stream()
                .filter(ModuleStructure::isFingerprint)
                .map(module -> module.getIdentityConstant().getName())
                .sorted()
                .toList();
        if (external.isEmpty()) {
            return;
        }

        log(INFO, "External module dependencies (resolved at run time): {}",
                String.join(", ", external));

        var unbundled = external.stream()
                .filter(name -> !name.endsWith(SYSTEM_DOMAIN_SUFFIX))
                .filter(name -> !bundled.contains(name) && repo.getModuleNames().contains(name))
                .toList();
        if (!unbundled.isEmpty()) {
            log(WARNING, "Modules present on the module path but NOT included in the bundle: {}",
                    String.join(", ", unbundled));
        }
    }

    /**
     * @return the output file to write the bundle to, honoring -o (file or directory); the default
     *         name is {@code <main-module-simple-name>.bundle.xtc}
     */
    private File resolveOutputFile(ModuleStructure moduleMain) {
        var nameDefault = moduleMain.getIdentityConstant().getUnqualifiedName() + ".bundle.xtc";
        return options().getOutputFile()
                .map(file -> file.isDirectory() ? new File(file, nameDefault) : file)
                .orElseGet(() -> new File(nameDefault));
    }

    @Override
    protected void validateOptions() {
        validateModulePath();

        var opts = options();
        if (opts.getModulePath().isEmpty() && opts.getModuleSelection().isEmpty()) {
            log(ERROR, "Nothing to bundle: specify a module path (-L) and/or explicit module files");
        }
    }

    @Override
    public String desc() {
        return """
            Ecstasy bundler:

                Merges compiled modules into a single multi-module .xtc file that acts as a
                self-contained module repository.

                With no explicit module selection, bundles every non-system module found on
                the module path (-L). System (xtclang.org) modules always remain external
                dependencies, resolved from the XDK at run time.""";
    }
}

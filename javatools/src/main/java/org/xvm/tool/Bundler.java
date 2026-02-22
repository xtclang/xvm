package org.xvm.tool;


import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.ModuleStructure.ModuleType;

import org.xvm.tool.LauncherOptions.BundlerOptions;

import static org.xvm.util.Handy.quoted;
import static org.xvm.util.Severity.ERROR;
import static org.xvm.util.Severity.INFO;


/**
 * The "bundle" command:
 * <p>
 * Bundles multiple compiled .xtc module files into a single .xtc file containing multiple modules.
 * The bundled file can then be used as a library path entry, where all contained modules are
 * available for linking.
 * <p>
 * Usage: {@code xtc bundle -o output.xtc input1.xtc input2.xtc [...]}
 */
public class Bundler
        extends Launcher<BundlerOptions> {

    /**
     * Bundler constructor for programmatic use.
     *
     * @param options      pre-configured bundler options
     * @param console      representation of the terminal within which this command is run, or null
     * @param errListener  optional ErrorListener to receive errors, or null for no delegation
     */
    public Bundler(BundlerOptions options, Console console, ErrorListener errListener) {
        super(options, console, errListener);
    }

    /**
     * Entry point from the OS. Delegates to Launcher.
     *
     * @param args command line arguments
     */
    static void main(String[] args) {
        Launcher.main(insertCommand(CMD_BUNDLE, args));
    }

    @Override
    protected void validateOptions() {
        var opts = options();

        if (opts.getOutputFile().isEmpty()) {
            log(ERROR, "Output file (-o) is required");
        }

        var inputs = opts.getInputFiles();
        if (inputs.isEmpty()) {
            log(ERROR, "At least one input .xtc file is required");
        }

        for (File input : inputs) {
            if (!input.exists()) {
                log(ERROR, "Input file does not exist: {}", quoted(input));
            } else if (!input.isFile()) {
                log(ERROR, "Input is not a file: {}", quoted(input));
            } else if (!input.getName().endsWith(".xtc")) {
                log(ERROR, "Input file is not an .xtc file: {}", quoted(input));
            }
        }

        validateModulePath();
    }

    @Override
    protected int process() {
        var opts       = options();
        var outputFile = opts.getOutputFile().orElseThrow();
        var inputFiles = opts.getInputFiles();

        // Step 1: Load all input modules
        List<ModuleStructure> modules = new ArrayList<>();
        for (File inputFile : inputFiles) {
            log(INFO, "Loading module from: {}", inputFile);
            try {
                var struct = new FileStructure(inputFile);
                var module = struct.getModule();
                if (module == null) {
                    log(ERROR, "No primary module found in: {}", inputFile);
                    continue;
                }
                modules.add(module);
            } catch (IOException e) {
                log(ERROR, "Failed to load module from {}: {}", inputFile, e.getMessage());
            }
        }
        checkErrors();

        if (modules.isEmpty()) {
            log(ERROR, "No modules loaded");
            return 1;
        }

        // Step 2: Select the primary module
        var        primaryName   = opts.getPrimaryModule();
        int        primaryIndex  = 0;

        if (primaryName.isPresent()) {
            String name = primaryName.get();
            boolean found = false;
            for (int i = 0; i < modules.size(); i++) {
                if (modules.get(i).getIdentityConstant().getName().equals(name)) {
                    primaryIndex = i;
                    found = true;
                    break;
                }
            }
            if (!found) {
                log(ERROR, "Primary module {} not found among input modules", quoted(name));
                return 1;
            }
        }

        var primaryModule = modules.get(primaryIndex);
        log(INFO, "Primary module: {}", primaryModule.getIdentityConstant().getName());

        // Step 3: Create FileStructure from the primary module
        var bundleStruct = new FileStructure(primaryModule, false);

        // Step 4: Merge remaining modules
        for (int i = 0; i < modules.size(); i++) {
            if (i == primaryIndex) {
                continue;
            }

            var module = modules.get(i);
            log(INFO, "Merging module: {}", module.getIdentityConstant().getName());
            bundleStruct.merge(module, false, false);
        }

        // Step 5: Reclassify merged modules from Primary to Embedded
        for (ModuleStructure module : bundleStruct.children()) {
            if (!module.isFingerprint()
                    && !module.getIdentityConstant().equals(bundleStruct.getModuleId())) {
                module.setModuleType(ModuleType.Embedded);
            }
        }

        // Step 6: Remove fingerprint modules that reference now-embedded modules
        List<ModuleStructure> toRemove = new ArrayList<>();
        for (ModuleStructure module : bundleStruct.children()) {
            if (module.isFingerprint()) {
                String fpName = module.getIdentityConstant().getName();
                // check if this fingerprint references a module that is now embedded
                ModuleStructure embedded = bundleStruct.findModule(fpName);
                if (embedded != null && !embedded.isFingerprint()) {
                    toRemove.add(module);
                }
            }
        }
        for (ModuleStructure fp : toRemove) {
            log(INFO, "Removing resolved fingerprint: {}", fp.getIdentityConstant().getName());
            bundleStruct.removeChild(fp);
        }

        // Step 7: Write the bundle
        try {
            bundleStruct.writeTo(outputFile);
        } catch (IOException e) {
            log(ERROR, "Failed to write bundle to {}: {}", outputFile, e.getMessage());
            return 1;
        }

        // Step 8: Report
        int moduleCount = 0;
        for (ModuleStructure module : bundleStruct.children()) {
            if (!module.isFingerprint()) {
                moduleCount++;
            }
        }
        out("Bundled " + moduleCount + " modules into " + outputFile);

        return 0;
    }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc() {
        return """
            Ecstasy module bundler:

                Bundles multiple compiled .xtc modules into a single file.""";
    }
}

package org.xvm.tool;


import java.io.File;
import java.io.IOException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;

import org.xvm.compiler.Token.Id;

import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.ModuleInfo.Node;

import org.xvm.util.Severity;

import static org.xvm.compiler.Compiler.MODULE_MISSING;
import static org.xvm.tool.ModuleInfo.isExplicitCompiledFile;
import static org.xvm.util.Handy.parentOf;
import static org.xvm.util.Handy.quoted;
import static org.xvm.util.Handy.resolveFile;
import static org.xvm.util.Severity.ERROR;
import static org.xvm.util.Severity.FATAL;
import static org.xvm.util.Severity.INFO;
import static org.xvm.util.Severity.WARNING;


/**
 * This is the command-line Ecstasy compiler.
 * <p>
 * <p/>Find the root of the module containing the code in the current directory, and compile it, placing
 * the result in the default location:
 * <p>
 * <p/>{@code  xcc}
 *
 * <p/>Compile the specified module, placing the result in the default location:
 * <p>
 * <p/>{@code  xcc ./path/to/module_name.x}
 *
 * <p/>Compile the module that the specified file belongs to:
 * <p>
 * <p/>{@code  xcc MyClass.x}
 *
 * <p/>Alternatively, either of the following would work:
 * <p>
 * <p/>{@code  xcc MyClass.xtc}
 * <p/>{@code  xcc MyClass}
 *
 * <p/>The location for the resulting {@code .xtc} file follows the following rules:
 * <ul>
 * <li>If the "-o" option is specified, then the corresponding location is used for the output;</li>
 * <li>If the module source file is named "MyModule.x", then the output is in the same-named file
 *     with the ".xtc" extension, such as "MyModule.xtc".</li>
 * <li>If the module is in a project directory format, then the resulting ".xtc" file is placed in
 *     the build or target directory of the project. For example, if the module file is located at
 *     "app/src/main/x/app.x", and there is a directory "app/build", then the resulting ".xtc" file
 *     is written to "app/build/app.xtc".</li>
 * </ul>
 *
 * <p/>The location of additional resource files and/or directories can be specified by using the
 * {@code -r} option; for example:
 * <p>
 * <p/>{@code  xcc -r ~/dev/prj/otherApp/build/}
 *
 * <p/>The location of the resulting {@code .xtc} file can be specified by using the {@code -o}
 * option; for example:
 * <p>
 * <p/>{@code  xcc -o ~/modules/}
 *
 * <p/>The version of the resulting module can be specified by using the {@code -version} option;
 * for example:
 * <p>
 * <p/>{@code  xcc --set-version 0.4-alpha}
 *
 * <p/>In addition to built-in Ecstasy modules and modules located in the Ecstasy runtime library,
 * it is possible to provide a search path for modules that will be used by the compiler. The search
 * path can contain directories and/or ".xtc" files:
 * <p>
 * <p/>{@code  xcc -L ~/modules/:../build/:Utils.xtc}
 *
 * <p/>Other command line options:
 * <ul>
 * <li>{@code --rebuild} - force rebuild, even if the build appears to be up-to-date</li>
 * <li>{@code --qualify} - use fully qualified module names as the basis for output file names</li>
 * <li>{@code --nosrc} - (not implemented) do not include source code in the compiled module</li>
 * <li>{@code --nodbg} - (not implemented) do not include debugging information in the compiled module</li>
 * <li>{@code --nodoc} - (not implemented) do not include documentation in the compiled module</li>
 * <li>{@code --strict} - convert warnings to errors</li>
 * <li>{@code --nowarn} - suppress warnings</li>
 * <li>{@code -v} / {@code --verbose} - provide information about the work being done by the compilation process</li>
 * </ul>
 */
public class Compiler extends Launcher<CompilerOptions> {

    public Compiler(CompilerOptions options) {
        this(options, Launcher.DEFAULT_CONSOLE, null);
    }

    /**
     * Compiler constructor for programmatic use.
     *
     * @param options     pre-configured compiler options
     * @param console     representation of the terminal within which this command is run, or null
     * @param errListener optional ErrorListener to receive errors, or null for no delegation
     */
    public Compiler(CompilerOptions options, Console console, ErrorListener errListener) {
        super(options, console, errListener);
    }

    /**
     *
     * Entry point from the OS. Delegates to Launcher.
     *
     * @param args command line arguments
     */
    static void main(String[] args) {
        Launcher.main(insertCommand(CMD_BUILD, args));
    }

    /**
     * Get the input locations (source files) to compile.
     * Subclasses can override to provide dynamic sources.
     *
     * @return list of input source files
     */
    protected List<File> getInputLocations() {
        return options().getInputLocations();
    }

    // TODO: Also support a process call with an optional options paramter (and likely run and stuff...)
    @Override
    protected int process() {
        final var opts = options();

        if (opts.showVersion()) {
            showSystemVersion(ensureLibraryRepo());
        }

        log(INFO, "Selecting compilation targets");

        var resourceDirs = opts.getResourceLocations();
        var outputLoc    = opts.getOutputLocation().orElse(null);
        var targets      = selectTargets(opts.getInputLocations(), resourceDirs, outputLoc);

        prevModules = targets;

        int cTargets = targets.size();
        if (cTargets == 0) {
            if (opts.showVersion()) {
                return 0;
            }
            displayHelp();
            return 1;
        }

        if (outputLoc != null) {
            outputLoc = resolveFile(outputLoc);
        }

        var infoByName = new LinkedHashMap<String, ModuleInfo>();
        for (int i = 0; i < cTargets; ++i) {
            var info    = targets.get(i);
            var sModule = info.getQualifiedModuleName();
            var srcFile = info.getSourceFile();
            var binFile = info.getBinaryFile();

            log(srcFile == null ? ERROR : INFO, "  [{}]={}", i, srcFile == null ? "<unknown>" : srcFile.getPath());

            if (i == 1 && outputLoc != null && !outputLoc.isDirectory() && isExplicitCompiledFile(outputLoc.getName())) {
                log(ERROR, "Multiple modules are being compiled, but only one output module file name ({}) was specified; specify a target directory instead", outputLoc);
            }
            if (srcFile == null || !srcFile.exists()) {
                log(ERROR, "Could not locate the source for the module {}", info.getFileSpec());
            }
	        // TODO: Consider log(ERROR, ...) -> error(...)
            if (sModule == null) {
                log(ERROR, "Could not determine the module name for {}", info.getFileSpec());
            } else {
                infoByName.put(sModule, info);
            }
            if (binFile == null) {
                log(ERROR, "Could not determine the target location for {}; the module project may be missing a \"build\" or \"target\" directory", info.getFileSpec());
            } else {
                parentOf(binFile)
                        .filter(dir -> !dir.isDirectory() && dir.exists())
                        .ifPresent(_ -> log(ERROR,
                                "The output file {} cannot be written because its parent directory cannot be created because a file already exists with the same name",
                                binFile));
            }
        }
        checkErrors("target selection");

        final boolean fRebuild = opts.isForcedRebuild();
        final Optional<Version> verStamp = opts.getVersion();
        log(INFO, "Output-path={}, force-rebuild={}", outputLoc, fRebuild);

        final var mapTargets = new LinkedHashMap<File, Node>();
        var cSystemModules = 0;
        for (var moduleInfo : targets) {
            log(INFO, "Loading and parsing sources for module: {}", moduleInfo.getQualifiedModuleName());
            var node = moduleInfo.getSourceTree(this);
            // short-circuit the compilation of any up-to-date modules
            if (fRebuild || !moduleInfo.isUpToDate()) {
                mapTargets.put(moduleInfo.getSourceFile(), node);
                if (moduleInfo.isSystemModule()) {
                    ++cSystemModules;
                }
            } else if (verStamp.isPresent() && !verStamp.get().equals(moduleInfo.getModuleVersion())) {
                // recompile is not required, but the version stamp needs to be added
                log(INFO, "Stamping version {} onto module: {}", verStamp.get(), moduleInfo.getQualifiedModuleName());
                addVersion(moduleInfo, verStamp.get());
            }
        }
        checkErrors("source loading");

        if (mapTargets.isEmpty()) {
            log(INFO, "All modules are up to date; terminating compiler");
            return 0;
        }
        final var allNodes = List.copyOf(mapTargets.values());
        flushAndCheckErrors(allNodes);

        // repository setup
        ensureLibraryRepo();
        checkErrors("repository setup");

        if (cSystemModules == 0) {
            log(INFO, "Pre-loading and linking system libraries");
            prelinkSystemLibraries(repoLib);
        }
        prevLibs = repoLib;
        checkErrors("system library linking");

        final var repoOutput = new ModuleInfoRepository(infoByName, false);
        prevOutput = repoOutput;
        checkErrors("output repository setup");

        // the code below could be extracted if necessary: compile(allNodes, repoLib, repoOutput);
        log(INFO, "Creating empty modules and populating namespaces");
        final var mapCompilers = resolveCompilers(allNodes, repoLib);
        log(INFO, "Resolved compilers: {}", mapCompilers);
        flushAndCheckErrors(allNodes);

        log(INFO, "Resolving names and dependencies");
        final var compilers = List.copyOf(mapCompilers.values());
        linkModules(compilers, repoLib);
        flushAndCheckErrors(allNodes);

        resolveNames(compilers);
        flushAndCheckErrors(allNodes);

        injectNativeTurtle(repoLib);
        checkErrors("native turtle injection");

        log(INFO, "Validating expressions");
        validateExpressions(compilers);
        flushAndCheckErrors(allNodes);

        log(INFO, "Generating code");
        generateCode(compilers);
        flushAndCheckErrors(allNodes);

        if (allNodes.size() == 1) {
            log(INFO, "Storing results of compilation: {}", allNodes.getFirst().moduleInfo().getBinaryFile());
        } else {
            log(INFO, "Storing results of compilation:");
            for (var node : allNodes) {
                var info = node.moduleInfo();
                log(INFO, "  {} -> {}", info.getQualifiedModuleName(), info.getBinaryFile());
            }
        }
        emitModules(allNodes, repoOutput);
        flushAndCheckErrors(allNodes);

        log(INFO, "Finished; terminating compiler");
        return hasSeriousErrors() ? 1 : 0;
    }

    /**
     * The compiler depends on the NakedRef type from the prototype module being available to each
     * ConstantPool in the modules being compiled. This method injects that turtle.
     *
     * @param repoLib  the library repository being used for compilation
     */
    protected void injectNativeTurtle(ModuleRepository repoLib) {
        var repoBuild    = extractBuildRepo(repoLib);
        var moduleTurtle = repoBuild.loadModule(Constants.TURTLE_MODULE);
        if (moduleTurtle != null) {
            try (var ignore = ConstantPool.withPool(moduleTurtle.getConstantPool())) {
                var clzNakedRef  = moduleTurtle.getChild("NakedRef", ClassStructure.class);
                var typeNakedRef = clzNakedRef.getFormalType();

                for (var sModule : repoBuild.getModuleNames()) {
                    var module = repoBuild.loadModule(sModule);
                    module.getConstantPool().setNakedRefType(typeNakedRef);
                }
            }
        }
    }

    /**
     * Link all the AST objects for each module into a single parse tree, and create the outline
     * of the finished FileStructure.
     *
     * @param allNodes  the list of module sources being compiled
     * @param repo      the library repository (with the build repository at the front)
     *
     * @return a map from module name to compiler, one for each module being compiled
     */
    protected Map<String, org.xvm.compiler.Compiler> resolveCompilers(List<Node> allNodes, ModuleRepository repo) {
        final var mapCompilers = new LinkedHashMap<String, org.xvm.compiler.Compiler>();
        final var repoBuild = extractBuildRepo(repo);
        for (var node : allNodes) {
            // Create a module/package/class structure for each dir/file node in the "module tree"
            if (node.type().getCategory().getId() != Id.MODULE) {
                log(ERROR, "File {} doesn't contain a module statement", quoted(node));
                continue;
            }
            final var compiler = new org.xvm.compiler.Compiler(node.type(), node.errs());
            final var struct = compiler.generateInitialFileStructure();
            if (struct == null) {
                continue;
            }
            final var name = struct.getModuleId().getName();
            if (mapCompilers.containsKey(name)) {
                log(ERROR, "Duplicate module name: {}", quoted(name));
                continue;
            }
            // Hold on to the module compiler
            mapCompilers.put(name, compiler);
            // Hold on to the resulting module structure (it will be in the build repository)
            assert repoBuild.loadModule(name) == null;
            try {
                repo.storeModule(struct.getModule());
                assert repoBuild.loadModule(name) != null;
            } catch (IOException e) {
                log(FATAL, e, "I/O exception storing module: {}", name);
                // Error accumulates in m_sevWorst, flushAndCheckErrors() will abort if needed
            }
        }
        return mapCompilers;
    }

    /**
     * Lazily configure the library repository.
     *
     * @return the library repository
     */
    protected ModuleRepository ensureLibraryRepo() {
        if (repoLib == null) {
            log(INFO, "Creating and pre-populating library and build repositories");
            repoLib = configureLibraryRepo(options().getModulePath());
        }
        return repoLib;
    }

    /**
     * Link the modules together based on their declared dependencies.
     *
     * @param compilers  a module compiler for each module
     */
    protected void linkModules(List<org.xvm.compiler.Compiler> compilers, ModuleRepository repo) {
        for (var compiler : compilers) {
            final var idMissing = compiler.linkModules(repo);
            if (idMissing != null) {
                compiler.getErrorListener().log(FATAL, MODULE_MISSING, new String[]{idMissing.getName()}, null);
                return;
            }
        }
    }

    /**
     * Resolve dependencies, including among multiple modules that are being compiled at the same
     * time.
     *
     * @param compilers  a module compiler for each module
     */
    protected static void resolveNames(List<org.xvm.compiler.Compiler> compilers) {
        runCompilerPhase(compilers, org.xvm.compiler.Compiler::resolveNames);
    }

    /**
     * Validation phase, before code generation.
     *
     * @param compilers  a module compiler for each module
     */
    protected static void validateExpressions(List<org.xvm.compiler.Compiler> compilers) {
        runCompilerPhase(compilers, org.xvm.compiler.Compiler::validateExpressions);
    }

    /**
     * Functional interface for compiler phases that take an isLastAttempt flag and return isDone.
     */
    @FunctionalInterface
    private interface CompilerPhase {
        boolean run(org.xvm.compiler.Compiler compiler, boolean isLastAttempt);
    }

    /**
     * Run a compiler phase that may require multiple iterations to complete.
     *
     * @param compilers  the list of compilers to process
     * @param phase      the phase function to run on each compiler (takes compiler and isLastAttempt, returns isDone)
     */
    private static void runCompilerPhase(List<org.xvm.compiler.Compiler> compilers, CompilerPhase phase) {
        int cTriesLeft = 0x3F;
        do {
            boolean fDone = true;
            for (var compiler : compilers) {
                fDone &= phase.run(compiler, cTriesLeft == 1);
                if (compiler.isAbortDesired()) {
                    return;
                }
            }
            if (fDone) {
                return;
            }
        } while (--cTriesLeft > 0);

        // something couldn't get resolved; must be a bug in the compiler
        for (var compiler : compilers) {
            compiler.logRemainingDeferredAsErrors();
        }
    }

    /**
     * After names/dependencies are resolved, generate the actual code.
     *
     * @param compilers  a module compiler for each module
     */
    protected void generateCode(List<org.xvm.compiler.Compiler> compilers) {
        int cTriesLeft = 0x3F;
        do {
            boolean fDone = true;
            for (var compiler : compilers) {
                try {
                    fDone &= compiler.generateCode(cTriesLeft == 1);
                    if (compiler.isAbortDesired()) {
                        return;
                    }
                } catch (Throwable e) {
                    System.err.println("Failed to generate code for " + compiler);
                    e.printStackTrace(System.err);
                    log(ERROR, "Failed to generate code for {} due to exception: {}", compiler, e);
                }
            }
            if (fDone) {
                return;
            }
        } while (--cTriesLeft > 0);

        // something couldn't get resolved; must be a bug in the compiler
        for (var compiler : compilers) {
            compiler.logRemainingDeferredAsErrors();
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean addVersion(ModuleInfo info, Version ver) {
        var fileBin = info.getBinaryFile();
        try {
            var struct = new FileStructure(fileBin);
            struct.getModule().setVersion(ver);
            struct.writeTo(fileBin);
            return true;
        } catch (IOException e) {
            log(ERROR, "Failed to stamp version {} onto file {}", ver, fileBin);
            return false;
        }
    }

    /**
     * Emit the results of compilation.
     *
     * @return 0 for success, non-zero exit code for failure
     */
    protected int emitModules(List<Node> allNodes, ModuleRepository repoOutput) {
        var opts = options();
        var version = opts.getVersion();
        for (var nodeModule : allNodes) {
            var module = (ModuleStructure) nodeModule.type().getComponent();

            assert !module.isFingerprint();
            version.ifPresent(module::setVersion);

            if (repoOutput != null) {
                try {
                    repoOutput.storeModule(module);
                } catch (IOException e) {
                    log(FATAL, e, "I/O exception storing module: {}", module.getName());
                }
                int exitCode = checkErrors("module storage");
                if (exitCode != 0) {
                    return exitCode;
                }
            } else {
                // figure out where to put the resulting module
                var file = nodeModule.file().getParentFile();
                if (file == null) {
                    log(ERROR, "Unable to determine output location for module {} from file: {}", quoted(nodeModule.name()), nodeModule.file());
                    return checkErrors("output location resolution");
                }

                // at this point, we either have a directory or a file to put it in; resolve that to
                // an actual compiled module file name
                if (file.isDirectory()) {
                    var sName = nodeModule.name();
                    if (!opts.isOutputFilenameQualified()) {
                        int ofDot = sName.indexOf('.');
                        if (ofDot > 0) {
                            sName = sName.substring(0, ofDot);
                        }
                    }
                    file = new File(file, sName + ".xtc");
                }

                final var struct = module.getFileStructure();
                try {
                    struct.writeTo(file);
                } catch (IOException e) {
                    log(FATAL, e, "Exception occurred while attempting to write module file {}", quoted(file.getAbsolutePath()));
                    return 1;  // Unreachable - log(FATAL) throws
                }
            }
        }
        return 0;
    }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    protected void log(ErrorList errs) {
        var listErrs = errs.getErrors();
        int cErrs    = listErrs.size();

        if (cErrs > 0) {
            // if there are any COMPILER errors, suppress all VERIFY errors except the first three
            boolean fSuppressVerify = false;
            for (var err : listErrs) {
                if (err.getCode().startsWith("COMPILER")) {
                    fSuppressVerify = true;
                    break;
                }
            }
            int cVerify = 0;
            for (var err : listErrs) {
                if (fSuppressVerify && err.getCode().startsWith("VERIFY") && ++cVerify > 3) {
                    continue;
                }
                log(err.getSeverity(), err.toString());
            }
        }
    }

    @Override
    public String desc() {
        return """
            Ecstasy compiler:

                Converts ".x" files into a compiled ".xtc" Ecstasy module.""";
    }


    @Override
    protected boolean isBadEnoughToPrint(Severity sev) {
        if (options().isVerbose()) {
            return true;
        }
        return switch (strictLevel) {
            case None, Suppressed -> sev.isAtLeast(ERROR);
            case Normal, Stickler -> sev.isAtLeast(WARNING);
        };
    }

    @Override
    protected boolean isBadEnoughToAbort(Severity sev) {
        return sev.compareTo(strictLevel == Strictness.Stickler ? WARNING : ERROR) >= 0;
    }

    @Override
    public boolean isAbortDesired() {
        // Check BOTH Console (tool errors) AND ErrorListener delegate (compiler errors)
        // Use Compiler's strictness-aware abort threshold
        return isBadEnoughToAbort(m_sevWorst) || (m_errors != ErrorListener.BLACKHOLE && m_errors.isAbortDesired());
    }

    // ----- accessors -----------------------------------------------------------------------------

    @SuppressWarnings("unused")
    public List<ModuleInfo> getModuleInfos() {
        return prevModules;
    }

    public ModuleRepository getLibraryRepo() {
        return prevLibs == null ? configureLibraryRepo(options().getModulePath()) : prevLibs;
    }

    @SuppressWarnings("unused")
    public ModuleRepository getOutputRepo() {
        return prevOutput == null ? getLibraryRepo() : prevOutput;
    }

    // ----- options -------------------------------------------------------------------------------

    @Override
    protected void validateOptions() {
        var opts = options();
        // Set strictness level based on options
        //   NOTE: --strict and --nowarn are mutually exclusive (enforced by OptionGroup in LauncherOptions), so we
        //   do not need to check if nowarn and strict are both set and conflicting.
        assert !(opts.isStrict() && opts.isNoWarn()): "Incompatible option groups should be checked already.";
        if (opts.isStrict()) {
            strictLevel = Strictness.Stickler;
        } else if (opts.isNoWarn()) {
            strictLevel = Strictness.Suppressed;
        }

        // Validate the -L path of file(s)/dir(s)
        validateModulePath();

        // Validate the trailing file(s)/dir(s)
        var listInputs = opts.getInputLocations();
        for (int i = 0, c = listInputs.size(); i < c; ++i) {
            File fileOld = listInputs.get(i);
            File fileNew = validateSourceInput(fileOld);
            if (fileNew != fileOld) {
                listInputs.set(i, fileNew);
            }
        }

        // Validate the -o file/dir
        validateModuleOutput(listInputs.size());
    }

    /**
     * Validate that the output location can be used as a destination for .xtc file(s).
     * Uses the output location from options and the number of input modules to determine validity.
     *
     * @param numModules  the number of modules that will be written
     */
    private void validateModuleOutput(final int numModules) {
        options().getOutputLocation().ifPresent(file -> {
            boolean fSingle = isExplicitCompiledFile(file.getName());
            if (fSingle && numModules > 1) {
                log(ERROR, "The single file {} is specified, but multiple modules are expected", file);
                return;
            }

            var optDir = fSingle ? parentOf(file) : Optional.of(file);

            // Check if parent path exists as a file (can't create directory)
            if (fSingle) {
                optDir.filter(File::exists)
                      .filter(dir -> !dir.isDirectory())
                      .ifPresent(_ -> log(ERROR,
                          "The output file is {} but the parent directory cannot be created because a file already exists with the same name",
                          file));
            }

            // Create directory if it doesn't exist
            optDir.filter(dir -> !dir.exists()).ifPresent(dir -> {
                log(INFO, "Creating directory {}", dir);
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            });

            if (file.exists() && !file.isDirectory()) {
                if (!fSingle) {
                    log(WARNING, "File {} does not have the \".xtc\" extension", file);
                }
                if (!file.canWrite()) {
                    log(ERROR, "File {} can not be written to", file);
                }
            } else {
                optDir.filter(dir -> !dir.exists()).ifPresent(dir ->
                    log(ERROR, "Directory {} is missing", dir));
            }
        });
    }

    protected enum Strictness {
        None,
        Suppressed,
        Normal,
        Stickler
    }

    protected Strictness strictLevel = Strictness.Normal;

    protected ModuleRepository   repoLib;
    protected List<ModuleInfo>   prevModules;
    protected ModuleRepository   prevLibs;
    protected ModuleRepository prevOutput;
}

package org.xvm.tool;


import java.io.File;
import java.io.IOException;

import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;

import org.xvm.compiler.Token;

import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.ModuleInfo.Node;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;

import static org.xvm.compiler.Compiler.MODULE_MISSING;
import static org.xvm.tool.ModuleInfo.isExplicitCompiledFile;
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

    protected static final String COMMAND_NAME = "xcc";

    protected static org.xvm.compiler.Compiler[] NO_COMPILERS = new org.xvm.compiler.Compiler[0];

    // ----- constants -----------------------------------------------------------------------------

    protected enum Strictness {
        None,
        Suppressed,
        Normal,
        Stickler
    }

    protected Strictness strictLevel = Strictness.Normal;

    protected ModuleRepository repoLib;
    protected ModuleInfo[]     prevModules;
    protected ModuleRepository prevLibs;
    protected ModuleRepository prevOutput;

    /**
     * Compiler constructor for programmatic use.
     *
     * @param options     pre-configured compiler options
     * @param console     representation of the terminal within which this command is run, or null
     * @param errListener optional ErrorListener to receive errors, or null for no delegation
     */
    public Compiler(final CompilerOptions options, final Console console, final ErrorListener errListener) {
        super(options, console, errListener);
    }

    /**
     *
     * Entry point from the OS. Delegates to Launcher.
     *
     * @param args command line arguments
     */
    static void main(final String[] args) {
        Launcher.main(insertCommand(COMMAND_NAME, args));
    }

    // TODO: Also support a process call with an optional options paramter (and likely run and stuff...)
    @Override
    protected int process() {
        final var opts = options();

        if (opts.showVersion()) {
            showSystemVersion(ensureLibraryRepo());
        }

        log(Severity.INFO, "Selecting compilation targets");

        File[]       resourceDirs = opts.getResourceLocation();
        File         outputLoc    = opts.getOutputLocation();
        ModuleInfo[] aTarget      = selectTargets(opts.getInputLocations(), resourceDirs, outputLoc).toArray(new ModuleInfo[0]);

        prevModules = aTarget;

        int cTargets = aTarget.length;
        if (cTargets == 0) {
            if (opts.showVersion()) {
                return 0;
            }
            displayHelp();
            return 1;
        }

        if (outputLoc != null) {
            outputLoc = resolveFile(outputLoc);
            if (!outputLoc.exists()) {
                if (isExplicitCompiledFile(outputLoc.getName())) {
                    var outputDir = outputLoc.getParentFile();
                    if (outputDir.exists()) {
                        // it needs to be a directory
                        if (!outputDir.isDirectory()) {
                            log(ERROR, "The output file is {} but the parent directory cannot be created because a file already exists with the same name", outputLoc);
                        }
                    }
                }
            }
        }

        var infoByName = new ListMap<String, ModuleInfo>(cTargets);
        for (int i = 0; i < cTargets; ++i) {
            var info    = aTarget[i];
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
                var binDir = binFile.getParentFile();
                if (!binDir.isDirectory() && binDir.exists()) {
                    log(ERROR, "The output file {} cannot be written because its parent directory cannot be created because a file already exists with the same name", binFile);
                }
            }
        }
        checkErrors();

        final boolean fRebuild = opts.isForcedRebuild();
        final Version verStamp = opts.getVersion();
        log(INFO, "Output-path={}, force-rebuild={}", outputLoc, fRebuild);

        final var mapTargets = new ListMap<File, Node>(cTargets);
        var cSystemModules = 0;
        for (final var moduleInfo : aTarget) {
            log(INFO, "Loading and parsing sources for module: {}", moduleInfo.getQualifiedModuleName());
            var node = moduleInfo.getSourceTree(this);
            // short-circuit the compilation of any up-to-date modules
            if (fRebuild || !moduleInfo.isUpToDate()) {
                mapTargets.put(moduleInfo.getSourceFile(), node);
                if (moduleInfo.isSystemModule()) {
                    ++cSystemModules;
                }
            } else if (verStamp != null && !verStamp.equals(moduleInfo.getModuleVersion())) {
                // recompile is not required, but the version stamp needs to be added
                log(INFO, "Stamping version {} onto module: {}", verStamp, moduleInfo.getQualifiedModuleName());
                addVersion(moduleInfo, verStamp);
            }
        }
        checkErrors();

        if (mapTargets.isEmpty()) {
            log(INFO, "All modules are up to date; terminating compiler");
            return 0;
        }
        final var allNodes = mapTargets.values().toArray(new Node[0]);
        flushAndCheckErrors(allNodes);

        // repository setup
        ensureLibraryRepo();
        checkErrors();

        if (cSystemModules == 0) {
            log(INFO, "Pre-loading and linking system libraries");
            prelinkSystemLibraries(repoLib);
        }
        prevLibs = repoLib;
        checkErrors();

        final var repoOutput = new ModuleInfoRepository(infoByName, false);
        prevOutput = repoOutput;
        checkErrors();

        // the code below could be extracted if necessary: compile(allNodes, repoLib, repoOutput);
        log(INFO, "Creating empty modules and populating namespaces");
        final var mapCompilers = populateNamespace(allNodes, repoLib);
        flushAndCheckErrors(allNodes);

        log(INFO, "Resolving names and dependencies");
        final var compilers = mapCompilers.values().toArray(NO_COMPILERS);
        linkModules(compilers, repoLib);
        flushAndCheckErrors(allNodes);

        resolveNames(compilers);
        flushAndCheckErrors(allNodes);

        injectNativeTurtle(repoLib);
        checkErrors();

        log(INFO, "Validating expressions");
        validateExpressions(compilers);
        flushAndCheckErrors(allNodes);

        log(INFO, "Generating code");
        generateCode(compilers);
        flushAndCheckErrors(allNodes);

        if (allNodes.length == 1) {
            log(INFO, "Storing results of compilation: {}", allNodes[0].moduleInfo().getBinaryFile());
        } else {
            log(INFO, "Storing results of compilation:");
            for (final var node : allNodes) {
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
    protected void injectNativeTurtle(final ModuleRepository repoLib) {
        var repoBuild    = extractBuildRepo(repoLib);
        var moduleTurtle = repoBuild.loadModule(Constants.TURTLE_MODULE);
        if (moduleTurtle != null) {
            try (var ignore = ConstantPool.withPool(moduleTurtle.getConstantPool())) {
                var clzNakedRef = (ClassStructure) moduleTurtle.getChild("NakedRef");
                var typeNakedRef = clzNakedRef.getFormalType();

                for (final var sModule : repoBuild.getModuleNames()) {
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
     * @param allNodes  the array of module sources being compiled
     * @param repo      the library repository (with the build repository at the front)
     *
     * @return a map from module name to compiler, one for each module being compiled
     */
    protected Map<String, org.xvm.compiler.Compiler> populateNamespace(final Node[] allNodes, final ModuleRepository repo) {
        final var mapCompilers = new ListMap<String, org.xvm.compiler.Compiler>();
        final var repoBuild = extractBuildRepo(repo);
        for (final var node : allNodes) {
            // Create a module/package/class structure for each dir/file node in the "module tree"
            if (node.type().getCategory().getId() != Token.Id.MODULE) {
                log(ERROR, "File {} doesn't contain a module statement", quoted(node));
                continue;
            }
            final var compiler = new org.xvm.compiler.Compiler(node.type(), node.errs());
            final var struct = compiler.generateInitialFileStructure();
            if (struct == null) {
                return null;
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
            } catch (final IOException e) {
                log(FATAL, e.toString());
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
    protected void linkModules(final org.xvm.compiler.Compiler[] compilers, final ModuleRepository repo) {
        for (final var compiler : compilers) {
            final var idMissing = compiler.linkModules(repo);
            if (idMissing != null) {
                compiler.getErrorListener().log(FATAL, MODULE_MISSING, new String[]{idMissing.getName()}, null);
                return;
            }
        }
    }

    /**
     * Execute a compilation phase across all compilers with retry logic.
     *
     * @param compilers  the compilers to execute the phase on
     * @param phase      the compilation phase to execute
     * @param phaseName  the name of the phase (for logging)
     */
    protected void executeCompilationPhase(final org.xvm.compiler.Compiler[] compilers, final CompilationPhase phase, final String phaseName) {
        int cTriesLeft = 0x3F;
        do {
            boolean fDone = true;
            for (final var compiler : compilers) {
                try {
                    fDone &= phase.execute(compiler, cTriesLeft == 1);
                    if (compiler.isAbortDesired()) {
                        return;
                    }
                } catch (final RuntimeException e) {
                    if (phase.shouldLogException()) {
                        e.printStackTrace(System.err);
                        log(ERROR, "Failed to {} for {} due to exception: {}", phaseName, compiler, e);
                    } else {
                        throw e;
                    }
                }
            }
            if (fDone) {
                return;
            }
        } while (--cTriesLeft > 0);

        // something couldn't get resolved; must be a bug in the compiler
        for (final var compiler : compilers) {
            compiler.logRemainingDeferredAsErrors();
        }
    }

    /**
     * Functional interface for a compilation phase.
     */
    @FunctionalInterface
    protected interface CompilationPhase {
        /**
         * Execute this phase on a compiler.
         *
         * @param compiler  the compiler to execute on
         * @param fForce    whether to force completion (last try)
         *
         * @return true if this phase is complete for this compiler
         */
        boolean execute(org.xvm.compiler.Compiler compiler, boolean fForce);

        /**
         * Whether exceptions during this phase should be logged and continue,
         * or re-thrown.
         *
         * @return true to log and continue, false to re-throw
         */
        default boolean shouldLogException() {
            return false;
        }
    }

    /**
     * Resolve dependencies, including among multiple modules that are being compiled at the same
     * time.
     *
     * @param compilers  a module compiler for each module
     */
    protected void resolveNames(final org.xvm.compiler.Compiler[] compilers) {
        executeCompilationPhase(compilers, org.xvm.compiler.Compiler::resolveNames, "resolve names");
    }

    /**
     * Validation phase, before code generation.
     *
     * @param compilers  a module compiler for each module
     */
    protected void validateExpressions(final org.xvm.compiler.Compiler[] compilers) {
        executeCompilationPhase(compilers, org.xvm.compiler.Compiler::validateExpressions, "validate expressions");
    }

    /**
     * After names/dependencies are resolved, generate the actual code.
     *
     * @param compilers  a module compiler for each module
     */
    protected void generateCode(final org.xvm.compiler.Compiler[] compilers) {
        final var codeGenPhase = new CompilationPhase() {
            @Override
            public boolean execute(final org.xvm.compiler.Compiler compiler, final boolean fForce) {
                return compiler.generateCode(fForce);
            }

            @Override
            public boolean shouldLogException() {
                return true;  // generateCode catches and logs exceptions
            }
        };
        executeCompilationPhase(compilers, codeGenPhase, "generate code");
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean addVersion(final ModuleInfo info, final Version ver) {
        var fileBin = info.getBinaryFile();
        try {
            var struct = new FileStructure(fileBin);
            struct.getModule().setVersion(ver);
            struct.writeTo(fileBin);
            return true;
        } catch (final IOException e) {
            log(ERROR, "Failed to stamp version {} onto file {}", ver, fileBin);
            return false;
        }
    }

    /**
     * Emit the results of compilation.
     */
    protected void emitModules(final Node[] allNodes, final ModuleRepository repoOutput) {
        var opts = options();
        var version = opts.getVersion();
        for (final var nodeModule : allNodes) {
            var module = (ModuleStructure) nodeModule.type().getComponent();

            assert !module.isFingerprint();
            if (version != null) {
                module.setVersion(version);
            }

            if (repoOutput != null) {
                try {
                    repoOutput.storeModule(module);
                } catch (final IOException e) {
                    log(FATAL, e.toString());
                }
            } else {
                // figure out where to put the resulting module
                var file = nodeModule.file().getParentFile();
                if (file == null) {
                    log(ERROR, "Unable to determine output location for module {} from file: {}", quoted(nodeModule.name()), nodeModule.file());
                    continue;
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

                var struct = module.getFileStructure();
                try {
                    struct.writeTo(file);
                } catch (final IOException e) {
                    log(FATAL, "Exception ({}) occurred while attempting to write module file {}", e, quoted(file.getAbsolutePath()));
                }
            }
        }
    }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    protected void log(final ErrorList errs) {
        var listErrs = errs.getErrors();
        int cErrs    = listErrs.size();

        if (cErrs > 0) {
            // if there are any COMPILER errors, suppress all VERIFY errors except the first three
            boolean fSuppressVerify = false;

            for (final var err : listErrs) {
                if (err.getCode().startsWith("COMPILER")) {
                    fSuppressVerify = true;
                    break;
                }
            }

            int cVerify = 0;
            for (final var err : listErrs) {
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
    protected boolean isBadEnoughToPrint(final Severity sev) {
        if (options().verbose()) {
            return true;
        }
        return switch (strictLevel) {
            case None, Suppressed -> sev.compareTo(ERROR) >= 0;
            case Normal, Stickler -> sev.compareTo(WARNING) >= 0;
        };
    }

    @Override
    protected boolean isBadEnoughToAbort(final Severity sev) {
        return sev.compareTo(strictLevel == Strictness.Stickler ? WARNING : ERROR) >= 0;
    }

    // ----- accessors -----------------------------------------------------------------------------

    @SuppressWarnings("unused")
    public ModuleInfo[] getModuleInfos() {
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
        validateModulePath(opts.getModulePath());

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
        validateModuleOutput(opts.getOutputLocation(), listInputs.size() > 1);
    }
}

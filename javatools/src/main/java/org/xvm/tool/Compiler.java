package org.xvm.tool;


import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;

import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.ModuleInfo.Node;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;

import static org.xvm.compiler.Compiler.MODULE_MISSING;
import static org.xvm.tool.ModuleInfo.isExplicitCompiledFile;
import static org.xvm.util.Handy.resolveFile;


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
public class Compiler
        extends Launcher<CompilerOptions> {
    /**
     * Entry point from the OS. Delegates to Launcher.
     *
     * @param asArg command line arguments
     */
    public static void main(String[] asArg) {
        Launcher.main(insertCommand("xcc", asArg));
    }

    /**
     * Programmatic entry point. Delegates to Launcher.
     *
     * @param asArg command line arguments
     * @param errListener ErrorListener to receive errors
     * @return exit code
     */
    public static int launch(String[] asArg, ErrorListener errListener) {
        return Launcher.launch("xcc", asArg, errListener);
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
     * Compiler constructor (simplified, for programmatic use).
     *
     * @param options pre-configured compiler options
     */
    public Compiler(CompilerOptions options) {
        this(options, null, null);
    }

    @Override
    protected int process() {
        final var opts = options();

        if (opts.showVersion()) {
            showSystemVersion(ensureLibraryRepo());
        }

        log(Severity.INFO, "Selecting compilation targets");

        // source tree setup
        File[]       resourceDirs = options.getResourceLocation();
        File         outputLoc    = options.getOutputLocation();
        ModuleInfo[] aTarget      = selectTargets(options.getInputLocations(), resourceDirs, outputLoc).toArray(new ModuleInfo[0]);
        prevModules = aTarget;

        int cTargets = aTarget.length;
        if (cTargets == 0) {
            if (!options.showVersion()) {
                displayHelp();
            }

            displayHelp();
            return 1;
        }

        if (outputLoc != null) {
            outputLoc = resolveFile(outputLoc);
            if (!outputLoc.exists()) {
                if (isExplicitCompiledFile(outputLoc.getName())) {
                    File outputDir = outputLoc.getParentFile();
                    if (outputDir.exists()) {
                        // it needs to be a directory
                        if (!outputDir.isDirectory()) {
                            log(Severity.ERROR, "The output file is " + outputLoc
                                    + " but the parent directory cannot be created because"
                                    + " a file already exists with the same name");
                        }
                    }
                }
            }
        }

        Map<String, ModuleInfo> infoByName = new ListMap<>(cTargets);
        for (int i = 0; i < cTargets; ++i) {
            ModuleInfo info    = aTarget[i];
            String     sModule = info.getQualifiedModuleName();
            File       srcFile = info.getSourceFile();
            File       binFile = info.getBinaryFile();

            log(srcFile == null ? Severity.ERROR : Severity.INFO,
                    "  [" + i + "]=" + srcFile == null ? "<unknown>" : srcFile.getPath());

            if (i == 1 && outputLoc != null && !outputLoc.isDirectory()
                    && isExplicitCompiledFile(outputLoc.getName())) {
                log(Severity.ERROR, "Multiple modules are being compiled, but only one output"
                        + " module file name (" + outputLoc + ") was specified;"
                        + " specify a target directory instead");
            }

            if (srcFile == null || !srcFile.exists()) {
                log(Severity.ERROR, "Could not locate the source for the module " + info.getFileSpec());
            }

            if (sModule == null) {
                log(Severity.ERROR, "Could not determine the module name for " + info.getFileSpec());
            } else {
                infoByName.put(sModule, info);
            }

            if (binFile == null) {
                log(Severity.ERROR, "Could not determine the target location for " + info.getFileSpec()
                        + "; the module project may be missing a \"build\" or \"target\" directory");
            } else {
                File binDir = binFile.getParentFile();
                if (!binDir.isDirectory() && binDir.exists()) {
                    log(Severity.ERROR, "The output file " + binFile
                            + " cannot be written because its parent directory cannot be created"
                            + " because a file already exists with the same name");
                }
            }
        }
        checkErrors();

        boolean fRebuild = options.isForcedRebuild();
        Version verStamp = options.getVersion();
        log(Severity.INFO, "Output-path=" + outputLoc + ", force-rebuild=" + fRebuild);

        Map<File, Node> mapTargets     = new ListMap<>(cTargets);
        int             cSystemModules = 0;
        for (ModuleInfo moduleInfo : aTarget) {
            log(Severity.INFO, "Loading and parsing sources for module: "
                    + moduleInfo.getQualifiedModuleName());
            Node node = moduleInfo.getSourceTree(this);

            // short-circuit the compilation of any up-to-date modules
            if (fRebuild || !moduleInfo.isUpToDate()) {
                mapTargets.put(moduleInfo.getSourceFile(), node);
                if (moduleInfo.isSystemModule()) {
                    ++cSystemModules;
                }
            } else if (verStamp != null && !verStamp.equals(moduleInfo.getModuleVersion())) {
                // recompile is not required, but the version stamp needs to be added
                log(Severity.INFO, "Stamping version " + verStamp
                        + " onto module: " + moduleInfo.getQualifiedModuleName());
                addVersion(moduleInfo, verStamp);
            }
        }
        checkErrors();

        if (mapTargets.isEmpty()) {
            log(Severity.INFO, "All modules are up to date; terminating compiler");
            return 0;
        }

        Node[] allNodes = mapTargets.values().toArray(new Node[0]);
        flushAndCheckErrors(allNodes);

        // repository setup
        ensureLibraryRepo();
        checkErrors();

        if (cSystemModules == 0) {
            log(Severity.INFO, "Pre-loading and linking system libraries");
            prelinkSystemLibraries(repoLib);
        }
        prevLibs = repoLib;
        checkErrors();

        ModuleRepository repoOutput = new ModuleInfoRepository(infoByName, false);
        prevOutput = repoOutput;
        checkErrors();

        // the code below could be extracted if necessary: compile(allNodes, repoLib, repoOutput);
        log(Severity.INFO, "Creating empty modules and populating namespaces");
        Map<String, org.xvm.compiler.Compiler> mapCompilers = populateNamespace(allNodes, repoLib);
        flushAndCheckErrors(allNodes);

        log(Severity.INFO, "Resolving names and dependencies");
        org.xvm.compiler.Compiler[] compilers = mapCompilers.values().toArray(NO_COMPILERS);
        linkModules(compilers, repoLib);
        flushAndCheckErrors(allNodes);

        resolveNames(compilers);
        flushAndCheckErrors(allNodes);

        injectNativeTurtle(repoLib);
        checkErrors();

        log(Severity.INFO, "Validating expressions");
        validateExpressions(compilers);
        flushAndCheckErrors(allNodes);

        log(Severity.INFO, "Generating code");
        generateCode(compilers);
        flushAndCheckErrors(allNodes);

        if (allNodes.length == 1) {
            log(Severity.INFO, "Storing results of compilation: " + allNodes[0].moduleInfo().getBinaryFile());
        } else {
            log(Severity.INFO, "Storing results of compilation:");
            for (Node node : allNodes) {
                ModuleInfo info = node.moduleInfo();
                log(Severity.INFO, "  " + info.getQualifiedModuleName() + " -> " + info.getBinaryFile());
            }
        }
        emitModules(allNodes, repoOutput);
        flushAndCheckErrors(allNodes);

        log(Severity.INFO, "Finished; terminating compiler");
        return hasSeriousErrors() ? 1 : 0;
    }

    /**
     * The compiler depends on the NakedRef type from the prototype module being available to each
     * ConstantPool in the modules being compiled. This method injects that turtle.
     *
     * @param repoLib  the library repository being used for compilation
     */
    protected void injectNativeTurtle(ModuleRepository repoLib) {
        ModuleRepository repoBuild    = extractBuildRepo(repoLib);
        ModuleStructure  moduleTurtle = repoBuild.loadModule(Constants.TURTLE_MODULE);
        if (moduleTurtle != null) {
            try (var ignore = ConstantPool.withPool(moduleTurtle.getConstantPool())) {
                ClassStructure clzNakedRef  = (ClassStructure) moduleTurtle.getChild("NakedRef");
                TypeConstant   typeNakedRef = clzNakedRef.getFormalType();

                for (String sModule : repoBuild.getModuleNames()) {
                    ModuleStructure module = repoBuild.loadModule(sModule);
                    module.getConstantPool().setNakedRefType(typeNakedRef);
                }
            }
        }
    }

    /**
     * Link all of the AST objects for each module into a single parse tree, and create the outline
     * of the finished FileStructure.
     *
     * @param allNodes  the array of module sources being compiled
     * @param repo      the library repository (with the build repository at the front)
     *
     * @return a map from module name to compiler, one for each module being compiled
     */
    protected Map<String, org.xvm.compiler.Compiler> populateNamespace(Node[] allNodes, ModuleRepository repo) {
        Map<String, org.xvm.compiler.Compiler> mapCompilers = new ListMap<>();

        ModuleRepository repoBuild = extractBuildRepo(repo);
        for (Node node : allNodes) {
            // create a module/package/class structure for each dir/file node in the "module tree"
            if (node.type().getCategory().getId() != Token.Id.MODULE) {
                log(Severity.ERROR, "File \"" + node + "\" doesn't contain a module statement");
                continue;
            }

            var           compiler = new org.xvm.compiler.Compiler(node.type(), node.errs());
            FileStructure struct   = compiler.generateInitialFileStructure();
            if (struct == null) {
                return null;
            }

            String name = struct.getModuleId().getName();
            if (mapCompilers.containsKey(name)) {
                log(Severity.ERROR, "Duplicate module name: \"" + name + "\"");
                continue;
            }

            // hold on to the module compiler
            mapCompilers.put(name, compiler);

            // hold on to the resulting module structure (it will be in the build repository)
            assert repoBuild.loadModule(name) == null;
            try {
                repo.storeModule(struct.getModule());
                assert repoBuild.loadModule(name) != null;
            } catch (IOException e) {
                log(Severity.FATAL, e.toString());
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
            log(Severity.INFO, "Creating and pre-populating library and build repositories");
            repoLib = configureLibraryRepo(m_options.getModulePath());
        }
        return repoLib;
    }

    /**
     * Link the modules together based on their declared dependencies.
     *
     * @param compilers  a module compiler for each module
     */
    protected void linkModules(org.xvm.compiler.Compiler[] compilers, ModuleRepository repo) {
        for (var compiler : compilers) {
            ModuleConstant idMissing = compiler.linkModules(repo);
            if (idMissing != null) {
                compiler.getErrorListener().log(Severity.FATAL,
                        MODULE_MISSING, new String[]{idMissing.getName()}, null);
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
    protected void resolveNames(org.xvm.compiler.Compiler[] compilers) {
        int cTriesLeft = 0x3F;
        do {
            boolean fDone = true;
            for (var compiler : compilers) {
                fDone &= compiler.resolveNames(cTriesLeft == 1);

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
     * Validation phase, before code generation.
     *
     * @param compilers  a module compiler for each module
     */
    protected void validateExpressions(org.xvm.compiler.Compiler[] compilers) {
        int cTriesLeft = 0x3F;
        do {
            boolean fDone = true;
            for (var compiler : compilers) {
                fDone &= compiler.validateExpressions(cTriesLeft == 1);

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
    protected void generateCode(org.xvm.compiler.Compiler[] compilers) {
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

                    log(Severity.ERROR, "Failed to generate code for " + compiler
                            + " due to exception: " + e);
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

    protected boolean addVersion(ModuleInfo info, Version ver) {
        File fileBin = info.getBinaryFile();
        try {
            FileStructure struct = new FileStructure(fileBin);
            struct.getModule().setVersion(ver);
            struct.writeTo(fileBin);
            return true;
        } catch (IOException e) {
            log(Severity.ERROR, "Failed to stamp version " + ver + " onto file " + fileBin);
            return false;
        }
    }

    /**
     * Emit the results of compilation.
     */
    protected void emitModules(Node[] allNodes, ModuleRepository repoOutput) {
        CompilerOptions options = m_options;
        Version version = options.getVersion();
        for (Node nodeModule : allNodes) {
            ModuleStructure module = (ModuleStructure) nodeModule.type().getComponent();

            assert !module.isFingerprint();
            if (version != null) {
                module.setVersion(version);
            }

            if (repoOutput != null) {
                try {
                    repoOutput.storeModule(module);
                } catch (IOException e) {
                    log(Severity.FATAL, e.toString());
                }
            } else {
                // figure out where to put the resulting module
                File file = nodeModule.file().getParentFile();
                if (file == null) {
                    log(Severity.ERROR, "Unable to determine output location for module \""
                            + nodeModule.name() + "\" from file :" + nodeModule.file());
                    continue;
                }

                // at this point, we either have a directory or a file to put it in; resolve that to
                // an actual compiled module file name
                if (file.isDirectory()) {
                    String sName = nodeModule.name();
                    if (!options.isOutputFilenameQualified()) {
                        int ofDot = sName.indexOf('.');
                        if (ofDot > 0) {
                            sName = sName.substring(0, ofDot);
                        }
                    }
                    file = new File(file, sName + ".xtc");
                }

                FileStructure struct = module.getFileStructure();
                try {
                    struct.writeTo(file);
                } catch (IOException e) {
                    log(Severity.FATAL, "Exception (" + e
                            + ") occurred while attempting to write module file \""
                            + file.getAbsolutePath() + "\"");
                }
            }
        }
    }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    protected void log(ErrorList errs) {
        List<ErrorInfo> listErrs = errs.getErrors();
        int             cErrs    = listErrs.size();

        if (cErrs > 0) {
            // if there are any COMPILER errors, suppress all VERIFY errors except the first three
            boolean fSuppressVerify = false;

            for (ErrorInfo err : listErrs) {
                if (err.getCode().startsWith("COMPILER")) {
                    fSuppressVerify = true;
                    break;
                }
            }

            int cVerify = 0;
            for (ErrorInfo err : listErrs) {
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

            Converts ".x" files into a compiled ".xtc" Ecstasy module.

            Usage:

                xcc <options> <filename>.x ...""";
    }

    @Override
    protected boolean isBadEnoughToPrint(Severity sev) {
        if (m_options.verbose()) {
            return true;
        }

        return switch (strictLevel) {
            case None, Suppressed -> sev.compareTo(Severity.ERROR) >= 0;
            case Normal, Stickler -> sev.compareTo(Severity.WARNING) >= 0;
        };
    }

    @Override
    protected boolean isBadEnoughToAbort(Severity sev) {
        Severity limit = strictLevel == Strictness.Stickler ? Severity.WARNING : Severity.ERROR;
        return sev.compareTo(limit) >= 0;
    }


    // ----- accessors -----------------------------------------------------------------------------

    public ModuleInfo[] getModuleInfos() {
        return prevModules;
    }

    public ModuleRepository getLibraryRepo() {
        return prevLibs == null
                ? configureLibraryRepo(m_options.getModulePath())
                : prevLibs;
    }

    public ModuleRepository getOutputRepo() {
        return prevOutput == null
                ? getLibraryRepo()
                : prevOutput;
    }


    // ----- options -------------------------------------------------------------------------------

    @Override
    protected void validateOptions() {
        CompilerOptions opts = m_options;

        // Set strictness level based on options
        if (opts.isStrict()) {
            strictLevel = Strictness.Stickler;
            if (opts.isNoWarn()) {
                log(Severity.ERROR, "Conflicting options specified: \"--strict\" and \"--nowarn\"");
            }
        } else if (opts.isNoWarn()) {
            strictLevel = Strictness.Suppressed;
        }

        // Validate the -L path of file(s)/dir(s)
        validateModulePath(opts.getModulePath());

        // Validate the trailing file(s)/dir(s)
        List<File> listInputs = opts.getInputLocations();
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


    // ----- constants -----------------------------------------------------------------------------

    protected enum Strictness {None, Suppressed, Normal, Stickler}

    protected static org.xvm.compiler.Compiler[] NO_COMPILERS = new org.xvm.compiler.Compiler[0];


    // ----- fields --------------------------------------------------------------------------------

    protected Strictness strictLevel = Strictness.Normal;

    protected ModuleRepository repoLib;
    protected ModuleInfo[]     prevModules;
    protected ModuleRepository prevLibs;
    protected ModuleRepository prevOutput;
}

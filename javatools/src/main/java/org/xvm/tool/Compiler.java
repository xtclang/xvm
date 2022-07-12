package org.xvm.tool;


import java.io.File;
import java.io.IOException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;


/**
 * This is the command-line Ecstasy compiler.
 *
 * <p/>Find the root of the module containing the code in the current directory, and compile it, placing
 * the result in the default location:
 *
 * <p/>{@code  xtc}
 *
 * <p/>Compile the specified module, placing the result in the default location:
 *
 * <p/>{@code  xtc ./path/to/module_name.x}
 *
 * <p/>Compile the module that the specified file belongs to:
 *
 * <p/>{@code  xtc MyClass.x}
 *
 * <p/>The location for the resulting {@code .xtc} file follows the following rules:
 * <ul>
 * <li>If the "-o" option is specified, then the corresponding location is used for the output;</li>
 * <li>If the module is in a single file format, such as "MyModule.x", then the output is in the
 *     same-named file with the ".xtc" extension, such as "MyModule.xtc".</li>
 * <li>If the module is in a directory format, then the resulting ".xtc" file is placed in the
 *     <i>parent</i> directory of the directory containing the module. For example, if the module
 *     file is located at "app/main/src/App.x", and the module name is "App", then the resulting
 *     ".xtc" file is written to "app/main/App.xtc".</li>
 * </ul>
 *
 * <p/>The location of the resulting {@code .xtc} file can be specified by using the {@code -o} option;
 * for example:
 *
 * <p/>{@code  xtc -o ~/modules/}
 *
 * <p/>The version of the resulting module can be specified by using the {@code -version} option;
 * for example:
 *
 * <p/>{@code  xtc -version 0.4-alpha}
 *
 * <p/>In addition to built-in Ecstasy modules and modules located in the Ecstasy runtime library,
 * it is possible to provide a search path for modules that will be used by the compiler. The search
 * path can contain directories and/or ".xtc" files:
 *
 * <p/>{@code  xtc -L ~/modules/:../build/:Utils.xtc}
 *
 * <p/>Other command line options:
 * <ul>
 * <li>{@code -rebuild} - force rebuild, even if the build appears to be up-to-date</li>
 * <li>{@code -qualify} - use fully qualified module names as the basis for output file names</li>
 * <li>{@code -nosrc} - (not implemented) do not include source code in the compiled module</li>
 * <li>{@code -nodbg} - (not implemented) do not include debugging information in the compiled module</li>
 * <li>{@code -nodoc} - (not implemented) do not include documentation in the compiled module</li>
 * <li>{@code -strict} - convert warnings to errors</li>
 * <li>{@code -nowarn} - suppress warnings</li>
 * <li>{@code -verbose} - provide information about the work being done by the compilation process</li>
 * </ul>
 */
public class Compiler
        extends Launcher
    {
    /**
     * Entry point from the OS.
     *
     * @param asArg command line arguments
     */
    public static void main(String[] asArg)
        {
        new Compiler(asArg).run();
        }

    /**
     * Compiler constructor.
     *
     * @param asArg command line arguments
     */
    public Compiler(String[] asArg)
        {
        super(asArg);
        }

    @Override
    protected void process()
        {
        // source tree setup
        log(Severity.INFO, "Selecting compilation targets");
        List<File> listTargets = selectTargets(options().getInputLocations());
        for (int i = 0, c = listTargets.size(); i < c; ++i)
            {
            log(Severity.INFO, "  [" + i + "]=" + listTargets.get(i));
            }
        checkErrors();

        File    fileOutput = resolveOptionalLocation(options().getOutputLocation());
        boolean fRebuild   = options().isForcedRebuild();
        log(Severity.INFO, "Output-path=" + fileOutput + ", force-rebuild=" + fRebuild);

        Map<File, Node> mapTargets     = new ListMap<>(listTargets.size());
        int             cSystemModules = 0;
        for (File fileModule : listTargets)
            {
            log(Severity.INFO, "Loading and parsing sources for module: " + fileModule);
            Node node = loadSourceTree(fileModule, Stage.Linked);

            // short-circuit the compilation of any up-to-date modules
            if (fRebuild || !moduleUpToDate(node, fileOutput))
                {
                mapTargets.put(fileModule, node);
                if (isSystemModule(node))
                    {
                    ++cSystemModules;
                    }
                }
            }

        if (mapTargets.isEmpty())
            {
            log(Severity.INFO, "All modules are up to date; terminating compiler");
            return;
            }

        Node[] allNodes = mapTargets.values().toArray(new Node[0]);
        flushAndCheckErrors(allNodes);

        // repository setup
        log(Severity.INFO, "Creating and pre-populating library and build repositories");
        ModuleRepository repoLib = configureLibraryRepo(options().getModulePath());
        checkErrors();

        if (cSystemModules == 0)
            {
            log(Severity.INFO, "Pre-loading and linking system libraries");
            prelinkSystemLibraries(repoLib);
            }
        checkErrors();

        ModuleRepository repoOutput = configureResultRepo(fileOutput);
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

        log(Severity.INFO, "Storing results of compilation");
        emitModules(allNodes, repoOutput);
        flushAndCheckErrors(allNodes);

        log(Severity.INFO, "Finished; terminating compiler");
        }

    /**
     * The compiler depends on the NakedRef type from the prototype module being available to each
     * ConstantPool in the modules being compiled. This method injects that turtle.
     *
     * @param repoLib  the library repository being used for compilation
     */
    protected void injectNativeTurtle(ModuleRepository repoLib)
        {
        ModuleRepository repoBuild    = extractBuildRepo(repoLib);
        ModuleStructure  moduleTurtle = repoBuild.loadModule(Constants.TURTLE_MODULE);
        if (moduleTurtle != null)
            {
            try (var ignore = ConstantPool.withPool(moduleTurtle.getConstantPool()))
                {
                ClassStructure clzNakedRef  = (ClassStructure) moduleTurtle.getChild("NakedRef");
                TypeConstant   typeNakedRef = clzNakedRef.getFormalType();

                for (String sModule : repoBuild.getModuleNames())
                    {
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
    protected Map<String, org.xvm.compiler.Compiler> populateNamespace(Node[] allNodes, ModuleRepository repo)
        {
        Map<String, org.xvm.compiler.Compiler> mapCompilers = new ListMap<>();

        ModuleRepository repoBuild = extractBuildRepo(repo);
        for (Node node : allNodes)
            {
            String name = node.name();
            if (mapCompilers.containsKey(name))
                {
                log(Severity.ERROR, "Duplicate module name: \"" + name + "\"");
                continue;
                }

            // create a module/package/class structure for each dir/file node in the "module tree"
            var           compiler = new org.xvm.compiler.Compiler(node.type(), node.errs());
            FileStructure struct   = compiler.generateInitialFileStructure();
            assert struct != null;

            // hold on to the module compiler
            mapCompilers.put(name, compiler);

            // hold on to the resulting module structure (it will be in the build repository)
            assert repoBuild.loadModule(name) == null;
            try
                {
                repo.storeModule(struct.getModule());
                assert repoBuild.loadModule(name) != null;
                }
            catch (IOException e)
                {
                log(Severity.FATAL, e.toString());
                }
            }

        return mapCompilers;
        }

    /**
     * Link the modules together based on their declared dependencies.
     *
     * @param compilers  a module compiler for each module
     */
    protected void linkModules(org.xvm.compiler.Compiler[] compilers, ModuleRepository repo)
        {
        for (var compiler : compilers)
            {
            String sMissing = compiler.linkModules(repo);
            if (sMissing != null)
                {
                compiler.getErrorListener().log(Severity.FATAL,
                        org.xvm.compiler.Compiler.MODULE_MISSING, new String[]{sMissing}, null);
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
    protected void resolveNames(org.xvm.compiler.Compiler[] compilers)
        {
        int cTries = 0;
        do
            {
            boolean fDone = true;
            for (var compiler : compilers)
                {
                fDone &= compiler.resolveNames();

                if (compiler.isAbortDesired())
                    {
                    return;
                    }
                }
            if (fDone)
                {
                return;
                }
            }
        while (++cTries < 0x3F);

        // something couldn't get resolved; must be a bug in the compiler
        for (var compiler : compilers)
            {
            compiler.logRemainingDeferredAsErrors();
            }
        }

    /**
     * Validation phase, before code generation.
     *
     * @param compilers  a module compiler for each module
     */
    protected void validateExpressions(org.xvm.compiler.Compiler[] compilers)
        {
        int cTries = 0;
        do
            {
            boolean fDone = true;
            for (var compiler : compilers)
                {
                fDone &= compiler.validateExpressions();

                if (compiler.isAbortDesired())
                    {
                    return;
                    }
                }
            if (fDone)
                {
                return;
                }
            }
        while (++cTries < 0x3F);

        // something couldn't get resolved; must be a bug in the compiler
        for (var compiler : compilers)
            {
            compiler.logRemainingDeferredAsErrors();
            }
        }

    /**
     * After names/dependencies are resolved, generate the actual code.
     *
     * @param compilers  a module compiler for each module
     */
    protected void generateCode(org.xvm.compiler.Compiler[] compilers)
        {
        int cTries = 0;
        do
            {
            boolean fDone = true;
            for (var compiler : compilers)
                {
                try
                    {
                    fDone &= compiler.generateCode();

                    if (compiler.isAbortDesired())
                        {
                        return;
                        }
                    }
                catch (RuntimeException e)
                    {
                    System.err.println("Failed to generate code for " + compiler);
                    e.printStackTrace();

                    log(Severity.ERROR, "Failed to generate code for " + compiler
                            + " due to exception: " + e);
                    }
                }
            if (fDone)
                {
                return;
                }
            }
        while (++cTries < 0x3F);

        // something couldn't get resolved; must be a bug in the compiler
        for (var compiler : compilers)
            {
            compiler.logRemainingDeferredAsErrors();
            }
        }

    /**
     * Emit the results of compilation.
     */
    protected void emitModules(Node[] allNodes, ModuleRepository repoOutput)
        {
        Version version = options().getVersion();
        for (Node nodeModule : allNodes)
            {
            ModuleStructure module = (ModuleStructure) nodeModule.type().getComponent();

            assert !module.isFingerprint();
            if (version != null)
                {
                module.setVersion(version);
                }

            if (repoOutput != null)
                {
                try
                    {
                    repoOutput.storeModule(module);
                    }
                catch (IOException e)
                    {
                    log(Severity.FATAL, e.toString());
                    }
                }
            else
                {
                // figure out where to put the resulting module
                File file = nodeModule.file().getParentFile();
                if (file == null)
                    {
                    log(Severity.ERROR, "Unable to determine output location for module \""
                            + nodeModule.name() + "\" from file :" + nodeModule.file());
                    continue;
                    }

                // at this point, we either have a directory or a file to put it in; resolve that to
                // an actual compiled module file name
                if (file.isDirectory())
                    {
                    String sName = nodeModule.name();
                    if (!options().isOutputFilenameQualified())
                        {
                        int ofDot = sName.indexOf('.');
                        if (ofDot > 0)
                            {
                            sName = sName.substring(0, ofDot);
                            }
                        }
                    file = new File(file, sName + ".xtc");
                    }

                FileStructure struct = module.getFileStructure();
                try
                    {
                    struct.writeTo(file);
                    }
                catch (IOException e)
                    {
                    log(Severity.FATAL, "Exception (" + e
                            + ") occurred while attempting to write module file \""
                            + file.getAbsolutePath() + "\"");
                    }
                }
            }
        }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    protected void log(ErrorList errs)
        {
        List<ErrorInfo> listErrs = errs.getErrors();
        int             cErrs    = listErrs.size();

        if (cErrs > 0)
            {
            // if there are any COMPILER errors, suppress all VERIFY errors except the first three
            boolean fSuppressVerify = false;

            for (ErrorInfo err : listErrs)
                {
                if (err.getCode().startsWith("COMPILER"))
                    {
                    fSuppressVerify = true;
                    break;
                    }
                }

            int cVerify = 0;
            for (ErrorInfo err : listErrs)
                {
                if (fSuppressVerify && err.getCode().startsWith("VERIFY") && ++cVerify > 3)
                    {
                    continue;
                    }
                log(err.getSeverity(), err.toString());
                }
            }
        }

    @Override
    public String desc()
        {
        return """
            Ecstasy compiler:

            Converts ".x" files into a compiled ".xtc" Ecstasy module.

            Usage:

                xtc <options> <filename>.x ...""";
        }


    // ----- options -------------------------------------------------------------------------------

    @Override
    public Options options()
        {
        return (Options) super.options();
        }

    @Override
    protected Options instantiateOptions()
        {
        return new Options();
        }

    /**
     * Compiler command-line options implementation.
     */
    public class Options
        extends Launcher.Options
        {
        /**
         * Construct the Compiler Options.
         */
        public Options()
            {
            super();

            addOption("rebuild", Form.Name,   false, "Force rebuild");
            addOption("strict",  Form.Name,   false, "Treat warnings as errors");
            addOption("nowarn",  Form.Name,   false, "Suppress all warnings");
            addOption("L",       Form.Repo,   true , "Module path; a \"" + File.pathSeparator
                                                 + "\"-delimited list of file and/or directory names");
            addOption("o",       Form.File,   false, "File or directory to write output to");
            addOption("qualify", Form.Name,   false, "Use full module name for the output file name");
            addOption("version", Form.String, false, "Use full module name for the output file name");
            addOption(Trailing,  Form.File,   true , "Source file name(s) and/or module location(s) to"
                                                 + " compile");
            }

        /**
         * @return the list of files in the module path (empty list if none specified)
         */
        public List<File> getModulePath()
            {
            List<File> path = (List<File>) values().get("L");
            return path == null
                    ? Collections.EMPTY_LIST
                    : path;
            }

        /**
         * @return the input locations to use (files and/or directories), or an empty list if none
         *         specified
         */
        public List<File> getInputLocations()
            {
            List<File> list = (List<File>) values().get(Trailing);
            return list == null
                    ? Collections.EMPTY_LIST
                    : list;
            }

        /**
         * @return the output location to use (a file or directory), or null if none specified
         */
        public File getOutputLocation()
            {
            return (File) values().get("o");
            }

        /**
         * @return the version, or null if none specified
         */
        public Version getVersion()
            {
            String sVersion = (String) values().get("version");
            return sVersion == null ? null : new Version(sVersion);
            }

        /**
         * @return true if "fully qualified module name in output file name" option is set
         */
        public boolean isOutputFilenameQualified()
            {
            return specified("qualify");
            }

        /**
         * @return true if "force rebuild" option is set
         */
        public boolean isForcedRebuild()
            {
            return specified("rebuild");
            }

        @Override
        public void validate()
            {
            super.validate();

            if (specified("strict"))
                {
                strictLevel = Strictness.Stickler;
                if (specified("nowarn"))
                    {
                    log(Severity.ERROR, "Conflicting options specified: \"-strict\" and \"-nowarn\"");
                    }
                }
            else if (specified("nowarn"))
                {
                strictLevel = Strictness.Suppressed;
                }

            // validate the -L path of file(s)/dir(s)
            validateModulePath(getModulePath());

            // validate the trailing file(s)/dir(s)
            List<File> listInputs = getInputLocations();
            for (int i = 0, c = listInputs.size(); i < c; ++i)
                {
                File fileOld = listInputs.get(i);
                File fileNew = validateSourceInput(fileOld);
                if (fileNew != fileOld)
                    {
                    listInputs.set(i, fileNew);
                    }
                }

            // validate the -o file/dir
            validateModuleOutput(getOutputLocation(), listInputs.size() > 1);
            }

        @Override
        boolean isBadEnoughToPrint(Severity sev)
            {
            if (isVerbose())
                {
                return true;
                }

            return switch (strictLevel)
                {
                case None, Suppressed -> sev.compareTo(Severity.ERROR) >= 0;
                case Normal, Stickler -> sev.compareTo(Severity.WARNING) >= 0;
                };

            }

        @Override
        boolean isBadEnoughToAbort(Severity sev)
            {
            Severity limit = strictLevel == Strictness.Stickler ? Severity.WARNING : Severity.ERROR;
            return sev.compareTo(limit) >= 0;
            }
        }


    // ----- constants -----------------------------------------------------------------------------

    protected enum Strictness {None, Suppressed, Normal, Stickler}

    protected static org.xvm.compiler.Compiler[] NO_COMPILERS = new org.xvm.compiler.Compiler[0];


    // ----- fields --------------------------------------------------------------------------------

    protected Strictness strictLevel = Strictness.Normal;
    }
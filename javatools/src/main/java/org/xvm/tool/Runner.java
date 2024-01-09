package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.text.xString;

import org.xvm.util.Handy;
import org.xvm.util.ListSet;
import org.xvm.util.Severity;


import static org.xvm.util.Handy.checkReadable;
import static org.xvm.util.Handy.quotedString;


/**
 * The "execute" command:
 *
 *  java org.xvm.tool.Runner [-L repo(s)] [-M method_name] app.xtc [argv]
 *
 * where the default method is "run" with no arguments.
 */
public class Runner
        extends Launcher
    {
    /**
     * Entry point from the OS.
     *
     * @param asArg command line arguments
     */
    public static void main(String[] asArg)
        {
        new Runner(asArg).run();
        }

    /**
     * Runner constructor.
     *
     * @param asArg command line arguments
     */
    public Runner(String[] asArg)
        {
        this(asArg, null);
        }

    /**
     * Runner constructor.
     *
     * @param asArg    command line arguments
     * @param console  representation of the terminal within which this command is run
     */
    public Runner(String[] asArg, Console console)
        {
        super(asArg, console);
        }

    @Override
    protected void process()
        {
        // repository setup
        ModuleRepository repo = configureLibraryRepo(options().getModulePath());
        checkErrors();

        boolean fShowVer = options().isShowVersion();
        if (fShowVer)
            {
            showSystemVersion(repo);
            }

        final File fileSpec = options().getTarget();
        if (fileSpec == null)
            {
            if (!fShowVer)
                {
                displayHelp();
                }
            return;
            }

        ModuleInfo info = null;
        try
            {
            info = new ModuleInfo(fileSpec);
            }
        catch (RuntimeException e)
            {
            log(Severity.ERROR, "Failed to identify the module for: " + fileSpec + " (" + e + ")");
            }
        checkErrors();

        File            fileBin   = info.getBinaryFile();
        boolean         binExists = fileBin != null && fileBin.exists();
        ModuleStructure module    = null;
        boolean         fCompile  = false;
        if (!binExists)
            {
            String qualName = info.getQualifiedModuleName();
            module = repo.loadModule(qualName);
            if (module == null)
                {
                File fileSrc = info.getSourceFile();
                if (fileSrc != null && fileSrc.exists())
                    {
                    log(Severity.INFO, "The compiled module \"" + info.getQualifiedModuleName()
                            + "\" is missing; attempting to compile it from " + info.getSourceFile() + " ....");
                    fCompile = true;
                    }
                else
                    {
                    Set<String> possibles = null;
                    if (qualName.indexOf('.') < 0)
                        {
                        // the qualified name wasn't qualified; that may have been user input error;
                        // find all of the names that they may have meant to type
                        for (String name : repo.getModuleNames())
                            {
                            int ofDot = name.indexOf('.');
                            if (ofDot > 0 && name.substring(0, ofDot).equals(qualName))
                                {
                                if (possibles == null)
                                    {
                                    possibles = new ListSet<>();
                                    }
                                possibles.add(name);
                                }
                            }
                        }
                    if (possibles == null)
                        {
                        log(Severity.ERROR, "Failed to locate the module for: " + fileSpec);
                        }
                    else if (possibles.size() == 1)
                        {
                        log(Severity.ERROR, "Unable to locate the module for " + fileSpec
                                + "; did you mean " + quotedString(possibles.iterator().next()) + '?');
                        }
                    else
                        {
                        StringBuilder buf = new StringBuilder();
                        for (String name : possibles)
                            {
                            buf.append(", ")
                               .append(quotedString(name));
                            }
                        log(Severity.ERROR, "Unable to locate the module for " + fileSpec
                                + "; did you mean one of: " + buf.substring(2) + '?');
                        }
                    }
                }
            }
        else if (info.getSourceFile() != null && info.getSourceFile().exists() && !info.isUpToDate())
            {
            log(Severity.INFO, "The compiled module \"" + info.getQualifiedModuleName()
                    + "\" is out-of-date; recompiling ....");
            File fileBak = new File(fileBin.getParentFile(), fileBin.getName() + ".old");
            if (fileBak.exists() && !fileBak.delete())
                {
                log(Severity.ERROR, "Failed to delete the previously-backed-up module: " + fileBak);
                }
            else if (!fileBin.renameTo(fileBak))
                {
                log(Severity.ERROR, "Failed to back up the out-of-date module file: " + fileBin);
                }
            else
                {
                fCompile = true;
                }
            }
        checkErrors();

        if (fCompile)
            {
            String[] asRunnerArgs   = m_asArgs;
            int      cRunnerArgs    = asRunnerArgs.length;
            String[] asCompilerArgs = asRunnerArgs;
            int      cCompilerArgs  = cRunnerArgs;
            boolean  fTargetFound   = false;
            do
                {
                try
                    {
                    if (fileSpec.equals(new File(asCompilerArgs[cCompilerArgs-1])))
                        {
                        fTargetFound = true;
                        }
                    else
                        {
                        --cCompilerArgs;
                        }
                    }
                catch (Exception ignore) {}
                }
            while (!fTargetFound && cCompilerArgs > 0);
            assert fTargetFound && cCompilerArgs > 0;

            if (cCompilerArgs < cRunnerArgs)
                {
                asCompilerArgs = new String[cCompilerArgs];
                System.arraycopy(asRunnerArgs, 0, asCompilerArgs, 0, cCompilerArgs);
                }

            new Compiler(asCompilerArgs, m_console).run();
            info      = new ModuleInfo(fileSpec);
            fileBin   = info.getBinaryFile();
            binExists = fileBin != null && fileBin.exists();
            }

        // check if the compiled module file name was specified
        if (module == null && binExists)
            {
            if (checkReadable(fileBin))
                {
                try
                    {
                    try (FileInputStream in = new FileInputStream(fileBin))
                        {
                        FileStructure struct = new FileStructure(in);
                        module = struct.getModule();
                        }
                    }
                catch (IOException e)
                    {
                    log(Severity.FATAL, "I/O exception (" + e + ") reading module file: " + fileBin);
                    abort(true);
                    }
                }
            }

        if (module == null)
            {
            log(Severity.ERROR, "Missing module  for " + fileSpec);
            abort(true);
            }
        else
            {
            try
                {
                repo.storeModule(module);
                }
            catch (IOException e)
                {
                log(Severity.FATAL, "I/O exception (" + e + ") storing module file: " + fileSpec);
                abort(true);
                }
            checkErrors();
            }

        if (fShowVer)
            {
            Version ver   = info.getModuleVersion();
            String  sVer  = ver == null ? "<none>" : ver.toString();
            String  sName = info.isModuleNameQualified()
                    ? info.getQualifiedModuleName()
                    : quotedString(info.getSimpleModuleName());
            out(sName + " version " + sVer);
            }

        log(Severity.INFO, "Executing " + info.getQualifiedModuleName() + " from " + info.getBinaryFile());
        try
            {
            Connector connector = new Connector(repo);
            connector.loadModule(module.getName());

            connector.start();

            ConstantPool pool = connector.getConstantPool();
            try (var ignore = ConstantPool.withPool(pool))
                {
                String sMethod = options().getMethodName();
                Set<MethodConstant> setMethods = connector.getContainer().findMethods(sMethod);
                if (setMethods.size() != 1)
                    {
                    if (setMethods.isEmpty())
                        {
                        log(Severity.ERROR, "Missing method \"" + sMethod + "\" in module " +
                                info.getQualifiedModuleName());
                        }
                    else
                        {
                        log(Severity.ERROR, "Ambiguous method \"" + sMethod + "\" in module " +
                                info.getQualifiedModuleName());
                        }
                    abort(true);
                    }

                String[]        asArg       = options().getMethodArgs();
                ObjectHandle[]  ahArg       = Utils.OBJECTS_NONE;
                MethodStructure method      = (MethodStructure) setMethods.iterator().next().getComponent();
                TypeConstant    typeStrings = pool.ensureArrayType(pool.typeString());

                switch (method.getRequiredParamCount())
                    {
                    case 0:
                        if (asArg != null)
                            {
                            // the method doesn't require anything, but there are args
                            if (method.getParamCount() > 0)
                                {
                                TypeConstant typeArg = method.getParam(0).getType();
                                if (typeStrings.isA(typeArg))
                                    {
                                    ahArg = new ObjectHandle[]{xString.makeArrayHandle(asArg)};
                                    }
                                else
                                    {
                                    log(Severity.ERROR, "Unsupported argument type \"" +
                                        typeArg.getValueString() + "\" for method \"" + sMethod + "\"");
                                    abort(true);
                                    }
                                }
                            else
                                {
                                log(Severity.WARNING, "Method \"" + sMethod +
                                    "\" does not take any parameters; ignoring the specified arguments");
                                }
                            }
                        break;

                    case 1:
                        {
                        TypeConstant typeArg = method.getParam(0).getType();
                        if (typeStrings.isA(typeArg))
                            {
                            // the method requires an array that we can supply
                            ahArg = new ObjectHandle[]{
                                asArg == null
                                    ? xString.ensureEmptyArray()
                                    : xString.makeArrayHandle(asArg)};
                            }
                        else
                            {
                            log(Severity.ERROR, "Unsupported argument type \"" +
                                typeArg.getValueString() + "\" for method \"" + sMethod + "\"");
                            abort(true);
                            }
                        break;
                        }

                    default:
                        log(Severity.ERROR, "Unsupported method arguments \"" +
                            method.getIdentityConstant().getSignature().getValueString());
                        abort(true);
                    }

                connector.invoke0(sMethod, ahArg);

                connector.join();
                }
            }
        catch (InterruptedException ignore)
            {
            }
        catch (Throwable e)
            {
            log(Severity.FATAL, e.getMessage());
            }
        }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc()
        {
        return """
            Ecstasy runner:

                Executes an Ecstasy module, compiling it first if necessary.

            Usage:

                xec <options> <modulename>
                
            Also supports any of:
            
                xec <options> <filename>
                xec <options> <filename>.x
                xec <options> <filename>.xtc
            """;
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
     * Runner command-line options implementation.
     */
    public class Options
        extends Launcher.Options
        {
        /**
         * Construct the Runner Options.
         */
        public Options()
            {
            super();

            addOption("L",       Form.Repo  , true , "Module path; a \"" + File.pathSeparator
                                                  + "\"-delimited list of file and/or directory names");
            addOption("M",       Form.String, false, "Method name; defaults to \"run\"");
            addOption(Trailing,  Form.File  , false, "Module file name (.xtc) to execute");
            addOption(ArgV,      Form.AsIs  , true , "Arguments to pass to the method");
            }

        /**
         * @return the list of files in the module path (empty list if none specified)
         */
        public List<File> getModulePath()
            {
            return (List<File>) values().getOrDefault("L", Collections.emptyList());
            }

        /**
         * @return the method name
         */
        public String getMethodName()
            {
            return (String) values().getOrDefault("M", "run");
            }

        /**
         * @return the file to execute
         */
        public File getTarget()
            {
            return (File) values().get(Trailing);
            }

        /**
         * @return the method arguments as an array of String, or null if none specified
         */
        public String[] getMethodArgs()
            {
            List<String> listArgs = (List<String>) values().get(ArgV);
            return listArgs == null
                    ? null
                    : listArgs.toArray(Handy.NO_ARGS);
            }

        @Override
        public void validate()
            {
            super.validate();

            // validate the -L path of file(s)/dir(s)
            validateModulePath(getModulePath());
            }
        }
    }
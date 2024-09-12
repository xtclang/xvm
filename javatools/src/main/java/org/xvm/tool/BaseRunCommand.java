package org.xvm.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.api.Connector;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;
import org.xvm.tool.flag.FlagSet;
import org.xvm.util.ListSet;
import org.xvm.util.Severity;

import static org.xvm.tool.Compiler.CMD_COMPILER;
import static org.xvm.tool.ModuleInfo.isExplicitCompiledFile;
import static org.xvm.tool.ModuleInfo.isExplicitEcstasyFile;
import static org.xvm.util.Handy.checkReadable;
import static org.xvm.util.Handy.isPathed;
import static org.xvm.util.Handy.quotedString;

/**
 * The base command for commands that will "run" an Ecstasy
 * module, for example "run" and "test".
 */
public abstract class BaseRunCommand
        extends AbstractCommand
    {
    /**
     * Runner constructor.
     *
     * @param sName    the name of this command (as specified on the command line)
     * @param console  representation of the terminal within which this command is run
     */
    protected BaseRunCommand(String sName, Console console)
        {
        super(sName, console);
        }

    @Override
    protected void process()
        {
        // repository setup
        FlagSet          flags = flags();
        ModuleRepository repo  = configureLibraryRepo(flags.getModulePath());
        checkErrors();

        final File fileSpec = getTarget();
        if (fileSpec == null)
            {
            if (!isShowVersion())
                {
                displayHelp();
                }
            return;
            }

        String          filePath   = fileSpec.getPath();
        File            fileBin    = null;
        boolean         binExists  = false;
        ModuleStructure module     = null;
        String          binLocDesc;
        if (isExplicitCompiledFile(filePath) && fileSpec.exists()
                && (isCompileDisabled() || isPathed(filePath)))
            {
            // the caller has explicitly specified the exact .xtc file and/or
            fileBin    = fileSpec;
            binExists  = true;
            binLocDesc = "the specified target " + filePath;
            }
        else if (!isPathed(filePath) && !isExplicitEcstasyFile(filePath)
                && (module = repo.loadModule(filePath)) != null)
            {
            // use the module we found in the repo
            binLocDesc = "the repository";
            }
        else
            {
            ModuleInfo info    = null;
            File       outFile = flags.getFile(ARG_OUTPUT);
            try
                {
                info = new ModuleInfo(fileSpec, null, outFile);
                }
            catch (RuntimeException e)
                {
                log(Severity.ERROR, "Failed to identify the module for: " + fileSpec + " (" + e + ")");
                }
            checkErrors();

            fileBin   = info.getBinaryFile();
            binExists = fileBin != null && fileBin.exists();

            boolean fCompile = false;
            if (!binExists)
                {
                String qualName = info.getQualifiedModuleName();
                module = repo.loadModule(qualName);
                if (module == null)
                    {
                    File fileSrc = info.getSourceFile();
                    if (fileSrc != null && fileSrc.exists() && !isCompileDisabled())
                        {
                        log(Severity.INFO, "The compiled module \"" + info.getQualifiedModuleName()
                                + "\" is missing; attempting to compile it from "
                                + info.getSourceFile() + " ....");
                        fCompile = true;
                        }
                    else
                        {
                        Set<String> possibles = null;
                        if (qualName.indexOf('.') < 0)
                            {
                            // the qualified name wasn't qualified; that may have been user input
                            // error; find all the names that they may have meant to type
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
                                    + "; did you mean " + quotedString(possibles.iterator().next())
                                    + '?');
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
                else
                    {
                    binExists = true;
                    }
                }

            if (binExists && !isCompileDisabled() && info.getSourceFile() != null
                    && info.getSourceFile().exists() && !info.isUpToDate())
                {
                log(Severity.INFO, "The compiled module \"" + info.getQualifiedModuleName()
                        + "\" is out-of-date; recompiling ....");
                File fileBak = new File(fileBin.getParentFile(), fileBin.getName() + ".old");
                if (fileBak.exists() && !fileBak.delete())
                    {
                    log(Severity.ERROR, "Failed to delete the previously-backed-up module: " + fileBak);
                    }
                else if (fileBin.exists() && !fileBin.renameTo(fileBak))
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
                List<String> compilerArgs = new ArrayList<>();
                if (flags.isVerbose())
                    {
                    compilerArgs.add("-v");
                    }

                List<File> libPath = flags.getModulePath();
                if (!libPath.isEmpty())
                    {
                    for (File libFile : libPath)
                        {
                        compilerArgs.add("-L");
                        compilerArgs.add(libFile.getPath());
                        }
                    }

                if (outFile != null)
                    {
                    compilerArgs.add("-o");
                    compilerArgs.add(outFile.getPath());
                    }

                compilerArgs.add(fileSpec.getPath());

                Launcher.launch(CMD_COMPILER, compilerArgs);

                info      = new ModuleInfo(fileSpec, null, outFile);
                fileBin   = info.getBinaryFile();
                binExists = fileBin != null && fileBin.exists();
                repo      = configureLibraryRepo(libPath);
                module    = repo.loadModule(info.getQualifiedModuleName());
                }

            binLocDesc = info.getBinaryFile().getPath();
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
            log(Severity.ERROR, "Missing module for " + fileSpec);
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

        String sName = module.getName();
        if (sName.equals(module.getSimpleName()))
            {
            // quote the "simpleName" to visually differentiate it (there is no qualified name)
            sName = quotedString(sName);
            }

        if (isShowVersion())
            {
            Version ver   = module.getVersion();
            String  sVer  = ver == null ? "<none>" : ver.toString();
            out(sName + " version " + sVer);
            }

        log(Severity.INFO, "Executing " + sName + " from " + binLocDesc);
        try
            {
            Connector connector = new Connector(repo);
            connector.loadModule(module.getName());

            connector.start(getInjections());

            ConstantPool pool = connector.getConstantPool();
            try (var ignore = ConstantPool.withPool(pool))
                {
                invoke(connector, pool, module);
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

    /**
     * Implemented by subclasses to actually execute a task using an Ecstasy module.
     *
     * @param connector  the {@link Connector}
     * @param pool       the constant pool to use
     * @param module     the module to execute
     */
    protected abstract void invoke(Connector connector, ConstantPool pool, ModuleStructure module);

    // ----- command line flags --------------------------------------------------------------------

    @Override
    protected void validate(FlagSet flagSet)
        {
        // validate the -L path of file(s)/dir(s)
        validateModulePath(flagSet.getModulePath());
        }

    @Override
    protected FlagSet instantiateFlags(String sName)
        {
        return new FlagSet()
                .withModulePath()
                .withStringMap(ARG_INJECTIONS, 'I', "Specifies name/value pairs for injection; the format is \"name1=value1,name2=value2\"")
                .withBoolean(ARG_NO_RECOMPILE, "Disable automatic compilation")
                .withFile(ARG_OUTPUT, 'o', "If compilation is necessary, the file or directory to write compiler output to");
        }

    /**
     * @return true iff "--no-recompile" is specified
     */
    public boolean isCompileDisabled()
        {
        return flags().getBoolean(ARG_NO_RECOMPILE);
        }

    /**
     * @return the module file to execute
     */
    public File getTarget()
        {
        FlagSet      flags    = flags();
        List<String> listArgs = flags.getArgumentsBeforeDashDash();
        if (listArgs.isEmpty())
            {
            return null;
            }
        return new File(listArgs.getFirst());
        }

    /**
     * @return the map of specified injection keys and values specified by the "--inject" flag
     * along with the values from any pass-thru flags.
     */
    public Map<String, ?> getInjections()
        {
        Map<String, Object> injections = new HashMap<>();
        FlagSet             flags      = flags();
        injections.putAll(flags.getPassThruArguments());
        injections.putAll(flags.getMapOfStrings(ARG_INJECTIONS));
        return injections;
        }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * The no recompilation command line flag name.
     */
    public static final String ARG_NO_RECOMPILE = "no-recompile";

    /**
     * The output file command line flag name.
     */
    public static final String ARG_OUTPUT = "output";

    /**
     * The injections command line flag name.
     */
    public static final String ARG_INJECTIONS = "inject";
    }
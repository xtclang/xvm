package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.Collections;
import java.util.List;

import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.text.xString;

import org.xvm.util.Handy;
import org.xvm.util.Severity;


/**
 * The "test" command:
 * <pre>
 *  java org.xvm.tool.TestRunner [-L repo(s)] app.xtc [argv]
 * </pre>
 */
public class TestRunner
        extends Launcher
    {
    /**
     * Entry point from the OS.
     *
     * @param asArg command line arguments
     */
    public static void main(String[] asArg)
        {
        new TestRunner(asArg).run();
        }

    /**
     * TestRunner constructor.
     *
     * @param asArg command line arguments
     */
    public TestRunner(String[] asArg)
        {
        super(asArg);
        }

    @Override
    protected void process()
        {
        // repository setup
        ModuleRepository repo = configureLibraryRepo(options().getModulePath());
        checkErrors();

        if (options().isShowVersion())
            {
            ModuleStructure core = repo.loadModule(Constants.ECSTASY_MODULE);
            out("Ecstasy Runtime Environment " + core.getVersion().getVersion()
                    + " (" + Constants.VERSION_MAJOR_CUR + "." + Constants.VERSION_MINOR_CUR + ")");
            }

        File fileModule = options().getTarget();
        if (fileModule == null)
            {
            return;
            }

        File            fileDir    = fileModule.getAbsoluteFile().getParentFile();
        String          sModule    = fileModule.getName();
        boolean         fExtension = explicitModuleFile(sModule);
        ModuleStructure module     = null;
        if (!fExtension)
            {
            module = repo.loadModule(sModule);
            }

        // check if the source file name was specified
        if (module == null && sModule.endsWith(".x"))
            {
            File fileSrc = sourceFile(fileModule);
            if (checkFile(fileSrc, null))
                {
                // determine the name of the compiled module
                String sName = getModuleName(fileSrc);
                sModule = sName == null
                        ? sModule + "tc"        // best guess: change ".x" to ".xtc"
                        : sName + ".xtc";
                }
            else
                {
                // best guess: the name of the compiled file ends with ".xtc" instead of ".x"
                sModule += "tc";
                }
            fileModule = new File(fileDir, sModule);
            }

        // check if the compiled module file name was specified
        if (module == null && sModule.endsWith(".xtc"))
            {
            File fileBin = binaryFile(fileModule);
            if (checkFile(fileBin, null))
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
                    log(Severity.FATAL, "I/O exception (" + e + ") reading module file: " + fileModule);
                    abort(true);
                    }
                }
            }

        // check the repository for a module of that name
        if (module == null && fExtension)
            {
            module = repo.loadModule(stripExtension(sModule));
            }

        // assume it's a file that is missing its extension
        if (module == null && !fExtension)
            {
            // basically, repeat the above steps for file searches; start with the source file
            sModule   += ".x";
            fileModule = new File(fileDir, sModule);
            File fileSrc = sourceFile(fileModule);
            if (checkFile(fileSrc, null))
                {
                // determine the name of the compiled module
                String sName = getModuleName(fileSrc);
                sModule = sName == null
                    ? sModule + "tc"        // best guess: change ".x" to ".xtc"
                    : sName + ".xtc";
                }
            else
                {
                // best guess: the name of the compiled file ends with ".xtc" instead of ".x"
                sModule += "tc";
                }
            fileModule = new File(fileDir, sModule);

            // then look for the compiled file
            File fileBin = binaryFile(fileModule);
            if (checkFile(fileBin, null))
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
                    log(Severity.FATAL, "I/O exception (" + e + ") reading module file: " + fileModule);
                    abort(true);
                    }
                }
            }

        if (module != null)
            {
            try
                {
                repo.storeModule(module);
                }
            catch (IOException e)
                {
                log(Severity.FATAL, "I/O exception (" + e + ") storing module file: " + fileModule);
                abort(true);
                }
            }

        if (module == null)
            {
            log(Severity.ERROR, "Unable to load module: " + fileModule);
            }
        checkErrors();


        try
        {
        ModuleStructure engineModule = repo.loadModule("xunit_engine.xtclang.org");
        Connector       connector    = new Connector(repo);
        connector.loadModule(engineModule.getName());

        connector.start();

        String   sMethod = options().getMethodName();
        String[] asArg   = new String[]{module.getName()};//options().getMethodArgs();

        ObjectHandle[] ahArg = Utils.OBJECTS_NONE;
        if (asArg != null)
            {
            try (var ignore = ConstantPool.withPool(connector.getConstantPool()))
                {
                ahArg = new ObjectHandle[]{xString.makeArrayHandle(asArg)};
                }
            }
        connector.invoke0(sMethod, ahArg);

        connector.join();
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
            Ecstasy test runner:

            Executes tests in a compiled Ecstasy module.

            Usage:

                xtest <options> <modulename> <xunit engine args>
            or:
                xtest <options> <filename>.xtc <xunit engine args>
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
     * TestRunner command-line options implementation.
     */
    public class Options
        extends Launcher.Options
        {
        /**
         * Construct the TestRunner Options.
         */
        public Options()
            {
            super();

            addOption("version", Form.Name  , false, "Displays the Ecstasy runtime version");
            addOption("L",       Form.Repo  , true , "Module path; a \"" + File.pathSeparator
                                                  + "\"-delimited list of file and/or directory names");
            addOption(Trailing,  Form.File  , false, "Module file name (.xtc) to execute tests in");
            addOption(ArgV,      Form.AsIs  , true , "Arguments to pass to the XUnit engine");
            }

        /**
         * @return true if a "show version" option has been specified
         */
        boolean isShowVersion()
            {
            return specified("version");
            }

        /**
         * @return the list of files in the module path (empty list if none specified)
         */
        public List<File> getModulePath()
            {
            return (List<File>) values().getOrDefault("L", Collections.EMPTY_LIST);
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

            // validate the trailing file (to execute)
            // note: the logic for validating the file is quite complex (it must intuit whether the
            // name is a module name or file name, and must potentially find the real module name
            // in the source code, and so on), so the logic is performed by the process() method
            File fileModule = getTarget();
            if (fileModule == null && !isShowVersion())
                {
                log(Severity.ERROR, "Module name or module file name must be specified");
                }
            }
        }
    }
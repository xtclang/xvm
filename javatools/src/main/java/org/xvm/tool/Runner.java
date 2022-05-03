package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.Collections;
import java.util.List;

import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.Handy;
import org.xvm.util.Severity;


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
        super(asArg);
        }

    @Override
    protected void process()
        {
        // repository setup
        log(Severity.INFO, "Creating and pre-populating library and build repositories");
        ModuleRepository repo = configureLibraryRepo(options().getModulePath());
        checkErrors();

        log(Severity.INFO, "Pre-loading and linking system libraries");
        prelinkSystemLibraries(repo, true);
        checkErrors();

        File            fileModule = options().getTarget();
        String          sModule    = fileModule.getName();
        ModuleStructure module     = null;
        if (sModule.endsWith(".xtc"))
            {
            // it's a file
            try
                {
                try (FileInputStream in = new FileInputStream(fileModule))
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
            }
        else
            {
            // it's a module
            module = repo.loadModule(sModule);
            }

        if (module == null)
            {
            log(Severity.ERROR, "Unable to load module: " + fileModule);
            }
        checkErrors();

        try
            {
            Connector connector = new Connector(repo);
            connector.loadModule(module.getName());

            connector.start();

            String   sMethod = options().getMethodName();
            String[] asArg   = options().getMethodArgs();

            ObjectHandle[] ahArg = Utils.OBJECTS_NONE;
            if (asArg != null)
                {
                try (var ignore = ConstantPool.withPool(connector.getConstantPool()))
                    {
                    int            cArgs  = asArg.length;
                    StringHandle[] ahName = new StringHandle[cArgs];
                    for (int i = 0; i < cArgs; i++)
                        {
                        ahName[i] = xString.makeHandle(asArg[i]);
                        }
                    ahArg = new ObjectHandle[]{xArray.makeStringArrayHandle(ahName)};
                    }
                }
            connector.invoke0(sMethod, ahArg);

            connector.join();
            }
        catch (InterruptedException ignore)
            {
            }
        }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc()
        {
        return """
            Ecstasy runner:

            Executes a compiled Ecstasy module.

            Usage:

                xec <options> <modulename>
            or:
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

            addOption("L",      Form.Repo  , true , "Module path; a \"" + File.pathSeparator
                                                  + "\"-delimited list of file and/or directory names");
            addOption("M",      Form.String, false, "Method name; defaults to \"run\"");
            addOption(Trailing, Form.File  , false, "Module file name (.xtc) to execute");
            addOption(ArgV,     Form.AsIs  , true , "Arguments to pass to the method");
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
            File fileModule = getTarget();
            if (fileModule.getName().endsWith(".xtc"))
                {
                if (fileModule == null || fileModule.length() == 0)
                    {
                    log(Severity.ERROR, "Module file required");
                    }
                else if (!fileModule.exists())
                    {
                    log(Severity.ERROR, "Specified module file does not exist");
                    }
                else if (!fileModule.isFile())
                    {
                    log(Severity.ERROR, "Specified module file is not a file");
                    }
                else if (!fileModule.canRead())
                    {
                    log(Severity.ERROR, "Specified module file cannot be read");
                    }
                }
            }
        }
    }


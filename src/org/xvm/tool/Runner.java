package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.Collections;
import java.util.List;

import org.xvm.api.Connector;

import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.util.Severity;


/**
 * The "execute" command:
 *
 *  java org.xvm.tool.Runner xtc_path [method_name [args]]
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
        prelinkSystemLibraries(repo);
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
                repo.storeModule(module);
                }
            catch (IOException e)
                {
                log(Severity.ERROR, "I/O exception (" + e + ") reading module file: " + fileModule);
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

            String sMethod = "run";
            // TODO GG
            // if (cArgs > 1)
            //     {
            //     sMethod = asArg[1];
            //     }

            ObjectHandle[] ahArg = Utils.OBJECTS_NONE;
            // TODO GG
            //if (cArgs > 2)
            //    {
            //    try (var x = ConstantPool.withPool(connector.getConstantPool()))
            //        {
            //        StringHandle[] ahName = new StringHandle[cArgs - 2];
            //        for (int i = 2; i < cArgs; i++)
            //            {
            //            ahName[i-2] = xString.makeHandle(asArg[i]);
            //            }
            //        ahArg = new ObjectHandle[]{xArray.makeStringArrayHandle(ahName)};
            //        }
            //    }
            connector.invoke0(sMethod, ahArg);

            connector.join();
            }
        catch (InterruptedException e)
            {
            }
        }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc()
        {
        return "Ecstasy runner:\n" +
               '\n' +
               "Executes a compiled Ecstasy module.\n" +
               '\n' +
               "Usage:\n" +
               '\n' +
               "    xec <options> <modulename>\n" +
               "or:\n" +
               "    xec <options> <filename>.xtc\n";
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

            addOption("L",      Form.Repo, true , "Module path; a \"" + File.pathSeparator
                                                + "\"-delimited list of file and/or directory names");
            addOption(Trailing, Form.File, false, ".xtc file name to execute");
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
         * @return the file to execute
         */
        public File getTarget()
            {
            return (File) values().get(Trailing);
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


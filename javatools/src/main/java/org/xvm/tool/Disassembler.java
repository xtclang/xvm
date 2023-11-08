package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.Collections;
import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.ModuleRepository;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.util.Severity;


/**
 * The "disassemble" command:
 *
 *  java org.xvm.tool.Disassembler xtc_path
 */
public class Disassembler
        extends Launcher
    {
    /**
     * Entry point from the OS.
     *
     * @param asArg command line arguments
     */
    public static void main(String[] asArg)
        {
        new Disassembler(asArg).run();
        }

    /**
     * Disassembler constructor.
     *
     * @param asArg command line arguments
     */
    public Disassembler(String[] asArg)
        {
        super(asArg);
        }

    @Override
    protected void process()
        {
        File      fileModule = options().getTarget();
        String    sModule    = fileModule.getName();
        Component component  = null;
        if (sModule.endsWith(".xtc"))
            {
            // it's a file
            log(Severity.INFO, "Loading module file: " + sModule);
            try
                {
                try (FileInputStream in = new FileInputStream(fileModule))
                    {
                    component = new FileStructure(in);
                    }
                }
            catch (IOException e)
                {
                log(Severity.ERROR, "I/O exception (" + e + ") reading module file: " + fileModule);
                }
            }
        else
            {
            // it's a module; set up the repository
            log(Severity.INFO, "Creating and pre-populating library and build repositories");
            ModuleRepository repo = configureLibraryRepo(options().getModulePath());
            checkErrors();

            log(Severity.INFO, "Loading module: " + sModule);
            component = repo.loadModule(sModule);
            }

        if (component == null)
            {
            log(Severity.ERROR, "Unable to load module: " + fileModule);
            }
        checkErrors();

        component.visitChildren(Disassembler::dump, false, true);
        }

    public static void dump(Component component)
        {
        if (component instanceof MethodStructure method)
            {
            MethodConstant id = method.getIdentityConstant();

            if (method.hasCode() && method.ensureCode() != null && !method.isNative())
                {
                out("** code for " + id);
                out(method.ensureCode().toString());
                out("");
                }
            else
                {
                out("** no code for " + id);
                out("");
                }
            }
        }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc()
        {
        return """
            Ecstasy disassembler:

            Examines a compiled Ecstasy module.

            Usage:

                xam <options> <modulename>
            or:
                xam <options> <filename>.xtc
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
     * Disassembler command-line options implementation.
     */
    public class Options
        extends Launcher.Options
        {
        /**
         * Construct the Disassembler Options.
         */
        public Options()
            {
            super();

            addOption("L",      Form.Repo  , true , "Module path; a \"" + File.pathSeparator
                                                  + "\"-delimited list of file and/or directory names");
            addOption(Trailing, Form.File  , false, "Module file name (.xtc) to disassemble");
            }

        /**
         * @return the list of files in the module path (empty list if none specified)
         */
        public List<File> getModulePath()
            {
            return (List<File>) values().getOrDefault("L", Collections.emptyList());
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

            // validate the trailing file (to execute)
            File fileModule = getTarget();
            if (fileModule == null || fileModule.length() == 0)
                {
                log(Severity.ERROR, "Module file required");
                }
            else if (fileModule.getName().endsWith(".xtc"))
                {
                if (!fileModule.exists())
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


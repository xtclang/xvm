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

import static org.xvm.util.Handy.parseDelimitedString;


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
        ModuleRepository repo = configureLibraryRepo(options().getModulePath());
        checkErrors();

        File            fileModule = options().getTarget();
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
            // note: the logic for validating the file is quite complex (it must intuit whether the
            // name is a module name or file name, and must potentially find the real module name
            // in the source code, and so on), so the logic is performed by the process() method
            File fileModule = getTarget();
            if (fileModule == null)
                {
                log(Severity.ERROR, "Module name or module file name must be specified");
                }
            }
        }


    // ----- internal helpers ----------------------------------------------------------------------

    /**
     * Determine if the specified module name is an explicit Ecstasy source or compiled module file
     * name.
     *
     * @param sModule  a module name or file name
     *
     * @return true iff the passed name is an explicit Ecstasy source or compiled module file name
     */
    boolean explicitModuleFile(String sModule)
        {
        return sModule.endsWith(".x") || sModule.endsWith(".xtc");
        }

    /**
     * If the passed file name ends with a ".x" or a ".xtc" extension, return the name without the
     * extension.
     *
     * @param sFile  the file name, possibly with a ".x" or ".xtc" extension
     *
     * @return the same file name, but without a ".x" or ".xtc" extension (if it had one)
     */
    String stripExtension(String sFile)
        {
        if (sFile.endsWith(".x"))
            {
            return sFile.substring(0, sFile.length()-2);
            }
        if (sFile.endsWith(".xtc"))
            {
            return sFile.substring(0, sFile.length()-4);
            }
        return sFile;
        }

    /**
     * Given a possible module file, try to find the corresponding module source file.
     *
     * @param fileModule  the module file (either source or compiled)
     *
     * @return the module source file, or null if it could not be found
     */
    File sourceFile(File fileModule)
        {
        String sModule = stripExtension(fileModule.getName());
        File   fileDir = fileModule.getAbsoluteFile().getParentFile();
        File   fileSrc = new File(fileDir, sModule + ".x");
        if (fileSrc.exists())
            {
            return fileSrc;
            }

        // in case names don't match (since file name for source doesn't have to match module name)
        fileSrc = findModuleSource(fileDir, sModule);
        if (checkFile(fileSrc, null))
            {
            return fileSrc;
            }

        // check if we're in the "build" or "dist" directory, and try to navigate to the source
        // directory using well known conventions
        if (fileDir.getName().equals("build") || fileDir.getName().equals("dist"))
            {
            // try a few more possibilities before giving up
            String[] asSearchPath = parseDelimitedString(
                    "src,source,src/x,source/x,src/main,source/main,src/main/x,source/main/x", ',');
            for (String sPath : asSearchPath)
                {
                File fileSearchDir = navigateTo(fileDir, sPath);
                if (fileSearchDir != null && fileSearchDir.isDirectory())
                    {
                    fileSrc = findModuleSource(fileSearchDir, sModule);
                    if (fileSrc != null && fileSrc.exists())
                        {
                        return fileSrc;
                        }
                    }
                }
            }

        return null;
        }

    /**
     * Given a possible module file, try to find the actual compiled module binary file.
     *
     * @param fileModule  the module file (either source or compiled)
     *
     * @return the module binary file, or null if it could not be found
     */
    File binaryFile(File fileModule)
        {
        String sModule = stripExtension(fileModule.getName());
        File   fileDir = fileModule.getAbsoluteFile().getParentFile();
        File   fileBin = new File(fileDir, sModule + ".xtc");
        if (fileBin.exists())
            {
            return fileBin;
            }

        // in case names don't match (because simple vs qualified names)
        fileBin = findModuleBinary(fileDir, sModule);
        if (checkFile(fileBin, null))
            {
            return fileBin;
            }

        // check if we're in the "src" or "source" directory, and try to navigate to the build or
        // dist directory using well known conventions
        String sDir = fileDir.getName();
        while (sDir.equals("src") || sDir.equals("source") || sDir.equals("main") || sDir.equals("x"))
            {
            fileDir = fileDir.getParentFile();
            if (fileDir == null)
                {
                return null;
                }
            sDir = fileDir.getName();
            }

        String[] asSearchPath = parseDelimitedString("dist,build", ',');
        for (String sPath : asSearchPath)
            {
            File fileSearchDir = navigateTo(fileDir, sPath);
            if (fileSearchDir != null && fileSearchDir.isDirectory())
                {
                fileBin = findModuleBinary(fileSearchDir, sModule);
                if (checkFile(fileBin, null))
                    {
                    return fileBin;
                    }
                }
            }

        return null;
        }

    /**
     * Given a starting directory and a sequence of '/'-delimited directory names, obtain the file
     * or directory indicated.
     *
     * @param file   the starting point
     * @param sPath  a '/'-delimited relative path
     *
     * @return the indicated file or directory, or null if it could not be navigated to
     */
    File navigateTo(File file, String sPath)
        {
        for (String sPart : parseDelimitedString(sPath, '/'))
            {
            if (!file.isDirectory())
                {
                return null;
                }

            file = switch (sPart)
                {
                case "."  -> file;
                case ".." -> file.getAbsoluteFile().getParentFile();
                default   -> new File(file, sPart);
                };

            if (file == null || !file.exists())
                {
                return null;
                }
            }

        return file;
        }

    /**
     * Given a directory that may contain an arbitrarily named source file containing the specified
     * module name, search for that source file.
     *
     * @param fileDir  a directory that may contain source
     * @param sModule  the module name (either the short name or the qualified name)
     *
     * @return the file that appears to contain the source for the module, or null
     */
    File findModuleSource(File fileDir, String sModule)
        {
        if (fileDir == null || !fileDir.exists() || !fileDir.isDirectory())
            {
            return null;
            }

        // extract the simple name of the module
        int    ofDot   = sModule.indexOf('.');
        String sSimple = ofDot < 0 ? sModule : sModule.substring(0, ofDot);

        // check various .x files in the directory to see if they contain the module
        suspendErrors();
        try
            {
            for (File fileSrc : fileDir.listFiles())
                {
                if (fileSrc.isFile() && fileSrc.canRead() && fileSrc.getName().endsWith(".x"))
                    {
                    String sCurrent = getModuleName(fileSrc);
                    if (sCurrent.equals(sModule) || sCurrent.equals(sSimple))
                        {
                        return fileSrc;
                        }

                    ofDot = sCurrent.indexOf('.');
                    if (ofDot >= 0 && sCurrent.substring(0, ofDot).equals(sSimple))
                        {
                        return fileSrc;
                        }
                    }
                }
            }
        finally
            {
            resumeErrors();
            }

        return null;
        }

    /**
     * Given a directory that may contain the module file for the specified module name, search for
     * that module file.
     *
     * @param fileDir  a directory that may contain a module file
     * @param sModule  the module name (either the short name or the qualified name)
     *
     * @return the file that appears to contain the compiled module, or null
     */
    File findModuleBinary(File fileDir, String sModule)
        {
        if (fileDir == null || !fileDir.exists() || !fileDir.isDirectory())
            {
            return null;
            }

        // extract the simple name of the module
        int    ofDot   = sModule.indexOf('.');
        String sSimple = ofDot < 0 ? sModule : sModule.substring(0, ofDot);

        // check various .x files in the directory to see if they contain the module
        for (File fileBin : fileDir.listFiles())
            {
            if (fileBin.isFile() && fileBin.canRead() && fileBin.getName().endsWith(".xtc"))
                {
                String sCurrent = stripExtension(fileBin.getName());
                if (sCurrent.equals(sModule) || sCurrent.equals(sSimple))
                    {
                    return fileBin;
                    }

                ofDot = sCurrent.indexOf('.');
                if (ofDot >= 0 && sCurrent.substring(0, ofDot).equals(sSimple))
                    {
                    return fileBin;
                    }
                }
            }

        return null;
        }

    /**
     * Evaluate the passed file to make sure that it exists and can be read.
     *
     * @param file   the file to check
     * @param sDesc  a short description of the file, such as "source file", to be used in any
     *               logged errors; pass null to suppress error logging
     *
     * @return true if the file check passes
     */
    boolean checkFile(File file, String sDesc)
        {
        String sErr;
        if (file == null || !file.exists())
            {
            sErr = "does not exist";
            }
        else if (file.length() == 0)
            {
            sErr = "is empty";
            }
        else if (!file.isFile())
            {
            sErr = "is not a file";
            }
        else if (!file.canRead())
            {
            sErr = "cannot be read";
            }
        else
            {
            return true;
            }

        if (sDesc != null)
            {
            log(Severity.ERROR, "Specified " + sDesc + " (\"" + file + "\") " + sErr);
            }
        return false;
        }
    }
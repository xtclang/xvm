package org.xvm.compiler;


import org.xvm.util.Handy;
import org.xvm.util.ListMap;

import java.io.File;
import java.io.IOException;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * This is the command-line harness for the prototype Ecstasy compiler.
 *
 * Find the root of the module containing the code in the current directory, and compile it, placing
 * the result in the default location:
 *
 *   xtc
 *
 * Compile the specified module, placing the result in the default location:
 *
 *   xtc ./path/to/module.x
 *
 * Compile just the specified file, or the minimum number of files necessary to compile the
 * specified file:
 *
 *   xtc MyClass.x
 *
 * The default location for the resulting {@code .xtc} file is based on the project structure
 * containing the module. The default location is determined by following these steps, and selecting
 * the first one that fits:
 * # If the module is located in a directory with a writable sibling directory named "target", and
 *   the result of the compilation is a module file, then the resulting module will be stored in the
 *   "target" directory.
 * # If the module is located in a directory with a writable sibling directory named "build", and
 *   the result of the compilation is a module or package or class file, then the resulting module,
 *   package, or class file will be stored in the "build" directory.
 * # If the module is located in a directory with a writable sibling directory named "classes", and
 *   the result of the compilation is a package or class file, then the resulting package or class
 *   file will be stored in the "classes" directory.
 * # Otherwise, the {@code .xtc} file will be placed in the directory containing the module root.
 *
 * The location of the resulting {@code .xtc} file can be specified by using the {@code -D} option;
 * for example:
 *
 *   xtc -D ~/modules/
 *
 * In addition to built-in Ecstasy modules and modules located in the Ecstasy runtime repository,
 * it is possible to provide a search path for modules that will be used by the compiler:
 *
 *   xtc -M ~/modules/:../build/
 *
 * Other command line options:
 * * {@code -nosrc} - do not include source code in the compiled module
 * * {@code -nodbg} - do not include debugging information in the compiled module
 * * {@code -nodoc} - do not include documentation in the compiled module
 * * {@code -strict} - convert warnings to errors
 * * {@code -nowarn} - suppress warnings
 * * {@code -verbose} - provide information about the work being done by the compilation process
 *
 * @author cp 2017.03.23
 */
public class CommandLine
    {
    public static void main(String[] args)
            throws Exception
        {
        List<File>   sources  = new ArrayList<>();
        Options      opts     = new Options();
        List<String> deferred = new ArrayList<>();
        boolean      error    = false;
        if (args != null)
            {
            String sContinued = null;
            for (String s : args)
                {
                if (s != null && s.length() > 0)
                    {
                    if (s.equals("-D") || s.equals("-M"))
                        {
                        sContinued = s;
                        }
                    else if (s.startsWith("-D") || (sContinued != null && sContinued.equals("-D")))
                        {
                        if (s.startsWith("-D"))
                            {
                            s = s.substring(2);
                            }
                        List<File> list = resolvePath(s);
                        if (list.isEmpty())
                            {
                            deferred.add("xtc: could not resolve: \"" + s + "\"");
                            error = true;
                            }
                        else if (list.size() > 1)
                            {
                            deferred.add("xtc: could not resolve to a single path: \"" + s + "\"");
                            error = true;
                            }
                        else
                            {
                            File file = list.get(0);
                            if (!file.canWrite())
                                {
                                deferred.add("xtc: " + (file.isDirectory() ? "directory" : "file")
                                        + " not writable: \"" + file + "\"");
                                error = true;
                                }
                            if (opts.destination != null)
                                {
                                deferred.add("xtc: overwriting destination \"" + opts.destination
                                        + "\" with new destination \"" + s + "\".");
                                }
                            opts.destination = file;
                            sContinued = null;
                            }
                        }
                    else if (s.startsWith("-M") || (sContinued != null && sContinued.equals("-M")))
                        {
                        if (s.startsWith("-M"))
                            {
                            s = s.substring(2);
                            }
                        for (String sPath : Handy.parseDelimitedString(s, File.pathSeparatorChar))
                            {
                            List<File> files = resolvePath(sPath);
                            if (files.isEmpty())
                                {
                                deferred.add("xtc: could not resolve: \"" + sPath + "\"");
                                }
                            else
                                {
                                for (File file : files)
                                    {
                                    if (file.canRead())
                                        {
                                        opts.modulePath.add(file);
                                        }
                                    else
                                        {
                                        deferred.add("xtc: " + (file.isDirectory() ? "directory" : "file")
                                                + " not readable: \"" + file + "\"");
                                        }
                                    }
                                }
                            }
                        sContinued = null;
                        }
                    else if (s.startsWith("-X"))
                        {
                        // format is -Xname=value or -Xname:value
                        if (s.length() > 2)
                            {
                            int ofDiv = s.indexOf('=');
                            if (ofDiv < 0)
                                {
                                ofDiv = s.indexOf(':');
                                }
                            if (ofDiv > 2)
                                {
                                String key = s.substring(2, ofDiv);
                                String val = s.substring(ofDiv + 1);
                                opts.customCfg.put(key, val);
                                }
                            }
                        }
                    else if (s.equals("-nosrc"))
                        {
                        opts.includeSrc = false;
                        }
                    else if (s.equals("-nodbg"))
                        {
                        opts.includeDbg = false;
                        }
                    else if (s.equals("-nodoc"))
                        {
                        opts.includeDoc = false;
                        }
                    else if (s.equals("-strict"))
                        {
                        opts.strictLevel = Options.Strictness.Stickler;
                        }
                    else if (s.equals("-nowarn"))
                        {
                        opts.strictLevel = Options.Strictness.Suppressed;
                        }
                    else if (s.equals("-verbose"))
                        {
                        opts.verbose = true;
                        }
                    else if (s.startsWith("-"))
                        {
                        deferred.add("xtc: unknown option: " + s);
                        }
                    else
                        {
                        List<File> files = resolvePath(s);
                        for (File file : files)
                            {
                            // this is expected to be the name of a file to compile
                            if (!file.exists())
                                {
                                deferred.add("xtc: no such file to compile: \"" + file + "\"");
                                error = true;
                                }
                            else if (!file.canRead())
                                {
                                deferred.add("xtc: " + (file.isDirectory() ? "directory" : "file")
                                        + " not readable: \"" + file + "\"");
                                error = true;
                                }
                            else
                                {
                                sources.add(file);
                                }
                            }
                        }
                    }
                }
            }

        // show accumulated errors/warnings
        if (error || (!deferred.isEmpty() && (opts.verbose
                || opts.strictLevel == Options.Strictness.Normal
                || opts.strictLevel == Options.Strictness.Stickler)))
            {
            for (String s : deferred)
                {
                out(s);
                }
            }

        if (error || (!deferred.isEmpty() && opts.strictLevel == Options.Strictness.Stickler))
            {
            out("xtc: Terminating.");
            System.exit(1);
            return;
            }

        // show compiler options
        if (opts.verbose)
            {
            out("xtc: Compiler options:");
            out(Handy.indentLines(opts.toString(), "   : "));
            out();

            if (!sources.isEmpty())
                {
                out("xtc: Source files:");
                int i = 0;
                for (File file : sources)
                    {
                    out("   : [" + i++ + "]=" + file);
                    }
                out();
                }
            }
        }

    /**
     * Represents the command-line options.
     */
    public static class Options
        {
        File    destination = null;
        boolean verbose     = false;
        boolean includeSrc  = true;
        boolean includeDbg  = true;
        boolean includeDoc  = true;

        enum Strictness {None, Suppressed, Normal, Stickler};
        Strictness strictLevel = Strictness.Normal;

        List<File> modulePath = new ArrayList<>();
        Map<String, String> customCfg = new ListMap<>();

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();
            sb.append("destination=")
              .append(destination==null ? "(default)" : destination)
              .append("\nverbose=")
              .append(verbose)
              .append("\nincludeSrc=")
              .append(includeSrc)
              .append("\nincludeDbg=")
              .append(includeDbg)
              .append("\nincludeDoc=")
              .append(includeDoc)
              .append("\nstrictLevel=")
              .append(strictLevel.name())
              .append("\nmodulePath=");

            if (modulePath.isEmpty())
                {
                sb.append("(none)");
                }
            else
                {
                int i = 0;
                for (File file : modulePath)
                    {
                    sb.append("\n[")
                      .append(i++)
                      .append("]=")
                      .append(file);

                    if (!file.exists())
                        {
                        sb.append(" (does not exist)");
                        }
                    else if (!file.canRead())
                        {
                        sb.append(" (no read permissions)");
                        }
                    else if (file.isDirectory())
                        {
                        sb.append(" (dir)");
                        }
                    }
                }

            if (!customCfg.isEmpty())
                {
                sb.append("\ncustomCfg=");
                for (Map.Entry<String, String> entry : customCfg.entrySet())
                    {
                    sb.append("\n[")
                      .append(entry.getKey())
                      .append("]=")
                      .append(entry.getValue());
                    }
                }
            return sb.toString();
            }
        }

    public static final void out()
        {
        out("");
        }

    public static final void out(Object o)
        {
        System.out.println(o);
        }

    /**
     *
     * @param sPath   the path to resolve
     *
     * @return a list of File objects
     *
     * @throws IOException
     */
    public static List<File> resolvePath(String sPath)
            throws IOException
        {
        List<File> files = new ArrayList<>();

        if (sPath.startsWith("~" + File.separator))
            {
            sPath = System.getProperty("user.home") + sPath.substring(1);
            }

        if (sPath.indexOf('*') >= 0 || sPath.indexOf('?') >= 0)
            {
            // wildcard file names
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), sPath))
                {
                stream.forEach(path -> files.add(path.toFile()));
                }
            }
        else
            {
            files.add(new File(sPath));
            }

        return files;
        }

    public static File findModule(File file)
            throws IOException
        {
        if (file.isFile())
            {
            Source    source  = new Source(file);
            ErrorList errlist = new ErrorList(100);
            Parser    parser  = new Parser(source, errlist);
            // TODO parse the file

            file = file.getParentFile();
            if (file == null)
                {
                return null;
                }
            }

        if (file.isDirectory())
            {
            // TODO does it contain module.x? if so, return module.x file
            // TODO otherwise loop getting parent

            }

        return null;
        }
    }

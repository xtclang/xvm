package org.xvm.tool;


import java.io.File;
import java.io.IOException;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.asm.FileRepository;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.compiler.BuildRepository;
import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;
import org.xvm.util.Severity;


import static org.xvm.util.Handy.indentLines;
import static org.xvm.util.Handy.parseDelimitedString;
import static org.xvm.util.Handy.quotedString;


/**
 * The "launcher" commands:
 *
 * <ul><li> <code>xtc</code> <i>("ecstasy")</i> routes to {@link Compiler}
 * </li><li> <code>xec</code> <i>("exec")</i> routes to {@link Runner}
 * </li><li> <code>xam</code> <i>("exam")</i> routes to {@link Disassembler}
 * </li></ul>
 */
public abstract class Launcher
    {
    /**
     * Entry point from the OS.
     *
     * @param asArg  command line arguments
     */
    public static void main(String[] asArg)
        {
        int argc = asArg.length;
        if (argc < 1)
            {
            System.err.println("Command name is missing");
            return;
            }

        String cmd = asArg[0];

        --argc;
        String[] argv = new String[argc];
        System.arraycopy(asArg, 1, argv, 0, argc);

        // if the command is prefixed with "debug", then strip that off
        if (cmd.length() > 5 && cmd.toLowerCase().startsWith("debug"))
            {
            cmd = cmd.substring("debug".length());
            if (cmd.charAt(0) == '_')
                {
                cmd = cmd.substring(1);
                }
            }

        switch (cmd)
            {
            case "xtc":
                Compiler.main(argv);
                break;

            case "xec":
                Runner.main(argv);
                break;

            case "xam":
                Disassembler.main(argv);
                break;

            default:
                System.err.println("Command name \"" + cmd + "\" is not supported");
                break;
            }
        }

    /**
     * @param asArgs  the Launcher's command-line arguments
     */
    public Launcher(String[] asArgs)
        {
        m_asArgs = asArgs;
        }

    /**
     * Execute the Launcher tool.
     */
    public void run()
        {
        Options opts = options();

        boolean fHelp = opts.parse(m_asArgs);
        checkErrors(fHelp);

        if (opts.isVerbose())
            {
            out();
            out(opts);
            out();
            }

        opts.validate();
        checkErrors();

        process();

        if (opts.isVerbose())
            {
            out();
            }
        }

    protected abstract void process();


    // ----- text output and error handling --------------------------------------------------------

    /**
     * Log a message of a specified severity.
     *
     * @param sev   the severity (may indicate an error)
     * @param sMsg  the message or error to display
     */
    protected void log(Severity sev, String sMsg)
        {
        if (errorsSuspended())
            {
            return;
            }

        if (sev.compareTo(m_sevWorst) > 0)
            {
            m_sevWorst = sev;
            }

        Options opts = options();
        if (opts.isBadEnoughToPrint(sev))
            {
            out(sev.desc() + ": " + sMsg);
            }

        if (sev == Severity.FATAL)
            {
            abort(true);
            }
        }

    /**
     * Log a sequence of errors from an ErrorList.
     *
     * @param errs  the ErrorList
     */
    protected void log(ErrorList errs)
        {
        for (ErrorInfo err : errs.getErrors())
            {
            log(err.getSeverity(), err.toString());
            }
        }

    /**
     * Print a blank line to the terminal.
     */
    public static void out()
        {
        out("");
        }

    /**
     * Print the String value of some object to the terminal.
     */
    public static void out(Object o)
        {
        System.out.println(o);
        }

    /**
     * Print a blank line to the terminal.
     */
    public static void err()
        {
        err("");
        }

    /**
     * Print the String value of some object to the terminal.
     */
    public static void err(Object o)
        {
        System.err.println(o);
        }

    /**
     * Suspend error detection.
     */
    protected void suspendErrors()
        {
        ++m_cSuspended;
        }

    /**
     * @return true if errors are currently suspended
     */
    protected boolean errorsSuspended()
        {
        return m_cSuspended > 0;
        }

    /**
     * Suspend error detection.
     */
    protected void resumeErrors()
        {
        if (m_cSuspended > 0)
            {
            --m_cSuspended;
            }
        else
            {
            log(Severity.FATAL, "Attempt to resume errors when errors have not been suspended");
            }
        }

    /**
     * Determine if a previously logged error should cause the program to exit, and if so, exit.
     */
    protected void checkErrors()
        {
        if (options().isBadEnoughToAbort(m_sevWorst))
            {
            abort(true);
            }
        }

    /**
     * Determine if a previously logged error should cause the program to exit, and if so, exit.
     *
     * @param fHelp  true iff the help message should be displayed and the program should exit
     */
    protected void checkErrors(boolean fHelp)
        {
        if (fHelp)
            {
            displayHelp();
            }

        if (fHelp || options().isBadEnoughToAbort(m_sevWorst))
            {
            abort(options().isBadEnoughToAbort(m_sevWorst));
            }
        }

    /**
     * Abort the command line with or without an error status.
     *
     * @param fError  true to abort with an error status
     */
    protected void abort(boolean fError)
        {
        System.exit(fError ? -1 : 0);
        }

    /**
     * Display a help message describing how to use this command-line tool.
     */
    public void displayHelp()
        {
        out();
        out(desc());
        out();

        out("Options:");
        Options options = options();

        String[] asName = options.options().keySet().toArray(Handy.NO_ARGS);
        Arrays.sort(asName, (s1, s2) ->
            {
            if (s1.equals(Trailing))
                {
                return 1;
                }
            if (s2.equals(Trailing))
                {
                return -1;
                }
            int n = s1.compareToIgnoreCase(s2);
            if (n == 0)
                {
                n = s1.compareTo(s2);
                }
            return n;
            });

        for (String sName : asName)
            {
            String sDisp = sName.equals(Trailing) ? sName : '-' + sName;

            StringBuilder sb = new StringBuilder();
            sb.append("  ")
              .append(sDisp);

            for (int i = 0, c = Math.max(2, 12 - sDisp.length()); i < c; ++i)
                {
                sb.append(' ');
                }

            sb.append(options.descriptionFor(sName));
            out(sb);
            }

        out();
        }

    /**
     * @return a description of this command-line tool
     */
    public String desc()
        {
        return this.getClass().getSimpleName();
        }

    /**
     * Produce a string representation of the specified option value.
     *
     * @param oVal  the value
     * @param form  the form of the value
     *
     * @return a string representation of the value
     */
    public static String stringFor(Object oVal, Form form)
        {
        switch (form)
            {
            case Name:
            case Boolean:
            case Int:
            case AsIs:
                return String.valueOf(oVal);

            case String:
                return quotedString((String) oVal);

            case File:
                return filePath((File) oVal);

            case Repo:
                StringBuilder sb    = new StringBuilder();
                boolean      first = true;
                for (File file : (List<File>) oVal)
                    {
                    if (first)
                        {
                        first = false;
                        }
                    else
                        {
                        sb.append(':');
                        }
                    sb.append(file.getPath());
                    }
                return sb.toString();

            default:
                throw new IllegalStateException();
            }
        }


    // ----- options -------------------------------------------------------------------------------

    /**
     * @return the Options for this Launcher
     */
    public Options options()
        {
        Options options = m_options;
        if (options == null)
            {
            m_options = options = instantiateOptions();
            }
        return options;
        }

    /**
     * @return a new Options object
     */
    protected abstract Options instantiateOptions();

    /**
     * A collection point and validator for Launcher options.
     */
    public class Options
        {
        /**
         * Construct a holder for command-line options.
         */
        public Options()
            {
            addOption("help"   , Form.Name, false, "Displays this help message");
            addOption("v"      , Form.Name, false, "Enables \"verbose\" logging and messages");
            addOption("verbose", Form.Name, false, "Enables \"verbose\" logging and messages");
            }

        /**
         * @return the containing Launcher
         */
        public Launcher launcher()
            {
            return Launcher.this;
            }

        /**
         * Determine the options that are available for the Launcher.
         *
         * @return a Map containing the named options that are available, and the Form and other
         *         info for each
         */
        public Map<String, Option> options()
            {
            return m_mapOptions;
            }

        /**
         * (Internal) Add meta-data for an option that is supported for this command line tool.
         *
         * @param sName   the command line switch ("-X" would be passed as "X")
         * @param form    the form of the data for the option (or {@link Form#Name} for a switch)
         * @param fMulti  pass true if the option can appear multiple times on the command line
         * @param sDesc   a human-readable description of the option, for the help display
         */
        protected void addOption(String sName, Form form, boolean fMulti, String sDesc)
            {
            assert sName != null;
            assert form != null;

            var prev = options().put(sName, new Option(sName, form, fMulti, sDesc));
            assert prev == null;

            assert sName.equals(ArgV) == (form == Form.AsIs);
            if (form == Form.AsIs)
                {
                assert !m_fArgV;
                assert fMulti;
                m_fArgV = true;
                }
            }

        /**
         * Determine the form of the specified option.
         *
         * @param sName  the name of the option
         *
         * @return the form of the option, or null if the option does not exist
         */
        public Form formOf(String sName)
            {
            Option opt = options().get(sName);
            return opt == null ? null : opt.form();
            }

        /**
         * Determine if the specified option can be specified more than once.
         *
         * @param sName  the name of the option
         *
         * @return true iff the specified option can be specified more than once
         */
        public boolean allowMultiple(String sName)
            {
            Option opt = options().get(sName);
            return (opt != null && opt.isMulti());
            }

        /**
         * Determine the values of the various options for the Launcher.
         *
         * @return a Map containing the options that are set, and what the value is for each
         */
        public Map<String, Object> values()
            {
            return m_mapValues;
            }

        /**
         * Determine if an option has already specified.
         *
         * @param sName  the name of the option
         *
         * @return true iff the option has already been specified
         */
        public boolean specified(String sName)
            {
            return values().containsKey(sName);
            }

        /**
         * Register that the specified option is specified.
         *
         * @param sName  the name of the option
         *
         * @return true if the option is accepted; false if specifying the option is an error
         */
        public boolean specify(String sName)
            {
            boolean fMulti;
            if (formOf(sName) == Form.Name &&
                ((fMulti = allowMultiple(sName)) || !values().containsKey(sName)))
                {
                store(sName, fMulti, "specified");
                return true;
                }
            else
                {
                return false;
                }
            }

        /**
         * Register a value for the specified option.
         *
         * @param sName  the name of the option
         * @param f      the value for the option
         *
         * @return true if the value is accepted; false if the value represents an error
         */
        public boolean specify(String sName, boolean f)
            {
            boolean fMulti;
            if (formOf(sName) == Form.Boolean &&
                ((fMulti = allowMultiple(sName)) || !values().containsKey(sName)))
                {
                store(sName, fMulti, f);
                return true;
                }
            else
                {
                return false;
                }
            }

        /**
         * Register a value for the specified option.
         *
         * @param sName  the name of the option
         * @param n      the value for the option
         *
         * @return true if the value is accepted; false if the value represents an error
         */
        public boolean specify(String sName, int n)
            {
            boolean fMulti;
            if (formOf(sName) == Form.Int &&
                ((fMulti = allowMultiple(sName)) || !values().containsKey(sName)))
                {
                store(sName, fMulti, n);
                return true;
                }
            else
                {
                return false;
                }
            }

        /**
         * Register a value for the specified option.
         *
         * @param sName  the name of the option
         * @param s      the value for the option
         *
         * @return true if the value is accepted; false if the value represents an error
         */
        public boolean specify(String sName, String s)
            {
            boolean fMulti;
            if (formOf(sName) == Form.String &&
                ((fMulti = allowMultiple(sName)) || !values().containsKey(sName)))
                {
                store(sName, fMulti, s);
                return true;
                }
            else
                {
                return false;
                }
            }

        /**
         * Register a value for the specified option.
         *
         * @param sName  the name of the option
         * @param file   the value for the option
         *
         * @return true if the value is accepted; false if the value represents an error
         */
        public boolean specify(String sName, File file)
            {
            boolean fMulti;
            if (formOf(sName) == Form.File &&
                ((fMulti = allowMultiple(sName)) || !values().containsKey(sName)))
                {
                store(sName, fMulti, file);
                return true;
                }
            else
                {
                return false;
                }
            }

        /**
         * Register a value for the specified option.
         *
         * @param sName      the name of the option
         * @param listFiles  the value for the option
         *
         * @return true if the value is accepted; false if the value represents an error
         */
        public boolean specify(String sName, List<File> listFiles)
            {
            boolean fMulti;
            if (formOf(sName) == Form.Repo &&
                ((fMulti = allowMultiple(sName)) || !values().containsKey(sName)))
                {
                store(sName, fMulti, listFiles);
                return true;
                }
            else
                {
                return false;
                }
            }

        /**
         * Parse the command line arguments into
         *
         * @param asArgs  the command line arguments to parse
         *
         * @return true iff help should be shown
         */
        public boolean parse(String[] asArgs)
            {
            boolean fHelp = false;

            if (asArgs == null)
                {
                return fHelp;
                }

            String              sPrev    = null;
            Map<String, Option> mapNames = options();

            NextArg: for (int i = 0, c = asArgs.length; i < c; ++i)
                {
                String sArg = asArgs[i];
                assert sArg != null;
                if (sArg.length() == 0)
                    {
                    continue;
                    }

                if (sPrev == null)
                    {
                    if (sArg.charAt(0) == '-')
                        {
                        // there are several possibilities:
                        // 1) for any argument:
                        //    -arg                      // no value ("specified" for Name form)
                        //    -arg=value                // '=' delimiter between arg and value
                        //    -arg value                // value is in the next arg
                        // 2) for single-character arguments (imagine that -A -B -C are all legal)
                        //    -A -B -C                  // no value ("specified" for Name form)
                        //    -ABC                      // no value ("specified" for Name form)
                        //    -Avalue                   // value for Int, File, FilePath forms
                        int     cch   = sArg.length();
                        int     ofEq  = sArg.indexOf('=');
                        boolean fEq   = ofEq >= 0;
                        String  sName = sArg.substring(1, fEq ? ofEq : cch);
                        String  sVal  = fEq ? sArg.substring(ofEq+1) : null;

                        if (sName.length() == 0)
                            {
                            log(Severity.FATAL, "Missing argument name. (Name is \"\".)");
                            }

                        if (sName.equals("help"))
                            {
                            fHelp = true;
                            continue;
                            }

                        boolean fFirst = true;
                        while (sName.length() > 0)
                            {
                            Option opt     = mapNames.get(sName);
                            String sRemain = "";
                            if (opt == null)
                                {
                                opt = mapNames.get(sName.substring(0, 1));
                                if (opt != null)
                                    {
                                    sName   = sName.substring(0, 1);
                                    sRemain = sName.substring(1);
                                    }
                                }

                            if (opt == null)
                                {
                                log(Severity.ERROR, "Unknown argument: \"-" +
                                        (fFirst ? sName : sName.substring(0, 1)) + '\"');
                                fHelp = true;
                                continue NextArg;
                                }

                            Form form = opt.form();
                            if (form == Form.Name)
                                {
                                // the name is either present or it is not
                                if (specified(sName) && !allowMultiple(sName))
                                    {
                                    log(Severity.WARNING,
                                            "Redundant option argument: \"-" + sName + '\"');
                                    }
                                else
                                    {
                                    specify(sName);
                                    }
                                }
                            else
                                {
                                if (sRemain.length() > 0)
                                    {
                                    if (sVal == null)
                                        {
                                        sPrev = sName;
                                        sArg  = sRemain;
                                        break;
                                        }
                                    else
                                        {
                                        // there is both a postpended value and an "=value"
                                        log(Severity.ERROR, "Illegal value for \"-"
                                                + sName + "\": \"" + sRemain + '=' + sVal + '\"');
                                        }
                                    }
                                else
                                    {
                                    sPrev = sName;
                                    sArg  = sVal;
                                    break;
                                    }
                                }

                            sName  = sRemain;
                            fFirst = false;
                            }
                        }
                    else
                        {
                        Option optTrail = mapNames.get(Trailing);
                        if (optTrail != null && (optTrail.isMulti() || !specified(Trailing)))
                            {
                            sPrev = Trailing;
                            }
                        else
                            {
                            Option optArgV = mapNames.get(ArgV);
                            if (optArgV != null)
                                {
                                // take EVERYTHING, and take it AS IS
                                List<String> listArgs = new ArrayList<>(c - i);
                                for (int iCopy = i; iCopy < c; ++iCopy)
                                    {
                                    listArgs.add(asArgs[iCopy]);
                                    }
                                store(ArgV, true, listArgs);
                                break NextArg;
                                }
                            else
                                {
                                log(Severity.ERROR,
                                        "Unsupported argument: " + quotedString(sArg));
                                fHelp = true;
                                }
                            }
                        }
                    }

                if (sPrev != null && sArg != null)
                    {
                    // this arg is a "option value" portion of some previous "option name"
                    Form    form   = formOf(sPrev);
                    boolean fMulti = allowMultiple(sPrev);
                    Object  oVal   = null;
                    assert form != null && form != Form.Name;
                    switch (form)
                        {
                        case Boolean:
                            if (sArg.length() == 1)
                                {
                                switch (sArg.charAt(0))
                                    {
                                    case 'T': case 't':
                                    case 'Y': case 'y':
                                    case '1':
                                        oVal = true;
                                        break;

                                    case 'F': case 'f':
                                    case 'N': case 'n':
                                    case '0':
                                        oVal = false;
                                        break;
                                    }
                                }
                            else if (sArg.equalsIgnoreCase("true") || sArg.equalsIgnoreCase("yes"))
                                {
                                oVal = true;
                                }
                            else if (sArg.equalsIgnoreCase("false") || sArg.equalsIgnoreCase("no"))
                                {
                                oVal = true;
                                }
                            break;

                        case Int:
                            try
                                {
                                oVal = Integer.valueOf(sArg);
                                }
                            catch (NumberFormatException e) {}
                            break;

                        case String:
                            if (sArg.length() == 0)
                                {
                                oVal = "";
                                }
                            else if (sArg.charAt(0) == '\"')
                                {
                                if (sArg.length() >= 2 && sArg.charAt(sArg.length()-1) == '\"')
                                    {
                                    sArg = sArg.substring(1, sArg.length()-1);
                                    }
                                }
                            else
                                {
                                oVal = sArg;
                                }
                            break;

                        case File:
                            if (sArg.length() > 0)
                                {
                                List<File> listFiles;
                                try
                                    {
                                    listFiles = resolvePath(sArg);
                                    }
                                catch (IOException e)
                                    {
                                    log(Severity.ERROR, "Exception resolving path \"" + sArg + "\": " + e);
                                    break;
                                    }

                                switch (listFiles.size())
                                    {
                                    case 0:
                                        break;

                                    case 1:
                                        oVal = listFiles.get(0);
                                        break;

                                    default:
                                        if (fMulti)
                                            {
                                            oVal = listFiles;
                                            }
                                        else
                                            {
                                            oVal = listFiles.get(0);
                                            log(Severity.ERROR, "Multiple (" + listFiles.size()
                                                    + ") files specified for \"" + sPrev
                                                    + "\", but only one file allowed");
                                            }
                                        break;
                                    }
                                }
                            break;

                        case Repo:
                            if (sArg.length() > 0)
                                {
                                List<File> repo = new ArrayList<>();
                                for (String sPath : parseDelimitedString(sArg, File.pathSeparatorChar))
                                    {
                                    List<File> files;
                                    try
                                        {
                                        files = resolvePath(sPath);
                                        }
                                    catch (IOException e)
                                        {
                                        log(Severity.ERROR, "Exception resolving path \""
                                                + sPath + "\": " + e);
                                        continue;
                                        }

                                    if (files.isEmpty())
                                        {
                                        log(Severity.ERROR, "Could not resolve: \"" + sPath + "\"");
                                        }
                                    else
                                        {
                                        for (File file : files)
                                            {
                                            if (file.canRead())
                                                {
                                                repo.add(file);
                                                }
                                            else if (file.exists())
                                                {
                                                log(Severity.ERROR, (file.isDirectory() ? "Directory"
                                                        : "File") + " not readable: \"" + file + "\"");
                                                }
                                            }
                                        }
                                    }
                                oVal = repo;
                                }
                            break;
                        }

                    if (oVal == null)
                        {
                        log(Severity.ERROR, "Illegal " + form.name() + " value: \"" + sArg + '\"');
                        }
                    else
                        {
                        if (!specified(sPrev) || fMulti)
                            {
                            store(sPrev, fMulti, oVal);
                            }
                        else
                            {
                            log(Severity.ERROR, "A value for \"-" + sPrev
                                    + "\" is specified more than once.");
                            }
                        }

                    sPrev = null;
                    }
                }

            if (sPrev != null)
                {
                // a trailing value was required
                log(Severity.ERROR, "Missing value for \"" + sPrev + "\" option");
                }

            return fHelp;
            }

        /**
         * Validate the options once the options have all been registered successfully.
         *
         * @return null on success, or a String to describe the validation error
         */
        public void validate()
            {
            }

        /**
         * Obtain a description for the specified option.
         *
         * @param sName  the option name
         *
         * @return the option description
         */
        public String descriptionFor(String sName)
            {
            Option opt = options().get(sName);
            if (opt == null)
                {
                return null;
                }

            String sDesc = opt.desc();
            return sDesc == null
                    ? opt.form().desc()
                    : sDesc;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder("Options\n    {\n");
            final String sIndent = "    ";
            for (Entry<String, Object> entry : values().entrySet())
                {
                String  sName  = entry.getKey();
                Object  oVal   = entry.getValue();
                Form    form   = formOf(sName);
                boolean fMulti = allowMultiple(sName);

                if (sName.equals(Trailing))
                    {
                    sb.append("    Target(s)");
                    }
                else
                    {
                    sb.append(sIndent)
                      .append('-')
                      .append(sName);
                    }

                if (fMulti || oVal instanceof List)
                    {
                    sb.append(":\n");
                    List list = (List) oVal;
                    int i = 0;
                    for (Object oEach : list)
                        {
                        sb.append(sIndent)
                          .append("   [")
                          .append(i++)
                          .append("]=")
                          .append(stringFor(oEach, form == Form.Repo ? Form.File : form))
                          .append('\n');
                        }
                    }
                else if (form == Form.Name)
                    {
                    sb.append('\n');
                    }
                else
                    {
                    sb.append('=')
                      .append(stringFor(oVal, form))
                      .append('\n');
                    }
                }
            return sb.append("    }").toString();
            }

        // ----- internal ----------------------------------------------------------------------

        /**
         * For options that are allowed to have multiple values, this will accumulate multiple
         * values under a single name in an ArrayList.
         *
         * @param sName   the option name
         * @param fMulti  true if the option can have multiple value
         * @param value   the value to store, or append to the ArrayList for that option name
         *
         */
        protected void store(String sName, boolean fMulti, Object value)
            {
            if (value instanceof List)
                {
                values().compute(sName, (k, v) ->
                    {
                    ArrayList list = v == null ? new ArrayList() : (ArrayList) v;
                    list.addAll((List) value);
                    return list;
                    });
                }
            else if (fMulti)
                {
                values().compute(sName, (k, v) ->
                    {
                    ArrayList list = v == null ? new ArrayList() : (ArrayList) v;
                    list.add(value);
                    return list;
                    });
                }
            else
                {
                values().putIfAbsent(sName, value);
                }
            }

        // ----- error handling ----------------------------------------------------------------

        /**
         * @return true if a verbose option has been specified
         */
        boolean isVerbose()
            {
            return  specified("v") || specified("verbose");
            }

        /**
         * Determine if a message or error of a particular severity should be displayed.
         *
         * @param sev  the severity to evaluate
         *
         * @return true if an error of that severity should be displayed
         */
        boolean isBadEnoughToPrint(Severity sev)
            {
            return isVerbose() || sev.compareTo(Severity.WARNING) >= 0;
            }

        /**
         * Determine if a message or error of a particular severity should cause the program to
         * exit.
         *
         * @param sev  the severity to evaluate
         *
         * @return true if an error of that severity should exit the program
         */
        boolean isBadEnoughToAbort(Severity sev)
            {
            return sev.compareTo(Severity.ERROR) >= 0;
            }

        // ----- fields ------------------------------------------------------------------------

        /**
         * The configured map options.
         */
        private Map<String, Option> m_mapOptions  = new HashMap<>();

        /**
         * The values of the various command line options.
         */
        private ListMap<String, Object> m_mapValues = new ListMap<>();

        /**
         * Set to true if an "AsIs" option is present.
         */
        private boolean m_fArgV;
        }


    // ----- repository management -----------------------------------------------------------------

    /**
     * Configure the library repository that is used to load module dependencies without compiling
     * them. The library repository is always writable, to allow the repository to cache modules
     * as they are linked together; use {@link #extractBuildRepo(ModuleRepository)} to get the
     * build repository from the library repository.
     *
     * @param path  a previously validated path of module directories and/or files
     *
     * @return the library repository, including a build repository at the head of the repo
     */
    protected ModuleRepository configureLibraryRepo(List<File> path)
        {
        if (path == null || path.isEmpty())
            {
            // this is the easiest way to deliver an empty repository
            return makeBuildRepo();
            }

        ModuleRepository[] repos = new ModuleRepository[path.size() + 1];
        repos[0] = makeBuildRepo();
        for (int i = 0, c = path.size(); i < c; ++i)
            {
            File file = path.get(i);
            repos[i + 1] = file.isDirectory()
                ? new DirRepository(file, true)
                : new FileRepository(file, true);
            }
        return new LinkedRepository(true, repos);
        }

    /**
     * Factory method for a BuildRepository.
     *
     * @return a new BuildRepository
     */
    protected BuildRepository makeBuildRepo()
        {
        return new BuildRepository();
        }

    /**
     * Obtain the BuildRepository from the library repository.
     *
     * @param repoLib the previously configured library repository
     *
     * @return the BuildRepository
     */
    protected BuildRepository extractBuildRepo(ModuleRepository repoLib)
        {
        if (repoLib instanceof BuildRepository)
            {
            return (BuildRepository) repoLib;
            }

        LinkedRepository repoLinked = (LinkedRepository) repoLib;
        return (BuildRepository) repoLinked.asList().get(0);
        }

    /**
     * Configure the repository that is used to store modules  dependencies without compiling
     * them. The library repository is always writable, to allow the repository to cache modules
     * as they are linked together; use {@link #extractBuildRepo(ModuleRepository)} to get the
     * build repository from the library repository.
     *
     * @param fileDest  a previously validated destination for the module(s), or null to use the
     *                  current directory
     *
     * @return a module repository that will store to the specified destination
     */
    protected ModuleRepository configureResultRepo(File fileDest)
        {
        fileDest = resolveOptionalLocation(fileDest);
        return fileDest.isDirectory()
                ? new DirRepository (fileDest, false)
                : new FileRepository(fileDest, false);
        }

    /**
     * Force load and link whatever modules are required by the compiler and/or runtime system.
     *
     * @param reposLib  the repository to use, as it would be returned from
     *                  {@link #configureLibraryRepo}
     */
    protected void prelinkSystemLibraries(ModuleRepository reposLib)
        {
        // note: this all assumes that the read-through option on LinkedRepository is being used
        ModuleStructure moduleEcstasy = reposLib.loadModule(Constants.ECSTASY_MODULE);
        if (moduleEcstasy == null)
            {
            log(Severity.FATAL, "Unable to load module: " + Constants.ECSTASY_MODULE);
            }
        ModuleStructure moduleNative  = reposLib.loadModule(Constants.PROTOTYPE_MODULE);
        if (moduleNative == null)
            {
            log(Severity.FATAL, "Unable to load module: " + Constants.PROTOTYPE_MODULE);
            }

        FileStructure structEcstasy = moduleEcstasy.getFileStructure();
        FileStructure structNative  = moduleNative .getFileStructure();

        // link the modules
        if (structEcstasy != null)
            {
            String sMissing = structEcstasy.linkModules(reposLib, false);
            if (sMissing != null)
                {
                log(Severity.FATAL, "Unable to link module " + Constants.ECSTASY_MODULE
                        + " due to missing module:" + sMissing);
                }
            }

        if (structNative != null)
            {
            String sMissing = structNative.linkModules(reposLib, false);
            if (sMissing != null)
                {
                log(Severity.FATAL, "Unable to link module " + Constants.PROTOTYPE_MODULE
                        + " due to missing module:" + sMissing);
                }
            }
        }


    // ----- file management -----------------------------------------------------------------------

    /**
     * Validate that the contents of the path are existent directories and/or .xtc files.
     *
     * @param listPath
     */
    public void validateModulePath(List<File> listPath)
        {
        for (File file : listPath)
            {
            String sDisp = "File or directory";
            if (file.isDirectory())
                {
                sDisp = "Directory";
                }
            else if (file.isFile())
                {
                sDisp = "File";
                }

            if (!file.exists())
                {
                log(Severity.ERROR, "File or directory \"" + file + "\" does not exist");
                }
            else if (!file.canRead())
                {
                log(Severity.ERROR, sDisp + " \"" + file + "\" does not exist");
                }
            else if (file.isFile() && !file.getName().endsWith(".xtc"))
                {
                log(Severity.WARNING, "File \"" + file + "\" does not have the \".xtc\" extension");
                }
            }
        }

    /**
     * Validate that the specified file can be used as a source file or directory for .x file(s).
     *
     * @param file  the file or directory to read source code from
     */
    public void validateSourceInput(File file)
        {
        // this is expected to be the name of a file to compile
        if (!file.exists())
            {
            log(Severity.ERROR, "No such source file: \"" + file + "\"");
            }
        else if (!file.canRead())
            {
            log(Severity.ERROR, (file.isDirectory() ? "Directory" : "File")
                    + " not readable: \"" + file + "\"");
            }
        else if (file.isFile() && !file.getName().endsWith(".x"))
            {
            log(Severity.WARNING, "Source file does not use \".x\" extension: \"" + file + "\"");
            }
        else if (file.isDirectory())
            {
            if (file.listFiles((File f) -> f.isFile() && f.getName().endsWith(".x")).length == 0)
                {
                log(Severity.ERROR, "Directory contains no source files: \"" + file + "\"");
                }
            }
        }

    /**
     * Validate that the specified file can be used as a destination file or directory for
     * .xtc file(s).
     *
     * @param file    the file or directory to write module(s) to; may be null (which implies some
     *                default)
     * @param fMulti  true indicates that multiple modules will be written
     */
    public void validateModuleOutput(File file, boolean fMulti)
        {
        if (file == null)
            {
            return;
            }

        if (!file.exists())
            {
            File fileParent = file.getParentFile();
            if (fileParent.isDirectory())
                {
                // an error will be reported further down
                file.mkdir();
                }
            }

        if (file.exists() && file.isDirectory())
            {
            if (!file.canWrite())
                {
                log(Severity.ERROR, "Directory \"" + file + "\" can not be written to");
                }
            }
        else if (file.exists() && file.isFile())
            {
            if (!file.getName().endsWith(".xtc"))
                {
                log(Severity.WARNING, "File \"" + file + "\" does not have the \".xtc\" extension");
                }

            if (fMulti)
                {
                log(Severity.ERROR, "The single file \"" + file
                        + "\" is specified, but multiple modules are expected");
                }

            if (!file.canWrite())
                {
                log(Severity.ERROR, "File \"" + file + "\" can not be written to");
                }
            }
        else if (file.canWrite() && (file.getParentFile() == null || file.getParentFile().canWrite())
                && file.getName().endsWith(".xtc"))
            {
            // the file doesn't exist, but we can write it, and it's obviously a file name for a
            // module because it ends with .xtc
            if (fMulti)
                {
                log(Severity.ERROR, "The single file \"" + file
                        + "\" is specified, but multiple modules are expected");
                }
            }
        else
            {
            log(Severity.ERROR, "File or directory \"" + file + "\" can not be written to");
            }
        }

    /**
     * Given a file (or directory) or null, produce a file (or directory) to use. Defaults to the
     * current working directory.
     *
     * @param file  a file, or directory, or null
     *
     * @return a file or directory
     */
    protected File resolveOptionalLocation(File file)
        {
        return file == null
                ? new File(".").getAbsoluteFile()
                : file;
        }

    /**
     * Resolve the specified "path string" into a list of files.
     *
     * @param sPath   the path to resolve, which may be a file or directory name, and may include
     *                wildcards, etc.
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

    /**
     * Produce a string describing the path of the passed file.
     *
     * @param file  the file to render the path of
     *
     * @return a string for display of the file's path
     */
    public static String filePath(File file)
        {
        String sPath = file.getPath();
        String sAbs;
        try
            {
            sAbs = file.getCanonicalPath();
            }
        catch (IOException e)
            {
            sAbs = file.getAbsolutePath();
            }
        return sPath.equals(sAbs)
            ? sPath
            : sPath + " (" + sAbs + ')';
        }

    /**
     * Based on a file, find the file representing the source module.
     *
     * @param file  the file or directory to examine
     *
     * @return the file containing the module, or null
     */
    protected File findModule(File file)
        {
        if (file.isFile())
            {
            // just in case the file is relative to some working
            // directory, resolve its location
            file = file.getAbsoluteFile();

            if (isModule(file))
                {
                return file;
                }

            file = file.getParentFile();
            }

        // we're going to have to walk up the directory tree, so
        // the entire path needs to be resolved
        file = file.getAbsoluteFile();

        while (file != null && file.isDirectory())
            {
            File moduleFile = new File(file, "module.x");
            if (moduleFile.exists() && moduleFile.isFile())
                {
                if (isModule(moduleFile))
                    {
                    return moduleFile;
                    }
                }

            file = file.getParentFile();
            }

        return null;
        }

    /**
     * Check if the specified file contains a module.
     *
     * @param file  the file (NOT a directory) to examine
     *
     * @return true iff the file declares a module
     */
    protected boolean isModule(File file)
        {
        if (!file.getName().endsWith(".x"))
            {
            return false;
            }

        return getModuleName(file) != null;
        }

    /**
     * Check if the specified file contains a module and if so, return the module's name.
     *
     * @param file  the file (NOT a directory) to examine
     *
     * @return the module's name if the file declares a module; null otherwise
     */
    public String getModuleName(File file)
        {
        assert file.isFile() && file.canRead();
        log(Severity.INFO, "Parsing file: " + file);

        try
            {
            Source source  = new Source(file, 0);
            Parser parser  = new Parser(source, ErrorListener.BLACKHOLE);
            return parser.parseModuleNameIgnoreEverythingElse();
            }
        catch (CompilerException e)
            {
            log(Severity.ERROR, "An exception occurred parsing \"" + file + "\": " + e);
            }
        catch (IOException e)
            {
            log(Severity.ERROR, "An exception occurred reading \"" + file + "\": " + e);
            }

        return null;
        }

    /**
     * Select modules to target for source code processing.
     *
     * @param listSources  a list of source locations
     *
     * @return a list of "module files", each representing a module's source code
     */
    protected List<File> selectTargets(List<File> listSources)
        {
        List<File> listResult = new ArrayList<>();

        // default (if no source is specified)
        if (listSources.isEmpty())
            {
            listSources = Collections.singletonList(new File("module.x"));
            }

        Set<File> setDups = null;
        for (File file : listSources)
            {
            File moduleFile = findModule(file);
            if (moduleFile == null)
                {
                log(Severity.ERROR, "Unable to find module source for file: " + file);
                }
            else if (listResult.contains(moduleFile))
                {
                if (setDups == null)
                    {
                    setDups = new HashSet<>();
                    }
                if (!setDups.contains(moduleFile))
                    {
                    log(Severity.WARNING, "Module source for file was specified multiple times: " + file);
                    setDups.add(moduleFile);
                    }
                }
            else
                {
                listResult.add(moduleFile);
                }
            }

        return listResult;
        }

    /**
     * Determine if the module on disk is up to date vis-a-vis the source code.
     *
     * @param nodeSourceTree      the source code
     * @param fileModuleLocation  the location of the compiled module (directory or file)
     *
     * @return true iff the date/time on the compiled module is up to date compared to the source
     */
    protected boolean moduleUpToDate(Node nodeSourceTree, File fileModuleLocation)
        {
        String sModule    = nodeSourceTree.name();
        File   fileModule = fileModuleLocation.isDirectory()
                ? new File(fileModuleLocation, sModule + ".xtc")
                : fileModuleLocation;
        return fileModule.isFile() && fileModule.exists()
                && fileModule.lastModified() > nodeSourceTree.lastModified();
        }

    /**
     * When working with a source code tree, and given a "module file" such as returned from
     * {@link #findModule(File)}, produce a source tree of the desired processing stage.
     *
     * @param fileModule  a file as returned from {@link #findModule(File)}
     * @param desired     the desired stage of processing: Parsed, Named, or Linked
     *
     * @return the root {@link Node} of the tree
     */
    protected Node loadSourceTree(File fileModule, Stage desired)
        {
        Node nodeModule = buildSourceTree(null, fileModule);
        nodeModule.logErrors();
        checkErrors();

        if (desired.compareTo(Stage.Parsed) >= 0)
            {
            nodeModule.parse();
            nodeModule.logErrors();
            checkErrors();
            }

        if (desired.compareTo(Stage.Named) >= 0)
            {
            nodeModule.registerNames();
            nodeModule.logErrors();
            checkErrors();
            }

        if (desired.compareTo(Stage.Linked) >= 0)
            {
            nodeModule.linkParseTrees();
            nodeModule.logErrors();
            checkErrors();
            }

        return nodeModule;
        }

    /**
     * Build a tree of source files that compose an Ecstasy module, or any sub-package thereof.
     *
     * @param parent  the parent node
     * @param file    a module file, or a directory that is part of a module
     *
     * @return a node iff there is anything "there" to compile, otherwise null
     */
    protected Node buildSourceTree(DirNode parent, File file)
        {
        DirNode node;
        if (file.isDirectory())
            {
            // we're parsing a sub-directory looking for source files
            // (and sub-directories)
            assert parent != null;
            node = makeDirNode(parent, file);
            File filePkg = new File(file, "package.x");
            if (filePkg.exists() && filePkg.isFile())
                {
                node.configureSource(filePkg, makeFileNode(parent, filePkg));
                }
            }
        else if (file.getName().equalsIgnoreCase("module.x"))
            {
            // this is the module root
            assert parent == null;
            node = makeDirNode(null, file.getParentFile());
            node.configureSource(file, makeFileNode(node, file));
            }
        else
            {
            // this is the entire module
            assert parent == null;
            return makeFileNode(null, file);
            }

        for (File fileChild : node.file().listFiles())
            {
            if (fileChild.isDirectory())
                {
                DirNode nodeChild = (DirNode) buildSourceTree(node, fileChild);
                if (nodeChild != null)
                    {
                    node.packageNodes().add(nodeChild);
                    }
                }
            else
                {
                if (node.sourceFile() != null && fileChild.equals(node.sourceFile()))
                    {
                    // this is the module.x or package.x file
                    continue;
                    }

                String sChild = fileChild.getName();
                if (!sChild.endsWith(".x"))
                    {
                    continue;
                    }

                if (sChild.equalsIgnoreCase("module.x") || sChild.equalsIgnoreCase("package.x"))
                    {
                    log(Severity.ERROR, "Illegal file encountered: " + fileChild);
                    continue;
                    }

                // it's a source file
                node.classNodes().put(fileChild, makeFileNode(node, fileChild));
                }
            }

        return node.sourceFile() == null && node.classNodes().isEmpty()
                && node.packageNodes().isEmpty() ? null : node;
        }

    /**
     * Flush errors from the specified nodes, and then check for errors globally.
     *
     * @param nodes  the nodes to flush
     */
    protected void flushAndCheckErrors(Node[] nodes)
        {
        for (Node node : nodes)
            {
            node.logErrors();
            }
        checkErrors();
        }

    /**
     * Determine if the specified module node represents a system module.
     *
     * @param nodeModule  a module node
     *
     * @return true iff the module node is for the Ecstasy or native prototype module
     */
    public boolean isSystemModule(Node nodeModule)
        {
        assert nodeModule.parent() == null;
        assert nodeModule.stage().compareTo(Stage.Named) >= 0;

        String sModule = nodeModule.name();
        return sModule.equals(Constants.ECSTASY_MODULE) || sModule.equals(Constants.PROTOTYPE_MODULE);
        }

    /**
     * Represents either a module/package or a class source node.
     */
    public abstract class Node
        {
        /**
         * Construct a Node.
         *
         * @param parent  the parent node
         * @param file    the file that this node will represent
         */
        public Node(DirNode parent, File file)
            {
            // at least one of the parameters is required
            assert parent != null || file != null;

            m_parent       = parent;
            m_file         = file;
            m_lastModified = file == null ? parent.m_lastModified : file.lastModified();
            }

        /**
         * @return the parent of this node
         */
        public DirNode parent()
            {
            return m_parent;
            }

        /**
         * @return the depth of this node
         */
        public int depth()
            {
            return parent() == null ? 0 : 1 + parent().depth();
            }

        /**
         * @return the file that this node represents
         */
        public File file()
            {
            return m_file;
            }

        /**
         * @return the date/time value when this node was last modified
         */
        public long lastModified()
            {
            return m_lastModified;
            }

        /**
         * @return the stage of the node
         */
        public Stage stage()
            {
            return m_stage;
            }

        /**
         * Load and parse the source code, as necessary.
         */
        public abstract void parse();

        /**
         * Collect the various top-level type names within the module.
         */
        public abstract void registerNames();

        /**
         * Link the various nodes of the module together
         */
        public void linkParseTrees()
            {
            }

        /**
         * @return the name of this node
         */
        public abstract String name();

        /**
         * @return a descriptive name for this node
         */
        public abstract String descriptiveName();

        /**
         * @return the parsed AST from this node
         */
        public abstract Statement ast();

        /**
         * @return the type (from the parsed AST) of this node
         */
        public abstract TypeCompositionStatement type();

        /**
         * @return the list containing any errors accumulated on (or under) this node
         */
        public abstract ErrorList errs();

        /**
         * @return log any errors accumulated on (or under) this node
         */
        public abstract void logErrors();

        // ----- fields ------------------------------------------------------------------------

        /**
         * The parent node, or null.
         */
        protected DirNode m_parent;

        /**
         * The file that this node is based on.
         */
        protected File    m_file;

        /**
         * Stage progression.
         */
        protected Stage   m_stage = Stage.Init;

        /**
         * A cached, last-modified date/time.
         */
        protected long    m_lastModified;
        }

    /**
     * Virtual factory: DirNode.
     *
     * @param parent  the parent node
     * @param file    the directory
     *
     * @return the new DirNode
     */
    public DirNode makeDirNode(DirNode parent, File file)
        {
        return new DirNode(parent, file);
        }

    /**
     * A DirNode represents a directory, which corresponds to a module or package.
     */
    public class DirNode
            extends Node
        {
        /**
         * Construct a DirNode.
         *
         * @param parent  the parent node
         * @param file    the directory that this node will represent
         */
        protected DirNode(DirNode parent, File file)
            {
            super(parent, file);
            }

        /**
         * Configure the source code for the package implementation itself.
         *
         * @param fileSrc  the file for the package.x file (or null if it does not exist)
         * @param nodeSrc  the node representing the package.x contents
         */
        void configureSource(File fileSrc, FileNode nodeSrc)
            {
            m_fileSrc = fileSrc;
            m_nodeSrc = nodeSrc;
            }

        /**
         * @return the simple package name, or if this is a module, the fully qualified module name
         */
        @Override
        public String name()
            {
            String sName = null;

            if (sourceNode() != null)
                {
                sName = sourceNode().name();
                }

            if (sName == null)
                {
                sName = file().getParent();
                }

            return sName == null ? "?" : sName;
            }

        @Override
        public String descriptiveName()
            {
            if (parent() == null)
                {
                return "module " + name();
                }

            StringBuilder sb = new StringBuilder();
            sb.append("package ")
              .append(name());

            DirNode node = parent();
            while (node.parent() != null)
                {
                sb.insert(8, node.name() + '.');
                node = node.parent();
                }

            return sb.toString();
            }

        /**
         * Parse this node and all nodes it contains.
         */
        @Override
        public void parse()
            {
            if (m_stage == Stage.Init)
                {
                long lModified = m_file.lastModified();

                if (m_nodeSrc == null)
                    {
                    // provide a default implementation
                    assert m_parent != null;
                    m_nodeSrc = makeFileNode(this, "package " + file().getName() + "{}");
                    }
                else
                    {
                    lModified = Math.max(lModified, m_nodeSrc.m_lastModified);
                    }
                m_nodeSrc.parse();

                for (FileNode cmpFile : m_mapClzNodes.values())
                    {
                    cmpFile.parse();
                    lModified = Math.max(lModified, cmpFile.m_lastModified);
                    }

                for (DirNode child : m_listPkgNodes)
                    {
                    child.parse();
                    lModified = Math.max(lModified, child.m_lastModified);
                    }

                m_stage        = Stage.Parsed;
                m_lastModified = lModified;
                }
            }

        /**
         * Go through all the packages and types in this package and register their names.
         */
        public void registerNames()
            {
            assert stage().compareTo(Stage.Parsed) >= 0;

            if (stage() == Stage.Parsed)
                {
                // code was created by the parse phase if there was none
                assert sourceNode() != null;

                sourceNode().registerNames();

                for (FileNode clz : classNodes().values())
                    {
                    clz.registerNames();
                    registerName(clz.name(), clz);
                    }

                for (DirNode pkg : packageNodes())
                    {
                    pkg.registerNames();
                    registerName(pkg.name(), pkg);
                    }

                m_stage = Stage.Named;
                }
            }

        /**
         * Register a node under a specified name.
         *
         * @param name  a name that must not conflict with any other child's name; if null, the
         *              request is ignored because it is assumed that an error has already been
         *              raised
         * @param node  the child node to register with the specified name
         */
        public void registerName(String name, Node node)
            {
            if (name != null)
                {
                if (children().containsKey(name))
                    {
                    log(Severity.ERROR, "Duplicate name \"" + name + "\" detected in " + descriptiveName());
                    }
                else
                    {
                    children().put(name, node);
                    }
                }
            }

        @Override
        public void linkParseTrees()
            {
            Node nodePkg = sourceNode();
            if (nodePkg == null)
                {
                Launcher.this.log(Severity.ERROR, "No package node for " + descriptiveName());
                }
            else
                {
                TypeCompositionStatement typePkg = nodePkg.type();

                for (FileNode nodeClz : classNodes().values())
                    {
                    typePkg.addEnclosed(nodeClz.ast());
                    }

                for (DirNode nodeNestedPkg : packageNodes())
                    {
                    // nest the package within this package
                    typePkg.addEnclosed(nodeNestedPkg.sourceNode().ast());

                    // recursively nest the classes and packages of the nested package within it
                    nodeNestedPkg.linkParseTrees();
                    }
                }
            }

        @Override
        public Statement ast()
            {
            return sourceNode() == null ? null : sourceNode().ast();
            }

        @Override
        public TypeCompositionStatement type()
            {
            return sourceNode() == null ? null : sourceNode().type();
            }

        @Override
        public ErrorList errs()
            {
            if (sourceNode() != null)
                {
                return sourceNode().errs();
                }

            return null;
            }

        @Override
        public void logErrors()
            {
            if (sourceNode() != null)
                {
                sourceNode().logErrors();
                }

            for (FileNode clz : classNodes().values())
                {
                clz.logErrors();
                }

            for (DirNode pkg : packageNodes())
                {
                pkg.logErrors();
                }
            }

        /**
         * @return the "package.x" or "module.x" file as appropriate in this directory, or null if
         *         none
         */
        public File sourceFile()
            {
            return m_fileSrc;
            }

        /**
         * @return the corresponding node for the {@link #sourceFile()}
         */
        public Node sourceNode()
            {
            return m_nodeSrc;
            }

        /**
         * @return the list of child nodes that are packages
         */
        public List<DirNode> packageNodes()
            {
            return m_listPkgNodes;
            }

        /**
         * @return the map containing all class nodes by file
         */
        public ListMap<File, FileNode> classNodes()
            {
            return m_mapClzNodes;
            }

        /**
         * @return the map containing all children by name
         */
        public Map<String, Node> children()
            {
            return m_mapChildren;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();
            sb.append(name())
              .append(": ")
              .append(sourceFile() == null ? "(no package.x)" : sourceFile().getName());

            for (Map.Entry<File, FileNode> entry : classNodes().entrySet())
                {
                File     file = entry.getKey();
                FileNode node = entry.getValue();

                sb.append("\n |- ")
                  .append(node == null ? file.getName() : node);
                }

            List<DirNode> pkgs = packageNodes();
            for (int i = 0, c = pkgs.size(); i < c; ++i)
                {
                DirNode pkg = pkgs.get(i);
                sb.append("\n |- ")
                  .append(indentLines(pkg.toString(), (i == c - 1 ? "    " : " |  ")).substring(4));
                }

            return sb.toString();
            }

        // ----- fields ------------------------------------------------------------------------

        /**
         * The module.x or package.x file, or null.
         */
        protected File                    m_fileSrc;

        /**
         * The node for the module.x or package.x file, or null.
         */
        protected FileNode                m_nodeSrc;

        /**
         * The classes nested directly in the module or package.
         */
        protected ListMap<File, FileNode> m_mapClzNodes  = new ListMap<>();

        /**
         * The packages nested directly in the module or package.
         */
        protected List<DirNode>           m_listPkgNodes = new ArrayList<>();

        /**
         * The child nodes (both packages and classes) nested directly in the module or package.
         */
        protected Map<String, Node>       m_mapChildren  = new HashMap<>();
        }

    /**
     * Virtual factory: FileNode.
     *
     * @param parent  the parent node
     * @param file    the source file
     *
     * @return the new FileNode
     */
    public FileNode makeFileNode(DirNode parent, File file)
        {
        return new FileNode(parent, file);
        }

    /**
     * Virtual factory: FileNode.
     *
     * @param parent  the parent node
     * @param source  the source code
     *
     * @return the new FileNode
     */
    public FileNode makeFileNode(DirNode parent, String source)
        {
        return new FileNode(parent, source);
        }

    /**
     * A FileNode represents an individual ".x" source file.
     */
    public class FileNode
            extends Node
            implements ErrorListener
        {
        /**
         * Construct a FileNode.
         *
         * @param parent  the parent node
         * @param file    the file that this node will represent
         */
        public FileNode(DirNode parent, File file)
            {
            super(parent, file);

            try
                {
                m_source = new Source(file, depth());
                }
            catch (IOException e)
                {
                Launcher.this.log(Severity.ERROR, "Failure reading: " + file);
                }
            }

        /**
         * Construct a FileNode from the code that would have been in a file.
         *
         * @param code  the source code
         */
        public FileNode(DirNode parent, String code)
            {
            super(parent, null);

            m_source = new Source(code, depth());
            }

        @Override
        public String name()
            {
            return m_stmtType == null
                    ? file().getName()
                    : type().getName();
            }

        @Override
        public String descriptiveName()
            {
            return m_stmtType == null
                    ? file().getAbsolutePath()
                    : type().getCategory().getId().TEXT + ' ' + name();
            }

        /**
         * @return the source code for this node
         */
        public Source source()
            {
            return m_source;
            }

        @Override
        public void parse()
            {
            if (m_stage == Stage.Init)
                {
                Source source = source();
                try
                    {
                    m_stmtAST = new Parser(source, this).parseSource();
                    }
                catch (CompilerException e)
                    {
                    if (!hasSeriousErrors())
                        {
                        log(Severity.FATAL, Parser.FATAL_ERROR, null,
                            source, source.getPosition(), source.getPosition());
                        }
                    }
                m_stage = Stage.Parsed;
                }
            }

        @Override
        public void registerNames()
            {
            assert stage().compareTo(Stage.Parsed) >= 0;

            if (stage() == Stage.Parsed)
                {
                // this can only happen if the errors were ignored
                Statement stmt = ast();
                if (stmt != null)
                    {
                    if (stmt instanceof TypeCompositionStatement)
                        {
                        m_stmtType = (TypeCompositionStatement) stmt;
                        }
                    else
                        {
                        List<Statement> list = ((StatementBlock) stmt).getStatements();
                        m_stmtType = (TypeCompositionStatement) list.get(list.size() - 1);
                        }
                    }

                m_stage = Stage.Named;
                }
            }

        @Override
        public Statement ast()
            {
            return m_stmtAST;
            }

        @Override
        public TypeCompositionStatement type()
            {
            return m_stmtType;
            }

        @Override
        public boolean log(ErrorInfo err)
            {
            return errs().log(err);
            }

        @Override
        public boolean isAbortDesired()
            {
            return m_errs != null && m_errs.isAbortDesired();
            }

        @Override
        public boolean hasSeriousErrors()
            {
            return m_errs != null && m_errs.hasSeriousErrors();
            }

        @Override
        public boolean hasError(String sCode)
            {
            return m_errs != null && m_errs.hasError(sCode);
            }

        @Override
        public ErrorList errs()
            {
            ErrorList errs = m_errs;
            if (errs == null)
                {
                m_errs = errs = new ErrorList(341);
                }
            return errs;
            }

        @Override
        public void logErrors()
            {
            ErrorList errs = m_errs;
            if (errs != null)
                {
                Launcher.this.log(errs);
                m_errs.clear();
                }
            }

        @Override
        public String toString()
            {
            return name() + '(' + stage().name() + (stage().compareTo(Stage.Parsed) >= 0
                    && ast() == null ? ", parse failed" : "") + ')';
            }

        // ----- fields ------------------------------------------------------------------------

        /**
         * The source code for the file node, if it has been loaded.
         */
        protected Source                   m_source;

        /**
         * The error list that buffers errors for the file node, if any.
         */
        protected ErrorList                m_errs;

        /**
         * The AST for the source code, once it has been parsed.
         */
        protected Statement                m_stmtAST;

        /**
         * The primary class (or other type) that the source file declares.
         */
        protected TypeCompositionStatement m_stmtType;
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * The various forms of command-line options can take:
     *
     * <ul><li><tt>Name</tt>      - either the option is specified or it is not;
     *                              e.g. "{@code -verbose}"
     * </li><li><tt>Boolean</tt>  - an explicitly boolean option;
     *                              e.g. "{@code -suppressBeep=False}"
     * </li><li><tt>Int</tt>      - an integer valued option;
     *                              e.g. "{@code -limit=5}" or "{@code -limit 5}"
     * </li><li><tt>String</tt>   - a String valued option (useful when no either form works);
     *                              e.g. "{@code -name="Bob"}" or "{@code -name "Bob"}"
     * </li><li><tt>File</tt>     - a File valued option;
     *                              e.g. "{@code -src=./My.x}" or "{@code -src ./My.x}"
     * </li><li><tt>FileList</tt> - a colon-delimited search path valued option;
     *                              e.g. "{@code -L ~/lib:./lib:./}" or "{@code -L~/lib:./}"
     * </li><li><tt>AsIs</tt>     - an AsIs valued option is a String that is not modified, useful
     *                              when being passed on to a further "argv-aware" program
     *                              e.g. "{@code xec MyApp.xtc -o=7 -X="hi"} -> {@code -o=7 -X="hi"}
     * </li></ul>
     */
    protected enum Form
        {
        Name("Switch"),
        Boolean,
        Int,
        String,
        File,
        Repo('\"' + java.io.File.pathSeparator + "\"-delimited File list"),
        AsIs;

        Form()
            {
            this(null);
            }

        Form(String desc)
            {
            DESC = desc;
            }

        public String desc()
            {
            return DESC == null ? name() : DESC;
            }

        private final String DESC;
        }

    /**
     * This is the name used for an option that does not have a name. It is called "trailing"
     * because it comes at the end of a sequence of options, such as the sequence of file names
     * at the end of the command: "{@code xtc -o ../build -verbose MyApp.x MyTest.x}".
     * <p/>
     * If a Launcher supports trailing files, for example, then the {@link Options#options()} method
     * should return a map containing an entry whose key is {@code Trailing} and whose value is
     * {@link Form#File}, with {@code allowMultiple(Trailing)} returning {@code true}.
     */
    protected static final String Trailing = "...";

    /**
     * This is the name used for an option that represents "the remainder of the options".
     * <p/>
     * To use this option, the Launcher must support no "Trailing", or a single "Trailing", but not
     * multiple "Trailing" values.
     */
    protected static final String ArgV = "[]";

    /**
     * Represents a single available command line option.
     */
    public static class Option
        {
        public Option(String sName, Form form, boolean fMulti, String sDesc)
            {
            assert sName != null && sName.length() > 0;
            assert form != null;

            m_sName  = sName;
            m_form   = form;
            m_fMulti = fMulti;
            m_sDesc  = sDesc;
            }

        public String name()
            {
            return m_sName;
            }

        public Form form()
            {
            return m_form;
            }

        public boolean isMulti()
            {
            return m_fMulti;
            }

        public String desc()
            {
            return m_sDesc;
            }

        private String  m_sName;
        private Form    m_form;
        private boolean m_fMulti;
        private String  m_sDesc;
        }

    enum Stage {Init, Parsed, Named, Linked}


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The command-line arguments.
     */
    private String[] m_asArgs;

    /**
     * The parsed options.
     */
    private Options m_options;

    /**
     * The worst severity issue encountered thus far.
     */
    private Severity m_sevWorst = Severity.NONE;

    /**
     * The number of times that errors have been suspended without being resumed.
     */
    private int m_cSuspended;
    }

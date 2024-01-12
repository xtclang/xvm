package org.xvm.tool;


import java.io.File;
import java.io.IOException;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.xvm.asm.FileRepository;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.compiler.BuildRepository;

import org.xvm.tool.ModuleInfo.Node;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;
import org.xvm.util.Severity;


import static org.xvm.tool.ModuleInfo.isExplicitCompiledFile;

import static org.xvm.util.Handy.parseDelimitedString;
import static org.xvm.util.Handy.quotedString;
import static org.xvm.util.Handy.resolveFile;
import static org.xvm.util.Handy.toPathString;


/**
 * The "launcher" commands:
 *
 * <ul><li> <code>xcc</code> <i>("ecstasy")</i> routes to {@link Compiler}
 * </li><li> <code>xec</code> <i>("exec")</i> routes to {@link Runner}
 * </li></ul>
 */
public abstract class Launcher
        implements ErrorListener
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
                System.out.println("Note: Command name \"xtc\" will be renamed to \"xcc\"");
                // TODO JK this spot is reserved for you to build a do-it-all "go"-style command
                // fall through (until the new xtc command is in place)
            case "xcc":
                Compiler.main(argv);
                break;

            case "xec":
                Runner.main(argv);
                break;

            default:
                System.err.println("Command name \"" + cmd + "\" is not supported");
                break;
            }
        }

    /**
     * @param asArgs  the Launcher's command-line arguments
     */
    public Launcher(String[] asArgs, Console console)
        {
        m_asArgs  = asArgs;
        m_console = console == null ? DefaultConsole : console;
        }

    /**
     * Execute the Launcher tool.
     */
    public void run()
        {
        Options opts = options();

        boolean fHelp = opts.parse(m_asArgs);
        if (Runtime.version().version().get(0) < 21)
            {
            log(Severity.INFO, "The suggested minimum JVM version is 21; this JVM version ("
                    + Runtime.version() + ") appears to be older");
            }
        else
            {
            log(Severity.INFO, "JVM version: " + Runtime.version());
            }
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
            m_console.log(sev, sMsg);
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
    public void out()
        {
        m_console.out();
        }

    /**
     * Print the String value of some object to the terminal.
     */
    public void out(Object o)
        {
        m_console.out(o);
        }

    /**
     * Print a blank line to the terminal.
     */
    public void err()
        {
        m_console.err();
        }

    /**
     * Print the String value of some object to the terminal.
     */
    public void err(Object o)
        {
        m_console.err(o);
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
        throw new LauncherException(fError);
        }

    /**
     * Display a help message describing how to use this command-line tool.
     */
    public void displayHelp()
        {
        out();
        out(desc());
        out();

        Options             options   = options();
        Map<String, Option> mapOpts   = options.options();
        String[]            asName    = mapOpts.keySet().toArray(Handy.NO_ARGS);
        int                 maxSyntax = Arrays.stream(asName).map(n ->
                                mapOpts.get(n).syntax().length()).max(Integer::compareTo).get();
        Arrays.sort(asName, (s1, s2) ->
            {
            if (s1.equals(s2))
                {
                return 0;
                }
            if (s1.equals(ArgV))
                {
                return 1;
                }
            if (s2.equals(ArgV))
                {
                return -1;
                }
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

        out("Options:");
        HashSet<String> alreadyDisplayed = new HashSet<>();
        for (String sName : asName)
            {
            Option opt = mapOpts.get(sName);
            if (opt.posixName() != null && alreadyDisplayed.contains(opt.posixName()) ||
                opt.linuxName() != null && alreadyDisplayed.contains(opt.linuxName()))
                {
                continue;
                }

            String sMsg = sName.equals(Trailing) && !mapOpts.containsKey(ArgV)
                    ? ArgV
                    : opt.syntax();

            StringBuilder sb = new StringBuilder();
            sb.append("  ")
              .append(sMsg);

            for (int i = 0, c = maxSyntax - sMsg.length() + 4; i < c; ++i)
                {
                sb.append(' ');
                }

            sb.append(options.descriptionFor(sName));
            out(sb);
            alreadyDisplayed.add(sName);
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
                return toPathString((File) oVal);

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
                    sb.append(toPathString(file));
                    }
                return sb.toString();

            default:
                throw new IllegalStateException();
            }
        }


    // ----- ErrorListener interface ---------------------------------------------------------------

    @Override public boolean log(ErrorInfo err)
        {
        log(err.getSeverity(), err.toString());
        return isAbortDesired();
        }

    @Override public boolean isAbortDesired()
        {
        return options().isBadEnoughToAbort(m_sevWorst);
        }

    @Override public boolean hasSeriousErrors()
        {
        return m_sevWorst.compareTo(Severity.ERROR) >= 0;
        }

    @Override public boolean isSilent()
        {
        return errorsSuspended();
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
            addOption(null, "help",    Form.Name, false, "Displays this help message");
            addOption("v",  "verbose", Form.Name, false, "Enables \"verbose\" logging and messages");
            addOption(null, "version", Form.Name, false, "Displays the Ecstasy runtime version");
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
         * @param sPosix  the POSIX-like command line switch ("-X" would be passed as "X")
         * @param sLinux  the Linux-like command line switch ("--test" would be passed as "test")
         * @param form    the form of the data for the option (or {@link Form#Name} for a switch)
         * @param fMulti  pass true if the option can appear multiple times on the command line
         * @param sDesc   a human-readable description of the option, for the help display
         */
        protected void addOption(String sPosix, String sLinux, Form form, boolean fMulti, String sDesc) {
            assert sPosix != null || sLinux != null;
            assert sPosix == null || sPosix.length() == 1 || sPosix.equals(ArgV) || sPosix.equals(Trailing);
            assert !Trailing.equals(sPosix) || sLinux == null;
            assert !ArgV    .equals(sPosix) || sLinux == null;
            assert form != null;

            Option opt = new Option(sPosix, sLinux, form, fMulti, sDesc);
            if (sPosix != null)
                {
                var prev = options().put(sPosix, opt);
                assert prev == null;
                }
            if (sLinux != null && !sLinux.equals(sPosix))
                {
                var prev = options().put(sLinux, opt);
                assert prev == null;
                }

            assert ArgV.equals(sPosix) == (form == Form.AsIs);
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
         * @return true if a "show version" option has been specified
         */
        boolean isShowVersion()
            {
            return specified("version");
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
                if (sArg.isEmpty())
                    {
                    continue;
                    }

                if (sPrev == null)
                    {
                    if (sArg.charAt(0) == '-')
                        {
                        // there are several possibilities:
                        // 1) for any single "posix" argument:
                        //    -arg                      // no value ("specified" for Name form)
                        //    -arg=value                // '=' delimiter between arg and value
                        //    -arg value                // value is in the next arg
                        // 2) for any single "linux" argument:
                        //    --arg                     // no value ("specified" for Name form)
                        //    --arg=value               // '=' delimiter between arg and value
                        //    --arg value               // value is in the next arg
                        // 3) for multiple "posix" arguments (imagine that -A -B -C are all legal)
                        //    -ABC                      // no value ("specified" for Name form)
                        int     cch    = sArg.length();
                        int     ofEq   = sArg.indexOf('=');
                        boolean fEq    = ofEq >= 0;
                        boolean fLinux = cch > 1 && sArg.charAt(1) == '-';
                        boolean fPosix = !fLinux;
                        String  sOpts  = sArg.substring(fPosix ? 1 : 2, fEq ? ofEq : cch);
                        String  sOrig  = sOpts;
                        String  sVal   = fEq ? sArg.substring(ofEq+1) : null;

                        if (sOpts.isEmpty())
                            {
                            log(Severity.FATAL, "Missing argument name. (Name is \"\".)");
                            }

                        if (fLinux && "help".equals(sOpts))
                            {
                            fHelp = true;
                            continue; // NextArg;
                            }

                        do
                            {
                            String sOpt;
                            if (fPosix)
                                {
                                sOpt  = sOpts.substring(0,1);
                                sOpts = sOpts.substring(1);
                                if ("?".equals(sOpt))
                                    {
                                    fHelp = true;
                                    continue;
                                    }
                                }
                            else
                                {
                                sOpt = sOpts;
                                }

                            Option opt = mapNames.get(sOpt);
                            if (opt == null || !sOpt.equals(fPosix ? opt.posixName() : opt.linuxName()))
                                {
                                fHelp = true;
                                if (opt == null && !mapNames.containsKey(sOrig))
                                    {
                                    log(Severity.ERROR, "Unknown argument: \"" + (fPosix ? "-" : "--") + sOpt + '\"');
                                    }
                                else if (fPosix)
                                    {
                                    log(Severity.ERROR, "Option \"-" + sOrig + "\" must use two preceding hyphens: \"--" + sOrig + "\"");
                                    }
                                else
                                    {
                                    log(Severity.ERROR, "Option \"--" + sOrig + "\" must use only one preceding hyphen: \"-" + sOrig + "\"");
                                    }
                                continue NextArg;
                                }

                            Form   form  = opt.form();
                            String sName = opt.simplestName();
                            if (form == Form.Name)
                                {
                                // the name is either present or it is not
                                if (specified(sName) && !allowMultiple(sName))
                                    {
                                    log(Severity.WARNING,
                                            "Redundant option argument: \"-" + sOpt + '\"');
                                    }
                                else
                                    {
                                    specify(sName);
                                    }
                                }
                            else
                                {
                                if (sPrev != null)
                                    {
                                    log(Severity.ERROR, "Options \"-" + sPrev + "\" and \"-" + sOpt
                                            + "\" cannot appear in the same cluster, since they"
                                            + " both require a trailing value");
                                    }
                                else
                                    {
                                    sPrev = sName;
                                    sArg  = sVal;
                                    }
                                }
                            }
                        while (fPosix && !sOpts.isEmpty());
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
                                listArgs.addAll(Arrays.asList(asArgs).subList(i, c));
                                store(ArgV, true, listArgs);
                                break;
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
                    // this arg is an "option value" portion of some previous "option name"
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
                            else if ("true".equalsIgnoreCase(sArg) || "yes".equalsIgnoreCase(sArg))
                                {
                                oVal = true;
                                }
                            else if ("false".equalsIgnoreCase(sArg) || "no".equalsIgnoreCase(sArg))
                                {
                                oVal = true;
                                }
                            break;

                        case Int:
                            try
                                {
                                oVal = Integer.valueOf(sArg);
                                }
                            catch (NumberFormatException ignore) {}
                            break;

                        case String:
                            if (sArg.isEmpty())
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
                            if (!sArg.isEmpty())
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
                            if (!sArg.isEmpty())
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
                                                        : "File") + " not readable: " + file);
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
            return specified("v") || specified("verbose");
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
        private final Map<String, Option> m_mapOptions  = new HashMap<>();

        /**
         * The values of the various command line options.
         */
        private final ListMap<String, Object> m_mapValues = new ListMap<>();

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
        if (repoLib instanceof BuildRepository repoBuild)
            {
            return repoBuild;
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
        fileDest = resolveFile(fileDest);
        return fileDest.isDirectory()
                ? new DirRepository (fileDest, false)
                : new FileRepository(fileDest, false);
        }

    /**
     * Force load and link whatever modules are required by the compiler.
     *
     * Note: This implementation assumes that the read-through option on LinkedRepository is being
     * used.
     *
     * @param reposLib  the repository to use, as it would be returned from
     *                  {@link #configureLibraryRepo}
     */
    protected void prelinkSystemLibraries(ModuleRepository reposLib)
        {
        ModuleStructure moduleEcstasy = reposLib.loadModule(Constants.ECSTASY_MODULE);
        if (moduleEcstasy == null)
            {
            log(Severity.FATAL, "Unable to load module: " + Constants.ECSTASY_MODULE);
            }

        FileStructure structEcstasy = moduleEcstasy.getFileStructure();
        if (structEcstasy != null)
            {
            String sMissing = structEcstasy.linkModules(reposLib, false);
            if (sMissing != null)
                {
                log(Severity.FATAL, "Unable to link module " + Constants.ECSTASY_MODULE
                    + " due to missing module:" + sMissing);
                }
            }

        ModuleStructure moduleTurtle = reposLib.loadModule(Constants.TURTLE_MODULE);
        if (moduleTurtle == null)
            {
            log(Severity.FATAL, "Unable to load module: " + Constants.TURTLE_MODULE);
            }

        FileStructure structTurtle = moduleTurtle .getFileStructure();
        if (structTurtle != null)
            {
            String sMissing = structTurtle.linkModules(reposLib, false);
            if (sMissing != null)
                {
                log(Severity.FATAL, "Unable to link module " + Constants.TURTLE_MODULE
                    + " due to missing module:" + sMissing);
                }
            }
        }

    /**
     * Display "xdk version" string.
     *
     * @param reposLib  the repository that contains the Ecstasy library
     */
    protected void showSystemVersion(ModuleRepository reposLib)
        {
        String sVer = "???";
        try
            {
            sVer = reposLib.loadModule(Constants.ECSTASY_MODULE).getVersionString();
            }
        catch (Exception ignore) {}
        out("xdk version " + (sVer == null ? "<none>" : sVer)
                + " (" + Constants.VERSION_MAJOR_CUR + "." + Constants.VERSION_MINOR_CUR + ")");
        }


    // ----- file management -----------------------------------------------------------------------

    /**
     * Obtain the module information based on the specified file(s).
     *
     * @param fileSpec       the file or directory to analyze, which may or may not exist
     * @param resourceSpecs  (optional) an array of files and/or directories which represent (in
     *                       aggregate) the resource path; null indicates that the default resources
     *                       location should be used, while an empty array explicitly indicates
     *                       that there is no resource path; as provided to the compiler using
     *                       the "-rp" command line switch
     * @param binarySpec     (optional) the file or directory which represents the target of the
     *                       binary; as provided to the compiler using the "-o" command line switch
     */
    public ModuleInfo ensureModuleInfo(File fileSpec, File[] resourceSpecs, File binarySpec)
        {
        boolean fCache = (resourceSpecs == null || resourceSpecs.length == 0) && binarySpec == null;
        if (fCache && moduleCache != null)
            {
            ModuleInfo info = moduleCache.get(fileSpec);
            if (info != null)
                {
                return info;
                }
            }

        ModuleInfo info = new ModuleInfo(fileSpec, resourceSpecs, binarySpec);

        if (fCache)
            {
            if (moduleCache == null)
                {
                moduleCache = new HashMap<>();
                }
            moduleCache.put(fileSpec, info);
            }

        return info;
        }

    /**
     * Validate that the contents of the path are existent directories and/or .xtc files.
     *
     * @param listPath
     */
    public void validateModulePath(List<File> listPath)
        {
        for (File file : listPath)
            {
            String sMsg = "File or directory";
            if (file.isDirectory())
                {
                sMsg = "Directory";
                }
            else if (file.isFile())
                {
                sMsg = "File";
                }

            if (!file.exists())
                {
                log(Severity.ERROR, "File or directory \"" + file + "\" does not exist");
                }
            else if (!file.canRead())
                {
                log(Severity.ERROR, sMsg + " \"" + file + "\" does not exist");
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
     *
     * @return the validated file or directory to read source code from
     */
    public File validateSourceInput(File file)
        {
        // this is expected to be the name of a file to compile
        if (!file.exists() || file.isDirectory())
            {
            try
                {
                ModuleInfo info = ensureModuleInfo(file, null, null);
                File srcFile = info == null ? null : info.getSourceFile();
                if (srcFile == null || !srcFile.exists())
                    {
                    log(Severity.ERROR, "Failed to locate the module source code for: " + file);
                    }
                }
            catch (RuntimeException e)
                {
                log(Severity.ERROR, "Failed to identify the module for: " + file + " (" + e + ")");
                }
            }
        else if (!file.canRead())
            {
            log(Severity.ERROR, "File not readable: " + file);
            }
        else if (!file.getName().endsWith(".x"))
            {
            log(Severity.WARNING, "Source file does not have a \".x\" extension: " + file);
            }
        return file;
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

        boolean fSingle = isExplicitCompiledFile(file.getName());
        if (fSingle && fMulti)
            {
            log(Severity.ERROR, "The single file " + file
                    + " is specified, but multiple modules are expected");
            return;
            }

        File dir = fSingle ? file.getParentFile() : file;
        if (!dir.exists())
            {
            log(Severity.INFO, "Creating directory " + dir);
            // ignore any errors here; errors would end up being reported further down
            dir.mkdirs();
            }

        if (file.exists())
            {
            if (!file.isDirectory())
                {
                if (!fSingle)
                    {
                    log(Severity.WARNING, "File " + file + " does not have the \".xtc\" extension");
                    }

                if (fMulti)
                    {
                    log(Severity.ERROR, "The single file " + file
                            + " is specified, but multiple modules are expected");
                    }

                if (!file.canWrite())
                    {
                    log(Severity.ERROR, "File " + file + " can not be written to");
                    }
                }
            }
        else if (!dir.exists())
            {
            log(Severity.ERROR, "Directory " + dir + " is missing");
            }
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
    protected static List<File> resolvePath(String sPath)
            throws IOException
        {
        List<File> files = new ArrayList<>();

        if (sPath.length() >= 2 && sPath.charAt(0) == '~'
                && (sPath.charAt(1) == '/' || sPath.charAt(1) == File.separatorChar))
            {
            sPath = System.getProperty("user.home") + File.separatorChar + sPath.substring(2);
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
     * Select modules to target for source code processing.
     *
     * @param listSources    a list of source locations
     * @param resourceSpecs  an optional array of resource locations
     * @param outputSpec     an optional location for storing the compilation result
     *
     * @return a list of "module files", each representing a module's source code
     */
    protected List<ModuleInfo> selectTargets(List<File> listSources, File[] resourceSpecs, File outputSpec)
        {
        ListMap<File, ModuleInfo> mapResults = new ListMap<>();

        Set<File> setDups = null;
        for (File file : listSources)
            {
            ModuleInfo info    = null;
            File       srcFile = null;
            try
                {
                info = ensureModuleInfo(file, resourceSpecs, outputSpec);
                if (info != null)
                    {
                    srcFile = info.getSourceFile();
                    }
                }
            catch (IllegalStateException | IllegalArgumentException e)
                {
                String msg = e.getMessage();
                log(Severity.ERROR, "Could not find module information for " + toPathString(file)
                        + " (" + (msg == null ? "Reason unknown" : msg) + ")");
                }
            if (srcFile == null)
                {
                log(Severity.ERROR, "Unable to find module source for file: " + file);
                }
            else if (mapResults.containsKey(srcFile))
                {
                if (setDups == null)
                    {
                    setDups = new HashSet<>();
                    }
                if (!setDups.contains(srcFile))
                    {
                    log(Severity.WARNING, "Module source was specified multiple times: " + srcFile);
                    setDups.add(srcFile);
                    }
                }
            else
                {
                mapResults.put(srcFile, info);
                }
            }

        return new ArrayList<>(mapResults.values());
        }

    /**
     * Flush errors from the specified nodes, and then check for errors globally.
     *
     * @param nodes  the nodes to flush
     */
    protected void flushAndCheckErrors(Node[] nodes)
        {
        if (nodes != null)
            {
            for (Node node : nodes)
                {
                if (node != null)
                    {
                    node.logErrors(this);
                    }
                }
            }
        checkErrors();
        }

    /**
     * Clean up any transient state
     */
    protected void reset()
        {
        m_sevWorst   = Severity.NONE;
        m_cSuspended = 0;
        moduleCache  = null;
        }


    // ----- Console -------------------------------------------------------------------------------

    /**
     * An interface representing this tool's interaction with the terminal.
     */
    public interface Console
        {
        /**
         * Print a blank line to the terminal.
         */
        default void out()
            {
            out("");
            }

        /**
         * Print the String value of some object to the terminal.
         */
        default void out(Object o)
            {
            System.out.println(o);
            }

        /**
         * Print a blank line to the terminal.
         */
        default void err()
            {
            err("");
            }

        /**
         * Print the String value of some object to the terminal.
         */
        default void err(Object o)
            {
            System.err.println(o);
            }

        /**
         * Log a message of a specified severity.
         *
         * @param sev   the severity (may indicate an error)
         * @param sMsg  the message or error to display
         */
        default void log(Severity sev, String sMsg)
            {
            out(sev.desc() + ": " + sMsg);
            }
        }

    /**
     * RuntimeException thrown upon a launcher failure.
     */
    static public class LauncherException
            extends RuntimeException
        {
        /**
         * @param error  true to abort with an error status
         */
        public LauncherException(boolean error)
            {
            super();

            this.error = error;
            }

        public final boolean error;
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * The default Console implementation.
     */
    public static final Console DefaultConsole = new Console() {};

    /**
     * The various forms of command-line options can take:
     *
     * <ul><li><tt>Name</tt>      - either the option is specified or it is not;
     *                              e.g. "{@code --verbose}"
     * </li><li><tt>Boolean</tt>  - an explicitly boolean option;
     *                              e.g. "{@code --suppressBeep=False}"
     * </li><li><tt>Int</tt>      - an integer valued option;
     *                              e.g. "{@code --limit=5}" or "{@code --limit 5}"
     * </li><li><tt>String</tt>   - a String valued option (useful when no either form works);
     *                              e.g. "{@code --name="Bob"}" or "{@code --name "Bob"}"
     * </li><li><tt>File</tt>     - a File valued option;
     *                              e.g. "{@code --src=./My.x}" or "{@code --src ./My.x}"
     * </li><li><tt>FileList</tt> - a colon-delimited search path valued option;
     *                              e.g. "{@code -L ~/lib:./lib:./}" or "{@code -L~/lib:./}"
     * </li><li><tt>AsIs</tt>     - an AsIs valued option is a String that is not modified, useful
     *                              when being passed on to a further "argv-aware" program
     *                              e.g. "{@code xec MyApp.xtc -o=7 -X="q"} -> {@code -o=7 -X="q"}"
     * </li></ul>
     */
    public enum Form
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
     * at the end of the command: "{@code xcc -o ../build --verbose MyApp.x MyTest.x}".
     * <p/>
     * If a Launcher supports trailing files, for example, then the {@link Options#options()} method
     * should return a map containing an entry whose key is {@code Trailing} and whose value is
     * {@link Form#File}, with {@code allowMultiple(Trailing)} returning {@code true}.
     */
    protected static final String Trailing = "<_>";

    /**
     * This is the name used for an option that represents "the remainder of the options".
     * <p/>
     * To use this option, the Launcher must support no "Trailing", or a single "Trailing", but not
     * multiple "Trailing" values.
     */
    protected static final String ArgV = "...";

    /**
     * Represents a single available command line option.
     */
    public static class Option
        {
        public Option(String sPosix, String sLinux, Form form, boolean fMulti, String sDesc)
            {
            assert sPosix != null || sLinux != null;
            assert (sPosix == null || !sPosix.isEmpty()) && (sLinux == null || !sLinux.isEmpty());
            assert form != null;

            m_sPosix = sPosix;
            m_sLinux = sLinux;
            m_form   = form;
            m_fMulti = fMulti;
            m_sDesc  = sDesc;
            }

        public String posixName()
            {
            return m_sPosix;
            }

        public String linuxName()
            {
            return m_sLinux;
            }

        public String simplestName()
            {
            return m_sPosix == null ? m_sLinux : m_sPosix;
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

        public String syntax()
            {
            if (m_sSyntax == null)
                {
                if(Trailing.equals(m_sPosix) || ArgV.equals(m_sPosix))
                    {
                    m_sSyntax = m_sPosix;
                    }
                else
                    {
                    m_sSyntax = (m_sPosix == null ? "" : "-" + m_sPosix)
                            +   (m_sPosix == null || m_sLinux == null ? "" : " | ")
                            +   (m_sLinux == null ? "" : "--" + m_sLinux);
                    }
                }
            return m_sSyntax;
            }

        @Override public String toString()
            {
            return syntax() + " : " + m_form + (m_fMulti ? "*" : "");
            }

        private final     String  m_sPosix;
        private final     String  m_sLinux;
        private transient String  m_sSyntax;
        private final     Form    m_form;
        private final     boolean m_fMulti;
        private final     String  m_sDesc;
        }

    protected enum Stage {Init, Parsed, Named, Linked}

    /**
     * Unknown fatal error. {0}
     */
    public static final String FATAL_ERROR      = "LAUNCHER-01";
    /**
     * Duplicate name "{0}" detected in {1}
     */
    public static final String DUP_NAME         = "LAUNCHER-02";
    /**
     * No package node for {0}
     */
    public static final String MISSING_PKG_NODE = "LAUNCHER-03";
    /**
     * Failure reading: {0}
     */
    public static final String READ_FAILURE     = "LAUNCHER-04";


    // ----- fields --------------------------------------------------------------------------------

    /**
     * A representation of the Console (e.g. terminal) that this tool is running in.
     */
    protected final Console m_console;

    /**
     * The command-line arguments.
     */
    protected final String[] m_asArgs;

    /**
     * The parsed options.
     */
    private Options m_options;

    /**
     * The worst severity issue encountered thus far.
     */
    protected Severity m_sevWorst = Severity.NONE;

    /**
     * The number of times that errors have been suspended without being resumed.
     */
    protected int m_cSuspended;

    protected Map<File, ModuleInfo> moduleCache;
    }
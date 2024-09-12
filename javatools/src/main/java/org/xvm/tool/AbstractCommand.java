package org.xvm.tool;


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
import org.xvm.tool.flag.Flag;
import org.xvm.tool.flag.FlagSet;
import org.xvm.util.ListMap;
import org.xvm.util.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

import static org.xvm.tool.ModuleInfo.isExplicitCompiledFile;
import static org.xvm.util.Handy.resolveFile;
import static org.xvm.util.Handy.toPathString;


/**
 * The base class for "launcher" commands.
 * <p/>
 * A launcher command has a name and zero or more command line flags that it accepts.
 * The name of the command is what is used on the command line to execute that command.
 * A command may also have sub commands, for example the "xtc" command has the
 * sub-command "test". A sub command inherits flags from its parent.
 */
public abstract class AbstractCommand
        implements ErrorListener
    {
    /**
     * Create a {@link AbstractCommand}.
     *
     * @param sName    the name of this launcher command
     */
    protected AbstractCommand(String sName)
        {
        this(sName, null);
        }

    /**
     * Create a {@link AbstractCommand}.
     *
     * @param sName    the name of this launcher command
     * @param console  an optional {@link Console} this command should use
     */
    protected AbstractCommand(String sName, Console console)
        {
        name           = sName;
        this.m_console = console == null ? DefaultConsole : console;
        }

    /**
     * Execute this {@link AbstractCommand} command, or sub-command {@link AbstractCommand}.
     *
     * @param sCommand  the name of the command to execute, or {@code null}
     *                  if the command name is in the {@code asArg} array
     * @param listArg   the list of command line arguments
     */
    final void run(String sCommand, List<String> listArg)
        {
        Queue<String> queueArgs = new LinkedList<>();
        if (sCommand != null && !sCommand.isBlank())
            {
            queueArgs.add(sCommand);
            }
        if (listArg != null && !listArg.isEmpty())
            {
            queueArgs.addAll(listArg);
            }
        AbstractCommand command = findSubCommand(queueArgs);
        command.run(queueArgs);
        }

    /**
     * Run this command.
     *
     * @param colArg  the command arguments
     */
    public final void run(Collection<String> colArg)
        {
        try
            {
            FlagSet flags = mergeGlobalFlags();
            flags.parse(colArg);
            if (flags.isShowVersion())
                {
                showSystemVersion(ensureLibraryRepo());
                }
            if (flags.isShowHelp())
                {
                displayHelp();
                // if the help flag was specified then we're done
                return;
                }
            validate(flags);
            process();
            }
        catch (FlagSet.ParseHelpException e)
            {
            // we get here for commands that did not specify the help
            // flag as a valid flag, but it was seen on the command line.
            displayHelp();
            }
        catch (FlagSet.ParseException e)
            {
            throw new RuntimeException("Error " + this, e);
            }
        }

    /**
     * Implemented in subclasses to actually run a specific launcher task.
     */
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

        if (isBadEnoughToPrint(sev))
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
     * Print the String value of some string with optional arguments to the terminal.
     * <p>
     * This method uses {@link String#format(String, Object...)} to create
     * the message to log.
     *
     * @param s      the string template for the message
     * @param aoArg  the arguments to apply to the string template
     */
    public void out(String s, Object... aoArg)
        {
        m_console.out(String.format(s, aoArg));
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
        if (isBadEnoughToAbort(m_sevWorst))
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
        if (fHelp || isBadEnoughToAbort(m_sevWorst))
            {
            abort(isBadEnoughToAbort(m_sevWorst));
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

        out("Usage:");
        out();

        String sCommand = getHelpCommandName();
        out(getUsageLine(sCommand));
        out();

        boolean fCommands = !subCommands.isEmpty();
        if (fCommands)
            {
            out("Available Commands:");
            long nMax = subCommands.keySet().stream().mapToInt(String::length).max().orElse(10);
            String sTemplate = "  %-" + nMax + "s  %s";
            for (Map.Entry<String, AbstractCommand> entry : subCommands.entrySet())
                {
                out(sTemplate, entry.getKey(), entry.getValue().getShortDescription());
                }
            out();
            printFlags("Global Flags:", ensureParentGlobalFlags());
            out("Use \"" + sCommand + " [command] --help\" for more information about a command.");
            }
        else
            {
            printFlags("Flags:", mergeGlobalFlags());
            }
        }

    protected String getHelpCommandName()
        {
        if(parentCommand != null)
            {
            return parentCommand.getHelpCommandName() + " " + name;
            }
        return name;
        }

    protected void printFlags(String sHeading, FlagSet flagSet)
        {
        if (!flagSet.hasNonHiddenFlags())
            {
            return;
            }

        out(sHeading);

        Map<String, Flag<?>> mapFlag   = flagSet.getFormalFlags();
        int                  nMax      = mapFlag.keySet().stream().mapToInt(String::length).max().orElse(10);
        String               sTemplate = "  %s --%-" + nMax + "s  %s";

        for (Map.Entry<String, Flag<?>> entry : mapFlag.entrySet())
            {
            Flag<?> flag   = entry.getValue();
            String  sShort = flag.hasShorthand() ? "-" + flag.getShorthand() + "," : "   ";
            out(sTemplate, sShort, flag.getName(), flag.getUsage());
            }

        out();
        }

    /**
     * Return a one line description of this launcher command.
     *
     * @return a one line description of this launcher command
     */
    protected abstract String getShortDescription();

    /**
     * Return a detailed, possibly multi-line description of this launcher command.
     *
     * @return a detailed, possibly multi-line description of this launcher command
     */
    public abstract String desc();

    /**
     * Return a single line, command line usage example for this
     * launcher command.
     * <p>
     * The {@code sName} parameter is the command name that should be used in the usage
     * example. This is because the name may come from a concatenation of command names.
     * For example the Compiler command could be "xcc" or it could be "xtc build", so
     * this name is passed in by the launcher that is printing the help text.
     * <p>
     * For example:
     * <pre>
     *     return sName + " [options] filename.x"
     * </pre>
     * @param sName  the name of this command
     *
     * @return a single line, command line usage example for this
     *         launcher command
     */
    protected abstract String getUsageLine(String sName);

    // ----- ErrorListener interface ---------------------------------------------------------------

    @Override public boolean log(ErrorInfo err)
        {
        log(err.getSeverity(), err.toString());
        return isAbortDesired();
        }

    @Override public boolean isAbortDesired()
        {
        return isBadEnoughToAbort(m_sevWorst);
        }

    @Override public boolean hasSeriousErrors()
        {
        return m_sevWorst.compareTo(Severity.ERROR) >= 0;
        }

    @Override public boolean isSilent()
        {
        return errorsSuspended();
        }

    protected boolean isBadEnoughToAbort(Severity sev)
        {
        return sev.compareTo(Severity.ERROR) >= 0;
        }

    protected boolean isBadEnoughToPrint(Severity sev)
        {
        return flags.isVerbose() || sev.compareTo(Severity.WARNING) >= 0;
        }

    // ----- sub-commands --------------------------------------------------------------------------

    /**
     * Return the parent of this {@link AbstractCommand} is this command
     * is a sub-command.
     *
     * @return  the parent of this {@link AbstractCommand} or {@code null} if
     *          this command is not a sub-command
     */
    public AbstractCommand getParent()
        {
        return parentCommand;
        }

    /**
     * Add a sub-command to this {@link AbstractCommand}
     *
     * @param launcher the sub-command to add
     *
     * @return this {@link AbstractCommand}
     */
    public AbstractCommand addCommand(AbstractCommand launcher)
        {
        if (launcher == this)
            {
            throw new IllegalArgumentException("Cannot add a launcher to itself");
            }
        launcher.parentCommand = this;
        subCommands.put(launcher.name(), launcher);
        return this;
        }

    /**
     * Add all the sub-commands to this {@link AbstractCommand}
     *
     * @param commands the sub-commands to add
     *
     * @return this {@link AbstractCommand}
     */
    public AbstractCommand addCommands(AbstractCommand... commands)
        {
        for (AbstractCommand launcher : commands)
            {
            addCommand(launcher);
            }
        return this;
        }

    /**
     * Return {@code true} if this {@link AbstractCommand} has sub-commands.
     *
     * @return {@code true} if this {@link AbstractCommand} has sub-commands
     */
    public boolean hasSubCommands()
        {
        return !subCommands.isEmpty();
        }

    /**
     * Return the sub-command with a given name, or {@code null} if this
     * {@link AbstractCommand} has no sub-command with that name.
     *
     * @return the sub-command with a given name, or {@code null} if this
     *         {@link AbstractCommand} has no sub-command with that name.
     */
    public AbstractCommand getSubCommand(String name)
        {
        return subCommands.get(name);
        }

    /**
     * Locate the sub-command to be executed from the command line arguments using the
     * first non-flag argument in the command line.
     * <p>
     * The matching sub-command names will be removed from the {@code queueArgs} list.
     * <p>
     * This method will not return null.
     *
     * @param queueArgs  the command line arguments
     *
     * @return  the first sub-command or this launcher if no sub commands are found
     */
    protected AbstractCommand findSubCommand(Queue<String> queueArgs)
        {
        return findRecursive(this, queueArgs);
        }

    /**
     * Recursively find a sub-command to execute by walking down the
     * hierarchy of sub-commands using the command names from the command
     * line arguments.
     *
     * @param launcher   the launcher containing possible matching sub-commands
     * @param queueArgs  the command line arguments possibly containing a command name to execute
     *
     * @return a sub-command to execute
     */
    private AbstractCommand findRecursive(AbstractCommand launcher, Queue<String> queueArgs)
        {
        if (!launcher.hasSubCommands())
            {
            return launcher;
            }
        Queue<String> queueArgsNoFlags = stripCommandLineFlags(new LinkedList<>(queueArgs), launcher);
        if (queueArgsNoFlags.isEmpty())
            {
            return launcher;
            }
        String          sCommand   = queueArgsNoFlags.poll();
        AbstractCommand subCommand = launcher.getSubCommand(sCommand);
        if (subCommand == null)
            {
            System.err.println("Invalid command name \"" + sCommand + "\"");
            return launcher;
            }
        Iterator<String> it = queueArgs.iterator();
        while (it.hasNext())
            {
            String sArg = it.next();
            if (sArg.equals(sCommand))
                {
                it.remove();
                break;
                }
            }
        return findRecursive(subCommand, queueArgs);
        }

    /**
     * Return a {@link Queue} of command line arguments that have all the "flag"
     * arguments and their values stripped out.
     *
     * @param args      the command line arguments to remove flags from
     * @param launcher  the launcher providing valid flags
     *
     * @return a {@link Queue} of command line arguments that have all the "flag"
     *         arguments and their values stripped out
     */
    private Queue<String> stripCommandLineFlags(Queue<String> args, AbstractCommand launcher)
        {
        if (args.isEmpty())
            {
            return args;
            }

        Queue<String> commands = new LinkedList<>();
        FlagSet       flags    = launcher.mergeGlobalFlags();

        while (!args.isEmpty())
            {
            String s = args.poll();
            if ("--".equals(s))
                {
                // the "--" flag indicates the end of the command line args
                break;
                }
            else if (s.charAt(0) == '-' && s.charAt(1) == '-' && !s.contains("=") && !flags.hasNoArgDefault(s))
                {
                // If '--flag arg' then
                // delete arg from args.
                if (args.size() == 1)
                    {
                    break;
                    }
                else
                    {
                    args.poll();
                    }
                }
            else if (s.charAt(0) == '-' && !s.contains("=") && !flags.hasNoArgDefault(s.charAt(1)))
                {
                // If '-f arg' then
                // delete 'arg' from args or break the loop if len(args) == 1.
                if (args.size() == 1)
                    {
                    break;
                    }
                else
                    {
                    args.poll();
                    }
                }
            else if (s.charAt(0) != '-')
                {
                commands.add(s);
                }
            }

        return commands;
        }

    // ----- options -------------------------------------------------------------------------------

    /**
     * Merge the global options from this command's parents into this command's flags.
     *
     * @return a new {@link FlagSet} with all the merged flags
     */
    protected FlagSet mergeGlobalFlags()
        {
        FlagSet flags = flags();
        ensureParentGlobalFlags().mergeInto(flags);
        return flags;
        }

    /**
     * Returns all the global flags from this command and its parents.
     *
     * @return all the global flags from this command and its parents
     */
    protected FlagSet ensureParentGlobalFlags()
        {
        if (parentGlobalFlags != null)
            {
            return parentGlobalFlags;
            }

        parentGlobalFlags = globalFlags();
        AbstractCommand parent = parentCommand;
        while (parent != null)
            {
            parentGlobalFlags.addFlags(parent.globalFlags());
            parent = parent.parentCommand;
            }
        return parentGlobalFlags;
        }

    /**
     * Return the name of this command.
     *
     * @return the name of this command
     */
    public String name()
        {
        return name;
        }

    /**
     * Return the command line flags for this {@link AbstractCommand}.
     */
    public final FlagSet flags()
        {
        FlagSet flags = this.flags;
        if (flags == null)
            {
            flags = this.flags = instantiateFlags(name);
            }
        return flags;
        }

    /**
     * Return the global command line flags for this {@link AbstractCommand}
     * that will apply to all sub-commands.
     */
    public final FlagSet globalFlags()
        {
        FlagSet flags = globalFlags;
        if (flags == null)
            {
            flags = globalFlags = instantiateGlobalFlags();
            }
        return flags;
        }

    /**
     * Instantiate the {@link FlagSet} for this launcher.
     *
     * @param sName  the name of the flag set
     *
     * @return the {@link FlagSet} for this launcher
     */
    protected abstract FlagSet instantiateFlags(String sName);

    /**
     * Instantiate the global {@link FlagSet} to be applied to any
     * sub-commands of this launcher.
     *
     * @return the global {@link FlagSet} to be applied to any
     *         sub-commands of this launcher
     */
    protected FlagSet instantiateGlobalFlags()
        {
        return new FlagSet();
        }

    /**
     * Validate the options once the flags have all been registered successfully.
     */
    protected void validate(FlagSet flagSet)
        {
        }

    /**
     * Returns {@code true} if the version flag was parsed from the command line.
     *
     * @return {@code true} if the version flag was parsed from the command line
     */
    protected boolean isShowVersion()
        {
        return flags().isShowVersion();
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
        return (BuildRepository) repoLinked.asList().getFirst();
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
     * Lazily configure the library repository.
     *
     * @return the library repository
     */
    protected ModuleRepository ensureLibraryRepo()
        {
        if (repoLib == null)
            {
            log(Severity.INFO, "Creating and pre-populating library and build repositories");
            repoLib = configureLibraryRepo(flags().getModulePath());
            }
        return repoLib;
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
     * @param listPath  the module path to validate
     */
    public void validateModulePath(List<File> listPath) throws LauncherException
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
        if (dir != null && !dir.exists())
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
        else if (dir != null && !dir.exists())
            {
            log(Severity.ERROR, "Directory " + dir + " is missing");
            }
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

    // ----- constants -----------------------------------------------------------------------------

    /**
     * The default Console implementation.
     */
    public static final Console DefaultConsole = new Console() {};

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The sub-commands for this launcher.
     */
    protected final Map<String, AbstractCommand> subCommands = new TreeMap<>();

    /**
     * The name of this command.
     */
    private final String name;

    /**
     * The command line flags that apply to this {@link AbstractCommand}.
     */
    private FlagSet flags;

    /**
     * The global command line flags that apply to this {@link AbstractCommand}
     * and to any sub-commands.
     */
    private FlagSet globalFlags;

    /**
     * The parent command's global command line flags.
     */
    private FlagSet parentGlobalFlags;

    /**
     * A representation of the Console (e.g. terminal) that this tool is running in.
     */
    protected final Console m_console;

    /**
     * The worst severity issue encountered thus far.
     */
    protected Severity m_sevWorst = Severity.NONE;

    /**
     * The number of times that errors have been suspended without being resumed.
     */
    protected int m_cSuspended;

    protected Map<File, ModuleInfo> moduleCache;

    /**
     * The optional parent {@link AbstractCommand} of this {@link AbstractCommand}.
     */
    private AbstractCommand parentCommand;

    /**
     * The lazily instantiated {@link ModuleRepository}.
     */
    protected ModuleRepository repoLib;
    }
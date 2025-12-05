package org.xvm.tool;

import org.xvm.asm.ModuleRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The "Ecstasy" do-anything command.
 */
public class Ecstasy
        extends Launcher {
    /**
     * Entry point from the OS.
     *
     * @param asArg command line arguments
     */
    public static void main(String[] asArg) {
        try {
            System.exit(launch(asArg));
        } catch (LauncherException e) {
            System.exit(e.error ? 1 : 0);
        }
    }

    /**
     * Helper method for external launchers.

     * @param asArg  command line arguments
     *
     * @return the result of the {@link #process()} call
     *
     * @throws LauncherException if an unrecoverable exception occurs
     */
    public static int launch(String[] asArg) throws LauncherException {
        return new Ecstasy(asArg, null).run();
    }

    /**
     * Ecstasy command constructor.
     *
     * @param asArg command line arguments
     */
    public Ecstasy(String[] asArg) {
        this(asArg, null);
    }

    /**
     * Ecstasy command constructor.
     *
     * @param asArg    command line arguments
     * @param console  representation of the terminal within which this command is run
     */
    public Ecstasy(String[] asArg, Console console) {
        super(asArg, console);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected int process() {
        // repository setup
        Options          options  = options();
        ModuleRepository repo     = configureLibraryRepo(options.getModulePath());
        int              exitCode = 1;

        checkErrors();

        boolean fShowVer = options.showVersion();
        if (fShowVer) {
            showSystemVersion(repo);
        }

        List<String> listArgs = (List<String>) options.values().get(ArgV);
        if (listArgs != null && !listArgs.isEmpty()) {
            String sCmd = listArgs.getFirst();
            switch (sCmd) {
                case "build":
                    exitCode = new Compiler(argsWithout("build"), null).run();
                    break;
                case "run":
                    exitCode = new Runner(argsWithout("run"), null).run();
                    break;
                case "test":
                    exitCode = new TestRunner(argsWithout("test"), null).run();
                    break;
                case "help":
                    displayHelp();
                    exitCode = 0;
                default:
                    out("ERROR: Unrecognised XTC sub-command: " + sCmd);
                    out("");
                    displayHelp();
            }
        } else {
            out("ERROR: Missing XTC sub-command");
            out("");
            displayHelp();
        }
        return exitCode;
    }

    private List<String> argsWithout(String sArg) {
        List<String> listArgs = new ArrayList<>(m_listArgs);
        listArgs.remove(sArg);
        return listArgs;
    }

    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc() {
        return """
            Ecstasy command

            Usage:

                xtc <command> <options> ...
            
            Commands:
            
                build  compiles an Ecstasy module (the same as running the xcc command)
                run    executes an Ecstasy module (the same as running the xec command)
                test   executes the tests in an Ecstasy module 
                
            To display help for a command use
            
                xtc <command> --help
            """;
    }

    // ----- options -------------------------------------------------------------------------------

    @Override
    public Options options() {
        return (Options) super.options();
    }

    @Override
    protected Options instantiateOptions() {
        return new Options();
    }
    /**
     * Disassembler command-line options implementation.
     */
    public class Options
            extends Launcher.Options {
        /**
         * Construct the Ecstasy command Options.
         */
        public Options() {
            super();
            addOption("L" ,     null, Form.Repo, true,  "Module path; a \"" + File.pathSeparator + "\"-delimited list of file and/or directory names");
            addOption(ArgV,     null, Form.AsIs, true,  "Command-specific arguments");
        }

        /**
         * @return the list of files in the module path (empty list if none specified)
         */
        @SuppressWarnings("unchecked")
        public List<File> getModulePath() {
            return (List<File>) values().getOrDefault("L", Collections.emptyList());
        }
    }
}
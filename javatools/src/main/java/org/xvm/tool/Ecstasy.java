package org.xvm.tool;

import org.xvm.asm.ModuleRepository;

import java.io.File;
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
            new Ecstasy(asArg).run();
        } catch (LauncherException e) {
            System.exit(e.error ? 1 : 0);
        }
    }

    /**
     * Disassembler constructor.
     *
     * @param asArg command line arguments
     */
    public Ecstasy(String[] asArg) {
        this(asArg, null);
    }

    /**
     * Disassembler constructor.
     *
     * @param asArg    command line arguments
     * @param console  representation of the terminal within which this command is run
     */
    public Ecstasy(String[] asArg, Console console) {
        super(asArg, console);
    }

    @Override
    protected void process() {
        // repository setup
        Options          options = options();
        ModuleRepository repo    = configureLibraryRepo(options.getModulePath());
        checkErrors();

        boolean fShowVer = options.isShowVersion();
        if (fShowVer) {
            showSystemVersion(repo);
        } else {
            displayHelp();
        }
        // TODO JK
    }

    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc() {
        return """
            Ecstasy command

            Usage:

                xtc <options> ...
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
        public List<File> getModulePath() {
            return (List<File>) values().getOrDefault("L", Collections.emptyList());
        }
    }
}
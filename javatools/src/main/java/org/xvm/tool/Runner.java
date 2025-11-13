package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitConnector;

import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.LauncherOptions.RunnerOptions;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;
import org.xvm.util.ListSet;
import org.xvm.util.Severity;

import static org.xvm.tool.ModuleInfo.isExplicitCompiledFile;
import static org.xvm.tool.ModuleInfo.isExplicitEcstasyFile;

import static org.xvm.util.Handy.checkReadable;
import static org.xvm.util.Handy.isPathed;
import static org.xvm.util.Handy.quotedString;


/**
 * The "execute" command:
 *
 *  java org.xvm.tool.Runner [-L repo(s)] [-M method_name] app.xtc [argv]
 *
 * where the default method is "run" with no arguments.
 */
public class Runner extends Launcher<RunnerOptions> {
    /**
     * Entry point from the OS. Delegates to Launcher.
     *
     * @param asArg command line arguments
     */
    public static void main(String[] asArg) {
        Launcher.main(insertCommand("xec", asArg));
    }

    /**
     * Programmatic entry point. Delegates to Launcher.
     *
     * @param asArg command line arguments
     * @param errListener ErrorListener to receive errors
     * @return exit code
     */
    public static int launch(String[] asArg, ErrorListener errListener) {
        return Launcher.launch("xec", asArg, errListener);
    }

    /**
     * Runner constructor for programmatic use.
     *
     * @param options     pre-configured runner options
     * @param console     representation of the terminal within which this command is run, or null
     * @param errListener optional ErrorListener to receive errors, or null for no delegation
     */
    public Runner(RunnerOptions options, Console console, ErrorListener errListener) {
        super(options, console, errListener);
    }

    /**
     * Runner constructor (simplified, for programmatic use).
     *
     * @param options pre-configured runner options
     */
    public Runner(RunnerOptions options) {
        this(options, null, null);
    }

    @Override
    protected int process() {
        // repository setup
        RunnerOptions    options = m_options;
        ModuleRepository repo    = configureLibraryRepo(options.getModulePath());
        checkErrors();

        boolean fShowVer = options.showVersion();
        if (fShowVer) {
            showSystemVersion(repo);
        }

        final File fileSpec = options.getTarget();
        if (fileSpec == null) {
            if (fShowVer) {
                return 0;
            }
            displayHelp();
            return 1;
        }

        String          filePath   = fileSpec.getPath();
        File            fileBin    = null;
        boolean         binExists  = false;
        ModuleStructure module     = null;
        String          binLocDesc;
        if (isExplicitCompiledFile(filePath) && fileSpec.exists()
                && (options.isCompileDisabled() || isPathed(filePath))) {
            // the caller has explicitly specified the exact .xtc file and/or
            fileBin    = fileSpec;
            binExists  = true;
            binLocDesc = "the specified target " + filePath;
        } else if (!isPathed(filePath) && !isExplicitEcstasyFile(filePath)
                && (module = repo.loadModule(filePath)) != null) {
            // use the module we found in the repo
            binLocDesc = "the repository";
        } else {
            ModuleInfo info    = null;
            File       outFile = options.getOutputFile();
            try {
                info = new ModuleInfo(fileSpec, options.deduce(), null, outFile);
            } catch (RuntimeException e) {
                log(Severity.ERROR, "Failed to identify the module for: " + fileSpec + " (" + e + ")");
            }
            checkErrors();

            fileBin   = info.getBinaryFile();
            binExists = fileBin != null && fileBin.exists();

            boolean fCompile = false;
            if (!binExists) {
                String qualName = info.getQualifiedModuleName();
                module = repo.loadModule(qualName);
                if (module == null) {
                    File fileSrc = info.getSourceFile();
                    if (fileSrc != null && fileSrc.exists() && !options.isCompileDisabled()) {
                        log(Severity.INFO, "The compiled module \"" + info.getQualifiedModuleName()
                                + "\" is missing; attempting to compile it from "
                                + info.getSourceFile() + " ....");
                        fCompile = true;
                    } else {
                        Set<String> possibles = null;
                        if (qualName.indexOf('.') < 0) {
                            // the qualified name wasn't qualified; that may have been user input
                            // error; find all of the names that they may have meant to type
                            for (String name : repo.getModuleNames()) {
                                int ofDot = name.indexOf('.');
                                if (ofDot > 0 && name.substring(0, ofDot).equals(qualName)) {
                                    if (possibles == null) {
                                        possibles = new ListSet<>();
                                    }
                                    possibles.add(name);
                                }
                            }
                        }
                        if (possibles == null) {
                            log(Severity.ERROR, "Failed to locate the module for: " + fileSpec);
                        } else if (possibles.size() == 1) {
                            log(Severity.ERROR, "Unable to locate the module for " + fileSpec
                                    + "; did you mean " + quotedString(possibles.iterator().next())
                                    + '?');
                        } else {
                            StringBuilder buf = new StringBuilder();
                            for (String name : possibles) {
                                buf.append(", ")
                                   .append(quotedString(name));
                            }
                            log(Severity.ERROR, "Unable to locate the module for " + fileSpec
                                    + "; did you mean one of: " + buf.substring(2) + '?');
                        }
                    }
                } else {
                    binExists = true;
                }
            }

            if (binExists && !options.isCompileDisabled() && info.getSourceFile() != null
                    && info.getSourceFile().exists() && !info.isUpToDate()) {
                log(Severity.INFO, "The compiled module \"" + info.getQualifiedModuleName()
                        + "\" is out-of-date; recompiling ....");
                fCompile = true;
            }
            checkErrors();

            if (fCompile) {
                // Build CompilerOptions programmatically
                CompilerOptions.Builder builder = new CompilerOptions.Builder();

                List<File> libPath = options.getModulePath();
                for (File libFile : libPath) {
                    builder.modulePath(libFile);
                }

                if (outFile != null) {
                    builder.output(outFile);
                }

                builder.input(fileSpec);

                CompilerOptions compilerOpts = builder.build();

                new Compiler(compilerOpts, m_console, m_errDelegate).run();

                info      = new ModuleInfo(fileSpec, options.deduce(), null, outFile);
                fileBin   = info.getBinaryFile();
                binExists = fileBin != null && fileBin.exists();
                repo      = configureLibraryRepo(libPath);
                module    = repo.loadModule(info.getQualifiedModuleName());
            }

            binLocDesc = info.getBinaryFile().getPath();
        }

        // check if the compiled module file name was specified
        if (module == null && binExists) {
            if (checkReadable(fileBin)) {
                try {
                    try (FileInputStream in = new FileInputStream(fileBin)) {
                        FileStructure struct = new FileStructure(in);
                        module = struct.getModule();
                    }
                } catch (IOException e) {
                    log(Severity.FATAL, "I/O exception (" + e + ") reading module file: " + fileBin);
                    abort(true);
                }
            }
        }

        if (module == null) {
            log(Severity.ERROR, "Missing module for " + fileSpec);
            abort(true);
        } else {
            try {
                repo.storeModule(module);
            } catch (IOException e) {
                log(Severity.FATAL, "I/O exception (" + e + ") storing module file: " + fileSpec);
                abort(true);
            }
            checkErrors();
        }

        String sName = module.getName();
        if (sName.equals(module.getSimpleName())) {
            // quote the "simpleName" to visually differentiate it (there is no qualified name)
            sName = quotedString(sName);
        }

        if (fShowVer) {
            Version ver   = module.getVersion();
            String  sVer  = ver == null ? "<none>" : ver.toString();
            out(sName + " version " + sVer);
        }

        boolean fJit = options.isJit();

        log(Severity.INFO, "Executing " + sName + " from " + binLocDesc);
        try {
            Connector connector = fJit ? new JitConnector(repo) : new Connector(repo);
            connector.loadModule(module.getName());
            connector.start(options.getInjections());

            ConstantPool pool = connector.getConstantPool();
            try (var ignore = ConstantPool.withPool(pool)) {
                String               sMethod    = options.getMethodName();
                Set<MethodStructure> setMethods = connector.findMethods(sMethod);
                if (setMethods.size() != 1) {
                    if (setMethods.isEmpty()) {
                        log(Severity.ERROR, "Missing method \"" + sMethod + "\" in module " + sName);
                    } else {
                        log(Severity.ERROR, "Ambiguous method \"" + sMethod + "\" in module " + sName);
                    }
                    abort(true);
                }

                String[]        asArg       = options.getMethodArgs();
                MethodStructure method      = setMethods.iterator().next();
                TypeConstant    typeStrings = pool.ensureArrayType(pool.typeString());

                switch (method.getRequiredParamCount()) {
                case 0:
                    if (asArg != null) {
                        // the method doesn't require anything, but there are args
                        if (method.getParamCount() > 0) {
                            TypeConstant typeArg = method.getParam(0).getType();
                            if (!typeStrings.isA(typeArg)) {
                                log(Severity.ERROR, "Unsupported argument type \"" +
                                    typeArg.getValueString() + "\" for method \"" + sMethod + "\"");
                                abort(true);
                            }
                        } else {
                            log(Severity.WARNING, "Method \"" + sMethod +
                                "\" does not take any parameters; ignoring the specified arguments");
                        }
                    }
                    break;

                case 1: {
                    TypeConstant typeArg = method.getParam(0).getType();
                    if (!typeStrings.isA(typeArg)) {
                        log(Severity.ERROR, "Unsupported argument type \"" +
                            typeArg.getValueString() + "\" for method \"" + sMethod + "\"");
                        abort(true);
                    }
                    break;
                }

                default:
                    log(Severity.ERROR, "Unsupported method arguments \"" +
                        method.getIdentityConstant().getSignature().getValueString());
                    abort(true);
                }

                connector.invoke0(method, asArg);

                return connector.join();
            }
        } catch (InterruptedException ignore) {
            return 1;
        } catch (Throwable e) {
            e.printStackTrace();
            log(Severity.FATAL, e.toString());
            return 1;
        }
    }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc() {
        return """
            Ecstasy runner:

                Executes an Ecstasy module, compiling it first if necessary.

            Usage:

                xec <options> <modulename>
            
            Also supports any of:
            
                xec <options> <filename>
                xec <options> <filename>.x
                xec <options> <filename>.xtc
            """;
    }


    // ----- options -------------------------------------------------------------------------------

    @Override
    protected void validateOptions() {
        // Validate the -L path of file(s)/dir(s)
        validateModulePath(m_options.getModulePath());
    }
}

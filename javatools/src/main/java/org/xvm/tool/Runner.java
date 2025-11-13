package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitConnector;

import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.LauncherOptions.RunnerOptions;

import org.xvm.util.ListSet;

import static org.xvm.tool.ModuleInfo.isExplicitCompiledFile;
import static org.xvm.tool.ModuleInfo.isExplicitEcstasyFile;
import static org.xvm.util.Handy.checkReadable;
import static org.xvm.util.Handy.isPathed;
import static org.xvm.util.Handy.quotedString;
import static org.xvm.util.Severity.ERROR;
import static org.xvm.util.Severity.FATAL;
import static org.xvm.util.Severity.INFO;
import static org.xvm.util.Severity.WARNING;


/**
 * The "execute" command:
 * <p>
 *  java org.xvm.tool.Runner [-L repo(s)] [-M method_name] app.xtc [argv]
 * <p>
 * where the default method is "run" with no arguments.
 */
public class Runner extends Launcher<RunnerOptions> {
    /**
     * Entry point from the OS. Delegates to Launcher.
     *
     * @param asArg command line arguments
     */
    static void main(String[] asArg) {
        Launcher.main(insertCommand("xec", asArg));
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

    @Override
    protected int process() {
        // repository setup
        RunnerOptions    options = options();
        ModuleRepository repo    = configureLibraryRepo(options.getModulePath());
        checkErrors();

        if (options.showVersion()) {
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
        if (isExplicitCompiledFile(filePath) && fileSpec.exists() && (options.isCompileDisabled() || isPathed(filePath))) {
            // the caller has explicitly specified the exact .xtc file and/or
            fileBin    = fileSpec;
            binExists  = true;
            binLocDesc = "the specified target " + filePath;
        } else if (!isPathed(filePath) && !isExplicitEcstasyFile(filePath) && (module = repo.loadModule(filePath)) != null) {
            // use the module we found in the repo
            binLocDesc = "the repository";
        } else {
            ModuleInfo info    = null;
            File       outFile = options.getOutputFile();
            try {
                info = new ModuleInfo(fileSpec, options.deduce(), null, outFile);
            } catch (RuntimeException e) {
                log(ERROR, "Failed to identify the module for: " + fileSpec + " (" + e + ")");
            }
            checkErrors();

            fileBin   = Objects.requireNonNull(info).getBinaryFile();
            binExists = fileBin != null && fileBin.exists();

            boolean fCompile = false;
            if (!binExists) {
                String qualName = info.getQualifiedModuleName();
                module = repo.loadModule(qualName);
                if (module == null) {
                    File fileSrc = info.getSourceFile();
                    if (fileSrc != null && fileSrc.exists() && !options.isCompileDisabled()) {
                        log(INFO, "The compiled module \"" + info.getQualifiedModuleName()
                                + "\" is missing; attempting to compile it from "
                                + info.getSourceFile() + " ....");
                        fCompile = true;
                    } else {
                        var possibles = resolvePossibleTargets(qualName, repo);
                        if (possibles == null) {
                            log(ERROR, "Failed to locate the module for: " + fileSpec);
                        } else if (possibles.size() == 1) {
                            log(ERROR, "Unable to locate the module for " + fileSpec
                                    + "; did you mean " + quotedString(possibles.iterator().next())
                                    + '?');
                        } else {
                            var buf = new StringBuilder();
                            for (String name : possibles) {
                                buf.append(", ")
                                   .append(quotedString(name));
                            }
                            log(ERROR, "Unable to locate the module for " + fileSpec
                                    + "; did you mean one of: " + buf.substring(2) + '?');
                        }
                    }
                } else {
                    binExists = true;
                }
            }

            if (binExists && !options.isCompileDisabled() && info.getSourceFile() != null
                    && info.getSourceFile().exists() && !info.isUpToDate()) {
                log(INFO, "The compiled module \"" + info.getQualifiedModuleName()
                        + "\" is out-of-date; recompiling ....");
                fCompile = true;
            }
            checkErrors();

            if (fCompile) {
                // Build CompilerOptions programmatically
                CompilerOptions.Builder builder = new CompilerOptions.Builder();

                List<File> libPath = options.getModulePath();
                for (File libFile : libPath) {
                    builder.addModulePath(libFile);
                }

                if (outFile != null) {
                    builder.setOutputLocation(outFile);
                }

                builder.addInputFile(fileSpec);

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
                    log(FATAL, "I/O exception (" + e + ") reading module file: " + fileBin);
                    throw abort(true);
                }
            }
        }

        if (module == null) {
            log(ERROR, "Missing module for " + fileSpec);
            throw abort(true);
        }
        try {
            repo.storeModule(module);
        } catch (IOException e) {
            log(FATAL, "I/O exception (" + e + ") storing module file: " + fileSpec);
            throw abort(true);
        }
        checkErrors();

        String sName = Objects.requireNonNull(module).getName();
        if (sName.equals(module.getSimpleName())) {
            // quote the "simpleName" to visually differentiate it (there is no qualified name)
            sName = quotedString(sName);
        }

        log(INFO, "Executing " + sName + " from " + binLocDesc);
        try {
            Connector connector = options.isJit() ? new JitConnector(repo) : new Connector(repo);
            connector.loadModule(module.getName());
            connector.start(options.getInjections());

            ConstantPool pool = connector.getConstantPool();
            try (var ignore = ConstantPool.withPool(pool)) {
                String               sMethod    = options.getMethodName();
                Set<MethodStructure> setMethods = connector.findMethods(sMethod);
                if (setMethods.size() != 1) {
                    log(ERROR, (setMethods.isEmpty() ? "Missing" : "Ambiguous") + " method \"" + sMethod + "\" in module " + sName);
                    throw abort(true);
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
                                log(ERROR, "Unsupported argument type \"" +
                                    typeArg.getValueString() + "\" for method \"" + sMethod + "\"");
                                throw abort(true);
                            }
                        } else {
                            log(WARNING, "Method \"" + sMethod +
                                "\" does not take any parameters; ignoring the specified arguments");
                        }
                    }
                    break;

                case 1: {
                    TypeConstant typeArg = method.getParam(0).getType();
                    if (!typeStrings.isA(typeArg)) {
                        log(ERROR, "Unsupported argument type \"" + typeArg.getValueString() + "\" for method \"" + sMethod + "\"");
                        throw abort(true);
                    }
                    break;
                }

                default:
                    log(ERROR, "Unsupported method arguments \"" + method.getIdentityConstant().getSignature().getValueString());
                    throw abort(true);
                }

                connector.invoke0(method, asArg);

                return connector.join();
            }
        } catch (InterruptedException ignore) {
            return 1;
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            log(FATAL, e.toString());
            return 1;
        }
    }

    private static Set<String> resolvePossibleTargets(String qualName, ModuleRepository repo) {
        Set<String> possibles = null;
        if (qualName.indexOf('.') < 0) {
            // the qualified name wasn't qualified; that may have been user input
            // error; find all the names that they may have meant to type
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
        return possibles;
    }


    // ----- text output and error handling --------------------------------------------------------

    @Override
    public String desc() {
        return """
            Ecstasy runner:

                Executes an Ecstasy module, compiling it first if necessary.

                Also supports:
                    <filename>, <filename>.x, or <filename>.xtc""";
    }


    // ----- options -------------------------------------------------------------------------------

    @Override
    protected void validateOptions() {
        // Validate the -L path of file(s)/dir(s)
        validateModulePath(options().getModulePath());
    }
}

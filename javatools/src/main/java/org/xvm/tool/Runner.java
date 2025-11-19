package org.xvm.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.Set;
import java.util.stream.Collectors;

import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.javajit.JitConnector;

import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.LauncherOptions.RunnerOptions;

import org.xvm.util.Handy;
import org.xvm.util.ListSet;

import static org.xvm.tool.ModuleInfo.isExplicitCompiledFile;
import static org.xvm.tool.ModuleInfo.isExplicitEcstasyFile;
import static org.xvm.util.Handy.checkReadable;
import static org.xvm.util.Handy.isPathed;
import static org.xvm.util.Handy.quoted;
import static org.xvm.util.Severity.ERROR;
import static org.xvm.util.Severity.FATAL;
import static org.xvm.util.Severity.INFO;
import static org.xvm.util.Severity.WARNING;
import static java.util.Objects.requireNonNull;

/**
 * The "execute" command:
 * <p>
 *  java org.xvm.tool.Runner [-L repo(s)] [-M method_name] app.xtc [argv]
 * <p>
 * where the default method is "run" with no arguments.
 */
public class Runner extends Launcher<RunnerOptions> {

    static final String COMMAND_NAME = "xec";

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
     * Entry point from the OS. Delegates to Launcher.
     *
     * @param asArg command line arguments
     */
    static void main(String[] asArg) {
        Launcher.main(insertCommand(COMMAND_NAME, asArg));
    }

    // TODO: Also support process calls with an overriding options object as parameter.
    @Override
    protected int process() {
        // repository setup
        final var opts = options();

	var repo = configureLibraryRepo(opts.getModulePath());

	checkErrors();
	
        if (opts.showVersion()) {
            showSystemVersion(repo);
        }

        final File fileSpec = opts.getTarget();
        if (fileSpec == null) {
            if (opts.showVersion()) {
                return 0;
            }
            displayHelp();
            return 1;
        }

        var             filePath   = fileSpec.getPath();
        File            fileBin    = null;
        boolean         binExists  = false;
        ModuleStructure module     = null;
        String          binLocDesc;

        if (isExplicitCompiledFile(filePath) && fileSpec.exists() && (opts.isCompileDisabled() || isPathed(filePath))) {
            // the caller has explicitly specified the exact .xtc file and/or
            fileBin    = fileSpec;
            binExists  = true;
            binLocDesc = "the specified target " + filePath;
        } else if (!isPathed(filePath) && !isExplicitEcstasyFile(filePath) && (module = repo.loadModule(filePath)) != null) {
            // use the module we found in the repo
            binLocDesc = "the repository";
        } else {
            ModuleInfo info    = null;
            File       outFile = opts.getOutputFile();
            try {
                info = new ModuleInfo(fileSpec, opts.deduce(), null, outFile);
            } catch (RuntimeException e) {
                log(ERROR, "Failed to identify the module for: {} ({})", fileSpec, e);
            }
            checkErrors();

            fileBin   = requireNonNull(info).getBinaryFile();
            binExists = fileBin != null && fileBin.exists();

            boolean fCompile = false;
            if (!binExists) {
                var qualName = info.getQualifiedModuleName();
                module = repo.loadModule(qualName);
                if (module == null) {
                    var fileSrc = info.getSourceFile();
                    if (fileSrc != null && fileSrc.exists() && !opts.isCompileDisabled()) {
                        log(INFO, "The compiled module {} is missing; attempting to compile it from {} ...", quoted(info.getQualifiedModuleName()), info.getSourceFile());
                        fCompile = true;
                    } else {
                        var possibles = resolvePossibleTargets(qualName, repo);
                        if (possibles == null) {
                            log(ERROR, "Failed to locate the module for: {}", fileSpec);
                        } else if (possibles.size() == 1) {
                            log(ERROR, "Unable to locate the module for {}; did you mean {}?", fileSpec, quoted(possibles.iterator().next()));
                        } else {
                            final var names = possibles.stream().map(Handy::quoted).collect(Collectors.joining(", "));
                            log(ERROR, "Unable to locate the module for {}; did you mean one of: {}?", fileSpec, names);
                        }
                    }
                } else {
                    binExists = true;
                }
            }

            if (binExists && !opts.isCompileDisabled() && info.getSourceFile() != null
                    && info.getSourceFile().exists() && !info.isUpToDate()) {
                log(INFO, "The compiled module \"{}\" is out-of-date; recompiling ...", info.getQualifiedModuleName());
                fCompile = true;
            }
            checkErrors();

            if (fCompile) {
                // Build CompilerOptions programmatically using fluent API
                final var builder = new CompilerOptions.Builder().addInputFile(fileSpec);
                opts.getModulePath().forEach(builder::addModulePath);
                if (outFile != null) {
                    builder.setOutputLocation(outFile);
                }
                final var compilerOpts = builder.build();
                new Compiler(compilerOpts, m_console, m_errDelegate).run();
                // TODO: No one checks the return value of the compile?
                info      = new ModuleInfo(fileSpec, opts.deduce(), null, outFile);
                fileBin   = info.getBinaryFile();
                binExists = fileBin != null && fileBin.exists();
                repo      = configureLibraryRepo(opts.getModulePath());
                module    = repo.loadModule(info.getQualifiedModuleName());
            }

            binLocDesc = info.getBinaryFile().getPath();
        }

        // check if the compiled module file name was specified
        if (module == null && binExists) {
            if (checkReadable(fileBin)) {
                try (var in = new FileInputStream(fileBin)) {
                    var struct = new FileStructure(in);
                    module = struct.getModule();
                } catch (final IOException e) {
                    log(FATAL, "I/O exception ({}) reading module file: {}", e, fileBin);
                    throw abort(true);
                }
            }
        }

        if (module == null) {
            log(ERROR, "Missing module for {}", fileSpec);
            throw abort(true);
        }
        try {
            repo.storeModule(module);
        } catch (final IOException e) {
            // TODO: This is kind of weird. "FATAL" is pretty much always "bad enough to print", and
            //   logging something fatal will abort. Not 100% consistent to also abort.
            log(FATAL, "I/O exception ({}) storing module file: {}", e.getMessage(), fileSpec);
            throw abort(true);
        }
        checkErrors();

        var sName = requireNonNull(module).getName();
        if (sName.equals(module.getSimpleName())) {
            // quote the "simpleName" to visually differentiate it (there is no qualified name)
            sName = quoted(sName);
        }

        log(INFO, "Executing {} from {}", sName, binLocDesc);
        try {
            var connector = opts.isJit() ? new JitConnector(repo) : new Connector(repo);
            connector.loadModule(module.getName());
            connector.start(opts.getInjections());

            var pool = connector.getConstantPool();
            try (var ignore = ConstantPool.withPool(pool)) {
                final var sMethod    = opts.getMethodName();
                final var setMethods = connector.findMethods(sMethod);
                if (setMethods.size() != 1) {
                    log(ERROR, "{} method {} in module {}", setMethods.isEmpty() ? "Missing" : "Ambiguous", quoted(sMethod), sName);
                    throw abort(true);
                }

                var asArg       = opts.getMethodArgs();
                var method      = setMethods.iterator().next();
                var typeStrings = pool.ensureArrayType(pool.typeString());

                switch (method.getRequiredParamCount()) {
                case 0:
                    if (asArg != null) {
                        // the method doesn't require anything, but there are args
                        if (method.getParamCount() > 0) {
                            var typeArg = method.getParam(0).getType();
                            if (!typeStrings.isA(typeArg)) {
                                log(ERROR, "Unsupported argument type {} for method {}", quoted(typeArg.getValueString()), quoted(sMethod));
                                throw abort(true);
                            }
                        } else {
                            log(WARNING, "Method {} does not take any parameters; ignoring the specified arguments", quoted(sMethod));
                        }
                    }
                    break;

                case 1: {
                    var typeArg = method.getParam(0).getType();
                    if (!typeStrings.isA(typeArg)) {
                        log(ERROR, "Unsupported argument type {} for method {}", quoted(typeArg.getValueString()), quoted(sMethod));
                        throw abort(true);
                    }
                    break;
                }

                default:
                    log(ERROR, "Unsupported method arguments {}", quoted(method.getIdentityConstant().getSignature().getValueString()));
                    throw abort(true);
                }

                connector.invoke0(method, asArg);

                return connector.join();
            }
        } catch (InterruptedException ignore) {
            log(WARNING, "Interrupted while waiting for method {}", quoted(sName));
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

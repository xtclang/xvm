package org.xvm.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

import org.xvm.util.Handy;

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

    public static final String COMMAND_NAME = "run";

    /**
     * Runner constructor for programmatic use.
     *
     * @param options     pre-configured runner options
     * @param console     representation of the terminal within which this command is run, or null
     * @param errListener optional ErrorListener to receive errors, or null for no delegation
     */
    public Runner(final RunnerOptions options, final Console console, final ErrorListener errListener) {
        super(options, console, errListener);
    }

    /**
     * Entry point from the OS. Delegates to Launcher.
     *
     * @param asArg command line arguments
     */
    static void main(final String[] asArg) {
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
            File       outFile = opts.getOutputFile();
            ModuleInfo info;
            try {
                info = new ModuleInfo(fileSpec, opts.mayDeduceLocations(), List.of(), outFile);
            } catch (final RuntimeException e) {
                log(ERROR, "Failed to identify the module for: {} ({})", fileSpec, e);
                throw new AssertionError("Unreachable", e);
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
                        if (possibles.isEmpty()) {
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
                final var builder = CompilerOptions.builder()
                        .addInputFile(fileSpec)
                        .addModulePath(opts.getModulePath());
                if (outFile != null) {
                    builder.setOutputLocation(outFile);
                }
                final var exitCode = new Compiler(builder.build(), m_console, m_errors).run();
                if (exitCode != 0) {
                    log(ERROR, "Runner invoked compilation failed with exit code {}", exitCode);
                    checkErrors();
                    throw new AssertionError("Unreachable");
                }
                info      = new ModuleInfo(fileSpec, opts.mayDeduceLocations(), List.of(), outFile);
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
                    log(FATAL, e, "I/O exception reading module file: {}", fileBin);
                    throw new AssertionError("Unreachable", e); // Unreachable - log(FATAL) throws
                }
            }
        }

        if (module == null) {
            log(ERROR, "Missing module for {}", fileSpec);
            checkErrors();  // ERROR level throws LauncherException
            throw new AssertionError("Unreachable"); // Unreachable - log(FATAL) throws
        }

        try {
            repo.storeModule(module);
        } catch (final IOException e) {
            log(FATAL, e, "I/O exception storing module file: {}", fileSpec);
            throw new AssertionError("Unreachable", e); // Unreachable - log(FATAL) throws
        }
        checkErrors();

        var sName = requireNonNull(module).getName();
        if (sName.equals(module.getSimpleName())) {
            // quote the "simpleName" to visually differentiate it (there is no qualified name)
            sName = quoted(sName);
        }

        log(INFO, "Executing {} from {}", sName, binLocDesc);

        var connector = createConnector(repo, module);

        var pool = connector.getConstantPool();
        try (var _ = ConstantPool.withPool(pool)) {
            final var sMethod    = opts.getMethodName();
            final var setMethods = connector.findMethods(sMethod);
            if (setMethods.size() != 1) {
                log(ERROR, "{} method {} in module {}", setMethods.isEmpty() ? "Missing" : "Ambiguous", quoted(sMethod), sName);
                checkErrors();  // Throws LauncherException
                return 1;  // Unreachable
            }

            var args = opts.getMethodArgs();
            var method = setMethods.iterator().next();
            var typeStrings = pool.ensureArrayType(pool.typeString());
            validateMethodArgs(sMethod, method, args, typeStrings);
            connector.invoke0(method, args);
            return connector.join();
        } catch (final InterruptedException _) {
            log(WARNING, "Interrupted while waiting for method {}", quoted(sName));
            return 1;
        } catch (final LauncherException e) {
            throw e;
        } catch (final Exception e) {
            e.printStackTrace(System.err);
            log(FATAL, e, "Unexpected error");
            throw new AssertionError("Unreachable", e); // Unreachable - log(FATAL) throws
        }
    }

    /**
     * Validate that the method arguments are compatible with the method signature.
     */
    private void validateMethodArgs(final String sMethod, final MethodStructure method, final List<String> asArg, final TypeConstant typeStrings) {
        final var requiredCount = method.getRequiredParamCount();
        final var totalCount    = method.getParamCount();

        // Only methods with 0 or 1 required parameters are supported
        if (requiredCount > 1) {
            log(ERROR, "Unsupported method arguments {}", quoted(method.getIdentityConstant().getSignature().getValueString()));
            checkErrors();  // ERROR level throws LauncherException
            throw new AssertionError("Unreachable");
        }

        // Warn if args provided but method takes no parameters
        if (!asArg.isEmpty() && totalCount == 0) {
            log(WARNING, "Method {} does not take any parameters; ignoring the specified arguments", quoted(sMethod));
            return;
        }

        // Validate parameter type when args will be passed:
        // - required param exists (requiredCount == 1), or
        // - args provided and method can accept them
        boolean willPassArgs = requiredCount > 0 || (!asArg.isEmpty() && totalCount > 0);
        if (willPassArgs) {
            var typeArg = method.getParam(0).getType();
            if (!typeStrings.isA(typeArg)) {
                log(ERROR, "Unsupported argument type {} for method {}", quoted(typeArg.getValueString()), quoted(sMethod));
                checkErrors();  // ERROR level throws LauncherException
                throw new AssertionError("Unreachable");
            }
        }
    }

    // TODO: Does the order here matter, and if not,
    private static Set<String> resolvePossibleTargets(final String qualName, final ModuleRepository repo) {
        final var possibles = new LinkedHashSet<String>();
        if (qualName.indexOf('.') < 0) {
            // the qualified name wasn't qualified; that may have been user input
            // error; find all the names that they may have meant to type
            for (final String name : repo.getModuleNames()) {
                final int ofDot = name.indexOf('.');
                if (ofDot > 0 && name.substring(0, ofDot).equals(qualName)) {
                    possibles.add(name);
                }
            }
        }
        return possibles;
    }

    @Override
    public String desc() {
        return """
            Ecstasy runner:

                Executes an Ecstasy module, compiling it first if necessary.

                Also supports:
                    <filename>, <filename>.x, or <filename>.xtc""";
    }

    // ----- connector creation --------------------------------------------------------------------

    /**
     * Create the {@link Connector} to use to execute the module.
     * Subclasses (like TestRunner) can override to customize connector setup.
     *
     * @param repo   the module repository
     * @param module the module to execute
     * @return the configured Connector ready for method invocation
     */
    protected Connector createConnector(final ModuleRepository repo, final ModuleStructure module) {
        final RunnerOptions opts = options();
        final Connector connector = createBaseConnector(repo, opts.isJit());
        connector.loadModule(module.getName());
        connector.start(opts.getInjections());
        return connector;
    }

    /**
     * Create the base Connector instance (with or without JIT).
     * Helper method for subclasses that need to customize connector setup.
     *
     * @param repo  the module repository
     * @param isJit true to use JIT connector, false for interpreted
     * @return a new Connector (not yet started)
     */
    protected Connector createBaseConnector(final ModuleRepository repo, final boolean isJit) {
        return isJit ? new JitConnector(repo) : new Connector(repo);
    }

    // ----- options -------------------------------------------------------------------------------

    @Override
    protected void validateOptions() {
        // Validate the -L path of file(s)/dir(s)
        validateModulePath(options().getModulePath());
    }
}

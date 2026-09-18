package org.xvm.api;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.compiler.BuildRepository;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.tool.Console;
import org.xvm.tool.Launcher.LauncherException;
import org.xvm.tool.LauncherOptions.CompilerOptions;

import static org.xvm.api.EmbeddingSupport.ERR_INTERNAL;

import static org.xvm.asm.ErrorListener.NOWHERE;
import static org.xvm.asm.ErrorListener.at;

import static org.xvm.util.Handy.readFileChars;

import static org.xvm.util.Severity.ERROR;

/**
 * Compiles Ecstasy modules from source against a repository of core libraries.
 *
 * <p>Unlike {@link EmbeddingSupport}, a ModuleCompiler is an ordinary object that needs no
 * configuration: compiling does not start the runtime, so it does not depend on the runtime's
 * JVM-wide state (see {@link EmbeddingSupport}), and any number of ModuleCompiler instances, each
 * with its own core repository, can exist in one JVM. Each call to compile is independent.
 *
 * <p>The compile methods report the same errors as the command-line compiler: every error found in
 * a compiler stage, with the compilation stopping at the end of the first stage that found any.
 */
public final class ModuleCompiler {
    private static final Console SILENT_CONSOLE = new Console() {
        @Override
        public String out(Object value) {
            return String.valueOf(value);
        }

        @Override
        public String err(Object value) {
            return String.valueOf(value);
        }
    };

    private final ModuleRepository coreRepo;

    /**
     * Construct a ModuleCompiler.
     *
     * @param coreRepo  the ModuleRepository to load the core libraries from
     */
    public ModuleCompiler(ModuleRepository coreRepo) {
        this.coreRepo = Objects.requireNonNull(coreRepo, "coreRepo");
    }

    /**
     * Compile a module that is in a String.
     *
     * @param source  the source code for an entire module to compile
     * @param input   (optional) the module repository to read any required modules from
     * @param errs    the ErrorListener to log any compiler messages to
     *
     * @return the resulting ModuleStructure, or null if a compiler error occurred
     */
    public ModuleStructure compile(String source, ModuleRepository input, @NotNull ErrorListener errs) {
        Objects.requireNonNull(errs, "errs");
        try {
            EmbeddingCompiler compiler = new EmbeddingCompiler(source, input, coreRepo, errs);
            return compiler.process() == 0
                    ? compiler.getModule()
                    : null;
        } catch (LauncherException e) {
            // the compiler stops at the end of a stage that logged errors; those errors are already
            // in "errs", so this is an ordinary failed compile, as it is for the command-line
            // compiler. A tool-level failure is logged only to the silent console, so report it
            if (!errs.hasSeriousErrors()) {
                errs.error(ERR_INTERNAL, NOWHERE, e, "Compilation failed");
            }
            return null;
        } catch (RuntimeException | AssertionError e) {
            // as in EmbeddingSupport.run(): the compiler runs over caller-supplied source, so a
            // failure in it is reported here rather than thrown at the caller, who was promised a
            // null instead
            errs.error(ERR_INTERNAL, NOWHERE, e, "Compilation failed");
            return null;
        }
    }

    /**
     * Compile a module that is in a file.
     *
     * @param file    the module source file
     * @param input   (optional) the module repository to read any required modules from
     * @param output  (optional) the module repository to write any compiled modules to
     * @param errs    the ErrorListener to log any compiler messages to
     *
     * @return true if the compilation succeeded and the result was placed into the output
     */
    public boolean compile(File file, ModuleRepository input, ModuleRepository output,
                           @NotNull ErrorListener errs) {
        Objects.requireNonNull(errs, "errs");
        ModuleStructure module;
        try {
            module = compile(new String(readFileChars(file)), input, errs);
        } catch (IOException e) {
            errs.error(ERR_INTERNAL, NOWHERE, e, "Unable to read module " + file);
            return false;
        }

        if (module == null) {
            assert errs.hasSeriousErrors();
            return false;
        }

        if (output != null) {
            try {
                output.storeModule(module);
            } catch (IOException e) {
                errs.error(ERR_INTERNAL, at(module), e, "Unable to store module " + module.getName());
                return false;
            }
        }
        return true;
    }

    /**
     * Adapter that supplies the source and repositories to the standard compiler pipeline and
     * captures its single compiled module instead of writing it to disk.
     */
    private static final class EmbeddingCompiler
            extends org.xvm.tool.Compiler {
        private final String           source;
        private final ModuleRepository inRepo;
        private final ModuleRepository coreRepo;
        private       ModuleStructure  module;

        private EmbeddingCompiler(String source, ModuleRepository input, ModuleRepository core,
                                  ErrorListener errs) {
            super(CompilerOptions.builder().build(), SILENT_CONSOLE, errs);

            this.source   = source;
            this.inRepo   = input;
            this.coreRepo = core;
        }

        @Override
        protected int process() {
            ModuleRepository repoLib = ensureLibraryRepo();
            checkErrors("repository setup");

            prelinkSystemLibraries(repoLib);
            checkErrors("system library linking");

            // as each module does in the command-line compiler (ModuleInfo.Node), the module logs
            // its errors to a list of its own, which is passed on to this tool at the end of each
            // stage; this tool stops at the first error logged to it, so a stage logging to it
            // directly would stop at its first error instead of reporting all of them
            ErrorList errsModule = new ErrorList(ErrorListener.DEFAULT_MAX_ERRORS);

            StatementBlock block;
            try {
                block = new Parser(new Source(source), errsModule).parseSource();
            } catch (CompilerException e) {
                block = null;
            }
            if (flushAndCheckErrors(errsModule, "source parsing") != 0 || block == null) {
                return 1;
            }

            Statement stmt = block.getStatements().getLast();
            if (!(stmt instanceof TypeCompositionStatement stmtModule) ||
                    stmtModule.getCategory().getId() != Id.MODULE) {
                log(ERROR, "In-memory source does not contain a module");
                return checkErrors("source parsing");
            }

            Compiler      compiler = new Compiler(stmtModule, errsModule);
            FileStructure struct   = compiler.generateInitialFileStructure();
            if (flushAndCheckErrors(errsModule, "module creation") != 0 || struct == null) {
                return 1;
            }

            try {
                repoLib.storeModule(struct.getModule());
            } catch (IOException e) {
                log(ERROR, e, "I/O exception storing module: {}", struct.getModule().getName());
                return 1;
            }

            int result = compile(List.of(compiler), repoLib);
            if (result == 0) {
                this.module = struct.getModule();
            }
            return result;
        }

        /**
         * Pass the errors the module has logged on to this tool, and check them.
         *
         * @param errsModule  the module's error list
         * @param context     the compiler stage being checked
         *
         * @return 0 if no serious errors, 1 if serious errors exist but do not require an abort
         */
        private int flushAndCheckErrors(ErrorList errsModule, String context) {
            errsModule.logTo(this);
            errsModule.clear();
            return checkErrors(context);
        }

        /**
         * The build repository comes first: it receives the module being compiled and, as the
         * launcher's does, a copy of each module read from the others. The caller's modules are
         * searched before the core libraries; the input repository may be absent, or be the core
         * repository itself.
         */
        @Override
        protected ModuleRepository configureLibraryRepo(List<File> ignore) {
            return new LinkedRepository(true, new BuildRepository(), inRepo, coreRepo);
        }

        /**
         * @return the result of the compilation
         */
        private ModuleStructure getModule() {
            return module;
        }
    }
}

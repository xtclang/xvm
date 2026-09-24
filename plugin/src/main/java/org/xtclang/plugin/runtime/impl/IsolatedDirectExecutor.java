package org.xtclang.plugin.runtime.impl;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.xvm.api.EmbeddingSupport;
import org.xvm.api.RunRequest;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileRepository;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.compiler.BuildRepository;
import org.xvm.tool.Console;
import org.xvm.tool.Launcher;
import org.xvm.tool.TestRunner;
import org.xvm.util.Severity;

import org.xtclang.plugin.runtime.DirectCompileRequest;
import org.xtclang.plugin.runtime.DirectRunRequest;
import org.xtclang.plugin.runtime.DirectTestRequest;
import org.xtclang.plugin.runtime.RuntimeExecutor;
import org.xtclang.plugin.runtime.RuntimeOutput;

/**
 * Owns one embedding session inside the selected XDK's implementation classloader.
 */
public final class IsolatedDirectExecutor implements RuntimeExecutor {
    private static final int DEFAULT_ERROR_LIMIT = 100;

    private final EmbeddingSupport session;
    private volatile EmbeddingSupport.Control activeControl;

    public IsolatedDirectExecutor(final List<File> coreModules) {
        session = EmbeddingSupport.create(repository(coreModules));
    }

    @Override
    public int execute(final Object request, final RuntimeOutput output) {
        return switch (request) {
            case DirectCompileRequest compile -> executeCompile(compile, output);
            case DirectRunRequest run -> executeRun(run, output);
            case DirectTestRequest test -> executeTest(test, output);
            default -> throw new IllegalArgumentException("Unknown embedded request: " + request);
        };
    }

    public int executeCompile(final DirectCompileRequest request, final RuntimeOutput output) {
        final var errors = new ErrorList(DEFAULT_ERROR_LIMIT);
        try (var console = new RequestConsole(request.stdoutFile(), request.stderrFile(), output)) {
            final var options = new IsolatedLauncherOptionsBuilder().buildCompilerOptions(request);
            final int result = session.compile(options, console, errors);
            return console.failed() ? 1 : result;
        }
    }

    public int executeRun(final DirectRunRequest request, final RuntimeOutput output) {
        return executeRun(request, null, output);
    }

    public int executeTest(final DirectTestRequest request, final RuntimeOutput output) {
        final var execution = new DirectRunRequest(request.projectDir(), request.stdoutFile(), request.stderrFile(),
            request.modulePath(), request.showVersion(), request.verbose(), request.jit(), request.moduleName(),
            request.methodName(), request.moduleArgs());
        return executeRun(execution, request.outputDir(), output);
    }

    private int executeRun(final DirectRunRequest request, final File testOutput, final RuntimeOutput output) {
        if (request.jit()) {
            throw new UnsupportedOperationException("Embedded execution does not support JIT; use --mode=ATTACHED.");
        }
        final var errors = new ErrorList(DEFAULT_ERROR_LIMIT);
        try (var console = new RequestConsole(request.stdoutFile(), request.stderrFile(), output)) {
            final var modules = repository(request.modulePath());
            if (request.showVersion()) {
                Launcher.showSystemVersion(session.getConfiguredRepository(), console);
            }
            if (request.verbose()) {
                console.out("Embedded interpreter: "
                    + request.moduleName() + '.' + request.methodName()
                    + " (working directory: " + request.projectDir() + ')');
            }
            String moduleName = request.moduleName();
            final var path = new File(moduleName);
            final var file = path.isAbsolute() ? path : new File(request.projectDir(), moduleName);
            if (file.isFile()) {
                try {
                    final var module = new FileStructure(file).getModule();
                    modules.storeModule(module);
                    moduleName = module.getName();
                } catch (final IOException e) {
                    throw new IllegalStateException("Unable to load module " + file, e);
                }
            }
            Map<String, List<String>> injections = Map.of();
            if (testOutput != null) {
                final var module = modules.loadModule(moduleName);
                if (module == null) {
                    console.err("Unable to load test module " + moduleName);
                    return 1;
                }
                final var version = module.getVersionString();
                final var outputPath = request.projectDir().toPath().toAbsolutePath().normalize()
                    .relativize(testOutput.toPath().toAbsolutePath().normalize()).toString();
                injections = Map.of(
                    TestRunner.XUNIT_MODULE_ARG, List.of(module.getName()),
                    TestRunner.XUNIT_MODULE_VERSION_ARG, version == null ? List.of() : List.of(version),
                    TestRunner.XUNIT_TEST_OUTPUT_DIR, List.of(outputPath));
                moduleName = TestRunner.XUNIT_MODULE;
            }
            final var execution = new RunRequest(modules, moduleName, request.methodName(), request.moduleArgs(),
                console.output, request.projectDir(), true, injections);
            try (var control = session.run(execution, errors)) {
                if (control == null) {
                    console.err(errors.getErrors());
                    return 1;
                }
                activeControl = control;
                control.join();
                if (errors.hasSeriousErrors() || control.result() == null || console.failed()) {
                    console.err(errors.getErrors());
                    return 1;
                }
                return Math.toIntExact(control.result());
            } finally {
                activeControl = null;
            }
        }
    }

    @Override
    public void close(final Duration timeout) {
        session.close(timeout);
    }

    @Override
    public boolean cancel(final Duration timeout) {
        final var control = activeControl;
        if (control == null) {
            return false;
        }
        control.close(timeout);
        return true;
    }

    private static ModuleRepository repository(final List<File> paths) {
        final var repositories = new ArrayList<ModuleRepository>();
        repositories.add(new BuildRepository());
        paths.forEach(path -> repositories.add(path.isDirectory()
            ? new DirRepository(path, true) : new FileRepository(path, true)));
        return new LinkedRepository(true, repositories.toArray(ModuleRepository[]::new));
    }

    private static final class RequestConsole implements Console, AutoCloseable {
        private final PrintWriter output;
        private final PrintWriter errors;

        RequestConsole(final File stdout, final File stderr, final RuntimeOutput sink) {
            output = writer(stdout, sink::out);
            try {
                errors = writer(stderr, sink::err);
            } catch (final RuntimeException e) {
                output.close();
                throw e;
            }
        }

        @Override
        public String out(final Object value) {
            final var message = String.valueOf(value);
            output.println(message);
            return message;
        }

        @Override
        public String err(final Object value) {
            final var message = String.valueOf(value);
            errors.println(message);
            return message;
        }

        @Override
        public String log(final Severity severity, final String template, final Object... params) {
            return log(severity, null, template, params);
        }

        @Override
        public String log(final Severity severity, final Throwable cause, final String template, final Object... params) {
            final var message = Console.formatTemplate(template, params);
            if (severity.isAtLeast(Severity.WARNING)) {
                err(message);
            } else {
                out(message);
            }
            if (cause != null) {
                cause.printStackTrace(errors);
            }
            return message;
        }

        boolean failed() {
            return output.checkError() || errors.checkError();
        }

        @Override
        public void close() {
            output.close();
            errors.close();
        }

        private static PrintWriter writer(final File file, final Consumer<String> sink) {
            if (file != null) {
                try {
                    Files.createDirectories(file.toPath().toAbsolutePath().getParent());
                    return new PrintWriter(Files.newBufferedWriter(file.toPath()), true);
                } catch (final IOException e) {
                    throw new IllegalStateException("Unable to open output file " + file, e);
                }
            }
            return new PrintWriter(new Writer() {
                private final StringBuilder line = new StringBuilder();

                @Override
                public synchronized void write(final char[] chars, final int offset, final int count) {
                    for (int i = offset; i < offset + count; i++) {
                        if (chars[i] == '\n') {
                            sink.accept(line.toString());
                            line.setLength(0);
                        } else if (chars[i] != '\r') {
                            line.append(chars[i]);
                        }
                    }
                }

                @Override
                public synchronized void flush() {
                    if (!line.isEmpty()) {
                        sink.accept(line.toString());
                        line.setLength(0);
                    }
                }

                @Override
                public void close() {
                    flush();
                }
            }, true);
        }
    }
}

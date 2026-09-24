package org.xvm.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;
import java.time.Instant;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.runtime.MainContainer;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OwnedResource;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template.text.xString.StringHandle;
import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template._native.io.xExternalConsole;
import org.xvm.runtime.template._native.mgmt.xCoreRepository;
import org.xvm.runtime.template._native.reflect.xRTModuleTemplate;

import org.xvm.util.Deadline;

import static org.xvm.api.EmbeddingSupport.ERR_UNHANDLED_EXCEPTION;
import static org.xvm.runtime.Runtime.DEFAULT_SHUTDOWN_TIMEOUT;
import static org.xvm.util.Severity.ERROR;

/**
 * Interpreter-backed management and monitoring for one runner task.
 */
class InterpreterControl
        implements EmbeddingSupport.Control {
    /**
     * Create and start the shared interpreter connector that hosts the runner module.
     */
    static Connector createConnector(ModuleRepository repository) {
        InterpreterConnector connector = new InterpreterConnector(repository);
        try {
            connector.loadModule("runner.xtclang.org");
            connector.start(null);
            // The module's run() starts its HTTP server. Native embedding uses the registry only.
            return connector;
        } catch (RuntimeException | Error e) {
            try {
                connector.close();
            } catch (RuntimeException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    /**
     * Create and start a control for the specified module.
     */
    static EmbeddingSupport.Control create(Connector connector, ModuleStructure module,
                                           ModuleRepository repository, PrintWriter console,
                                           File rootDir, String methodName, List<String> arguments,
                                           boolean hostFileSystem, Map<String, List<String>> injections,
                                           ErrorListener errs) {
        if (!(connector instanceof InterpreterConnector interpreter)) {
            throw new IllegalArgumentException("An InterpreterConnector is required");
        }

        Instant started   = Instant.now();
        Long    consoleId = null;
        Long    taskId    = null;
        Path    temporary = null;

        try {
            FileStructure file = prepareModule(interpreter, module, repository);
            MainContainer main = interpreter.getMainContainer();

            var methods = interpreter.findMethods(file.getModuleId(), methodName);
            if (methods.size() != 1) {
                throw new IllegalArgumentException("Missing or ambiguous entry method: " + methodName);
            }
            var method = methods.iterator().next();
            boolean passArguments = method.getRequiredParamCount() > 0
                    || !arguments.isEmpty() && method.getParamCount() > 0;
            var strings = file.getConstantPool().ensureArrayType(file.getConstantPool().typeString());
            if (method.getRequiredParamCount() > 1 || passArguments
                    && !strings.isA(method.getParam(0).getType())) {
                throw new IllegalArgumentException("Entry method must accept zero arguments or String[]");
            }

            if (console != null) {
                consoleId = xExternalConsole.register(interpreter.getNativeContainer(), console);
            }

            ObjectHandle hModule     = xRTModuleTemplate.makeHandle(main, file.getModule());
            ObjectHandle hRepository = xCoreRepository.INSTANCE.makeHandle(repository);
            ObjectHandle hConsoleId  = consoleId == null
                    ? xNullable.NULL
                    : xInt64.makeHandle(consoleId);
            ObjectHandle hRootDir = rootDir == null
                    ? xNullable.NULL
                    : xString.makeHandle(rootDir.getAbsolutePath());

            if (rootDir == null) {
                temporary = Files.createTempDirectory("xvm-embedding-");
                hRootDir  = xString.makeHandle(temporary.toString());
            }

            var entries = List.copyOf(injections.entrySet());
            var pool = main.getConstantPool();
            var arrayOfStrings = pool.ensureArrayType(pool.ensureArrayType(pool.typeString()));
            ObjectHandle injectionValues = xArray.createImmutableArray(main.resolveClass(arrayOfStrings),
                    entries.stream().map(entry -> xString.makeArrayHandle(entry.getValue().toArray(String[]::new)))
                            .toArray(ObjectHandle[]::new));
            ObjectHandle hTaskId = postRequest(
                    interpreter, "registerTask",
                    hModule, hRepository, hConsoleId, hRootDir,
                    xString.makeHandle(methodName), xString.makeArrayHandle(arguments.toArray(String[]::new)),
                    xBoolean.makeHandle(passArguments), xBoolean.makeHandle(hostFileSystem),
                    xString.makeArrayHandle(entries.stream().map(Map.Entry::getKey).toArray(String[]::new)),
                    injectionValues).join();
            taskId = ((JavaLong) hTaskId).getValue();

            CompletableFuture<Void> completion = new CompletableFuture<>();
            InterpreterControl      control    = new InterpreterControl(interpreter, module, temporary,
                    errs, taskId, consoleId, started, completion);

            postRequest(interpreter, "startTask", hTaskId).whenComplete((r, e) -> {
                if (e == null) {
                    TupleHandle tuple   = (TupleHandle) r;
                    long        result  = ((JavaLong) tuple.m_ahValue[0]).getValue();
                    String      failure = ((StringHandle) tuple.m_ahValue[1]).getStringValue();
                    control.finish(result, failure);
                } else {
                    control.finish(-1, e.toString());
                }
            }).whenComplete((r, e) -> {
                if (e == null) {
                    completion.complete(null);
                } else {
                    completion.completeExceptionally(e);
                }
            });
            return control;
        } catch (IOException | RuntimeException e) {
            try {
                if (taskId != null) {
                    await(postRequest(interpreter, "releaseTask", xInt64.makeHandle(taskId)),
                            Deadline.after(DEFAULT_SHUTDOWN_TIMEOUT));
                }
                deleteTemporaryDirectory(temporary);
            } catch (RuntimeException cleanup) {
                e.addSuppressed(cleanup);
            } finally {
                unregisterConsole(interpreter, consoleId);
            }
            throw new IllegalStateException("Unable to create embedded request", e);
        }
    }

    /**
     * Prepare application definitions in a fresh request file and constant pool.
     *
     * <p>First deserialize the application's serialized form to discard cached runtime handles
     * and materialized AST objects. Then combine that copy with the native definitions and link
     * dependencies into the resulting request file. The reusable connector owns the host; its
     * pool must not become the owner of application-specific specializations or singleton state.
     * A shallow in-memory module copy does not provide this execution isolation.
     *
     * @param connector   the reusable host that supplies native definitions
     * @param module      the source application; its pool is not the request's destination
     * @param repository  the dependencies available to this request
     *
     * @return the application file prepared for the new request
     */
    private static FileStructure prepareModule(InterpreterConnector connector, ModuleStructure module,
                                               ModuleRepository repository) {
        FileStructure file;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            module.getFileStructure().writeTo(bytes);
            ModuleStructure moduleCopy = new FileStructure(
                    new ByteArrayInputStream(bytes.toByteArray()), true, false).getModule();
            file = connector.getNativeContainer().createFileStructure(moduleCopy);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to prepare module " + module.getName(), e);
        }

        ModuleConstant idMissing = file.linkModules(repository, true);
        if (idMissing != null) {
            throw new IllegalStateException("Unable to load module " + idMissing.getName());
        }
        return file;
    }

    private static CompletableFuture<ObjectHandle> postRequest(
            InterpreterConnector connector, String methodName, ObjectHandle... arguments) {
        return connector.getMainContainer().invokeAsync(methodName, arguments);
    }

    private InterpreterControl(InterpreterConnector connector, ModuleStructure module,
                               Path temporary, ErrorListener errs, long taskId, Long consoleId,
                               Instant started, CompletableFuture<Void> completion) {
        this.connector  = connector;
        this.module     = module;
        this.temporary  = temporary;
        this.errs       = errs;
        this.taskId     = taskId;
        this.consoleId  = consoleId;
        this.started    = started;
        this.completion = completion;
    }

    private void finish(long result, String failure) {
        try {
            if (failure.isEmpty()) {
                this.result = result;
            } else {
                this.result = null;
                if (errs != null) {
                    errs.log(ERROR, ERR_UNHANDLED_EXCEPTION, new Object[] {failure}, module);
                }
            }
        } finally {
            this.stopped = Instant.now();
            this.running = false;
        }
    }

    private static void unregisterConsole(InterpreterConnector connector, Long consoleId) {
        if (consoleId != null) {
            xExternalConsole.unregister(connector.getNativeContainer(), consoleId);
        }
    }

    @Override
    public boolean running() {
        return running;
    }

    @Override
    public void join() {
        try {
            completion.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for embedded request", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Embedded request failed", e.getCause());
        }
    }

    @Override
    public Instant whenStarted() {
        return started;
    }

    @Override
    public Instant whenStopped() {
        return stopped;
    }

    @Override
    public Long result() {
        return result;
    }

    @Override
    public void close(Duration timeout) {
        Deadline deadline = Deadline.after(timeout);
        await(release(), deadline);
    }

    /**
     * A caller's deadline bounds its wait, not the lifetime of the release operation. Once the
     * runner confirms termination, finish host cleanup even if that caller has already returned.
     */
    private synchronized CompletableFuture<Void> release() {
        if (hostCleanup != null) {
            return hostCleanup;
        }
        if (releaseAttempt == null || releaseAttempt.isCompletedExceptionally()) {
            try {
                releaseAttempt = postRequest(connector, "releaseTask", xInt64.makeHandle(taskId))
                        .thenCompose(_ -> completion.handle((_, failure) -> null))
                        .thenCompose(_ -> releaseHostResources());
            } catch (RuntimeException | Error e) {
                releaseAttempt = CompletableFuture.failedFuture(e);
            }
        }
        return releaseAttempt;
    }

    /**
     * Fall back to runtime termination when the runner could not acknowledge release. A failed
     * or still-running runtime must never authorize deleting an application's directory.
     */
    void releaseAfterRuntimeClose(Duration timeout) {
        Deadline deadline = Deadline.after(timeout);
        if (!connector.isClosed()) {
            throw new IllegalStateException("Runtime cleanup is still pending");
        }
        await(releaseHostResources(), deadline);
    }

    private synchronized CompletableFuture<Void> releaseHostResources() {
        if (hostCleanup == null) {
            hostCleanup = OwnedResource.closeOnWorker(() -> {
                try {
                    unregisterConsole(connector, consoleId);
                } finally {
                    deleteTemporaryDirectory(temporary);
                }
            });
        }
        return hostCleanup;
    }

    private static <T> T await(CompletableFuture<T> future, Deadline deadline) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return future.get(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    // Cancellation must still release the request within the original budget.
                    interrupted = true;
                } catch (ExecutionException | TimeoutException e) {
                    throw new IllegalStateException("Embedded request did not complete", e);
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void deleteTemporaryDirectory(Path directory) {
        if (directory != null) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to delete request directory " + directory, e);
            }
        }
    }

    private final InterpreterConnector    connector;
    private final ModuleStructure         module;
    private final Path                    temporary;
    private final ErrorListener           errs;
    private final long                    taskId;
    private final Long                    consoleId;
    private final Instant                 started;
    private final CompletableFuture<Void> completion;
    private CompletableFuture<Void> releaseAttempt;
    private CompletableFuture<Void> hostCleanup;

    private volatile boolean running = true;
    private volatile Instant stopped;
    private volatile Long    result;
}

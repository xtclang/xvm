package org.xvm.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import java.time.Instant;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.runtime.MainContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template._native.io.xExternalConsole;
import org.xvm.runtime.template._native.mgmt.xCoreRepository;
import org.xvm.runtime.template._native.reflect.xRTModuleTemplate;

import static org.xvm.api.LspSupport.ERR_UNHANDLED_EXCEPTION;

import static org.xvm.util.Severity.ERROR;

/**
 * Interpreter-backed management and monitoring for one runner task.
 */
class InterpreterControl
        implements LspSupport.Control {
    /**
     * Create and start the shared interpreter connector that hosts the runner module.
     */
    static Connector createConnector(ModuleRepository repository) {
        InterpreterConnector connector = new InterpreterConnector(repository);
        connector.loadModule("runner.xtclang.org");
        connector.start(null);
        connector.getMainContainer().invokeAsync("run").join();
        return connector;
    }

    /**
     * Create and start a control for the specified module.
     */
    static LspSupport.Control create(Connector connector, ModuleStructure module,
                                     ModuleRepository repository, PrintStream console,
                                     ErrorListener errs) {
        if (!(connector instanceof InterpreterConnector interpreter)) {
            throw new IllegalArgumentException("An InterpreterConnector is required");
        }
        Instant started   = Instant.now();
        Long    consoleId = console == null
                ? null
                : xExternalConsole.register(interpreter.getNativeContainer(), console);
        try {
            FileStructure file = prepareModule(interpreter, module, repository);
            MainContainer main = interpreter.getMainContainer();

            ObjectHandle hModule     = xRTModuleTemplate.makeHandle(main, file.getModule());
            ObjectHandle hRepository = xCoreRepository.INSTANCE.makeHandle(repository);
            ObjectHandle hConsoleId  = consoleId == null
                    ? xNullable.NULL
                    : xInt64.makeHandle(consoleId);
            ObjectHandle hTaskId = main.invokeAsync(
                    "runTask", hModule, hRepository, hConsoleId).join();

            // every field is known before the object exists, so the constructor only assigns
            InterpreterControl control = new InterpreterControl(interpreter, module, console, errs,
                    started, consoleId, ((JavaLong) hTaskId).getValue());
            control.watch();
            return control;
        } catch (RuntimeException e) {
            // nothing was constructed, so there is nothing to leave half-started - just release
            // the console this factory registered
            if (consoleId != null) {
                xExternalConsole.unregister(interpreter.getNativeContainer(), consoleId);
            }
            throw e;
        }
    }

    private InterpreterControl(InterpreterConnector connector, ModuleStructure module,
                               PrintStream console, ErrorListener errs,
                               Instant started, Long consoleId, long taskId) {
        this.connector = connector;
        this.module    = module;
        this.console   = console;
        this.errs      = errs;
        this.started   = started;
        this.consoleId = consoleId;
        this.taskId    = taskId;
    }

    private static FileStructure prepareModule(InterpreterConnector connector,
                                               ModuleStructure module,
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

    private CompletableFuture<ObjectHandle> postRequest(
            String methodName, ObjectHandle... arguments) {
        return connector.getMainContainer().invokeAsync(methodName, arguments);
    }

    private void watch() {
        CompletableFuture.delayedExecutor(25, TimeUnit.MILLISECONDS).execute(() ->
            taskRunning().whenComplete((isRunning, exception) -> {
                if (exception != null) {
                    finish(null, exception.toString());
                } else if (isRunning) {
                    watch();
                } else {
                    collectResult();
                }
            }));
    }

    private CompletableFuture<Boolean> taskRunning() {
        return postRequest("taskRunning", xInt64.makeHandle(taskId))
                .thenApply(result -> result == xBoolean.TRUE);
    }

    private void collectResult() {
        taskResult().whenComplete((result, exception) -> {
            if (exception != null) {
                finish(null, exception.toString());
                return;
            }

            taskFailure().whenComplete((failure, failureException) ->
                finish(result, failureException == null
                        ? failure
                        : failureException.toString()));
        });
    }

    private CompletableFuture<Long> taskResult() {
        return postRequest("taskResult", xInt64.makeHandle(taskId))
                .thenApply(result -> result == xNullable.NULL
                        ? null
                        : ((JavaLong) result).getValue());
    }

    private CompletableFuture<String> taskFailure() {
        return postRequest("taskFailure", xInt64.makeHandle(taskId))
                .thenApply(result -> result == xNullable.NULL
                        ? null
                        : ((StringHandle) result).getStringValue());
    }

    private synchronized void finish(Long result, String failure) {
        if (!running) {
            return;
        }

        this.result  = result;
        this.stopped = Instant.now();
        try {
            if (failure != null) {
                if (console != null) {
                    console.println("Unhandled exception: " + failure);
                }
                if (errs != null) {
                    errs.log(ERROR, ERR_UNHANDLED_EXCEPTION, new Object[] {failure}, module);
                }
            }
        } finally {
            unregisterConsole();
            this.running = false;
        }
    }

    /**
     * Release the run's console. Reached only from {@link #finish}, which is synchronized and
     * returns early once {@code running} is false, so this happens exactly once.
     */
    private void unregisterConsole() {
        if (consoleId != null) {
            xExternalConsole.unregister(connector.getNativeContainer(), consoleId);
        }
    }

    @Override
    public boolean running() {
        return running;
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
    public void kill() {
        if (running) {
            postRequest("killTask", xInt64.makeHandle(taskId)).join();
        }
    }

    @Override
    public Long result() {
        return result;
    }

    @Override
    public File console() {
        return null;
    }

    private final InterpreterConnector connector;
    private final ModuleStructure      module;
    private final PrintStream          console;
    private final ErrorListener        errs;
    private final Instant              started;
    private final Long                 consoleId;
    private final long                 taskId;

    // the outcome, and the only genuinely mutable state; the task is already running by the time
    // this object exists, so "running" starts true rather than being switched on afterwards
    private volatile boolean running = true;
    private volatile Instant stopped;
    private volatile Long    result;
}

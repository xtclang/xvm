package org.xvm.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.lang.ref.Cleaner;

import java.time.Instant;

import java.util.concurrent.CompletableFuture;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.runtime.MainContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

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
                    "registerTask", hModule, hRepository, hConsoleId).join();
            long taskId = ((JavaLong) hTaskId).getValue();

            // the future exists before the task does, so the control can hold it as final
            var completion = new CompletableFuture<ObjectHandle>();

            InterpreterControl control = new InterpreterControl(interpreter, module, console, errs,
                    started, consoleId, taskId, completion);

            CLEANER.register(control, new TaskCleanup(interpreter, module.getSimpleName(), taskId));

            main.invokeAsync("startTask", hTaskId).whenComplete((r, e) -> {
                if (e == null) {
                    TupleHandle tuple   = (TupleHandle) r;
                    long        result  = ((JavaLong) tuple.m_ahValue[0]).getValue();
                    String      failure = ((StringHandle) tuple.m_ahValue[1]).getStringValue();
                    control.finish(result, failure);
                    completion.complete(r);
                } else {
                    control.finish(-1, e.toString());
                    completion.completeExceptionally(e);
                }
            });
            return control;
        } catch (RuntimeException e) {
            // nothing was constructed, so there is no half-started object to unwind - just release
            // the console this factory registered
            if (consoleId != null) {
                xExternalConsole.unregister(interpreter.getNativeContainer(), consoleId);
            }
            throw e;
        }
    }

    private InterpreterControl(InterpreterConnector connector, ModuleStructure module,
                               PrintStream console, ErrorListener errs, Instant started,
                               Long consoleId, long taskId,
                               CompletableFuture<ObjectHandle> completion) {
        this.connector  = connector;
        this.module     = module;
        this.console    = console;
        this.errs       = errs;
        this.started    = started;
        this.consoleId  = consoleId;
        this.taskId     = taskId;
        this.completion = completion;
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

    private synchronized void finish(long result, String failure) {
        if (!running) {
            return;
        }

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
            this.running = false;
            this.stopped = Instant.now();
            unregisterConsole();
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

    /**
     * A task's that deletes a task's file-system root after its Control becomes phantom reachable.
     */
    private record TaskCleanup(InterpreterConnector connector, String moduleName, long taskId)
            implements Runnable {
        @Override
        public void run() {
            connector.getMainContainer().invokeAsync("deleteTaskDirectory",
                    xInt64.makeHandle(taskId), xString.makeHandle(moduleName)).join();
        }
    }

    @Override
    public boolean running() {
        return running;
    }

    @Override
    public void join() {
        completion.join();
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

    private final InterpreterConnector connector;
    private final ModuleStructure      module;
    private final PrintStream          console;
    private final ErrorListener        errs;
    private final Instant              started;
    private final Long                 consoleId;
    private final long                 taskId;

    private final CompletableFuture<ObjectHandle> completion;

    // the outcome, and the only genuinely mutable state; the task is already registered and
    // running by the time this object exists, so "running" starts true
    private volatile boolean running = true;
    private volatile Instant stopped;
    private volatile Long    result;

    private static final Cleaner CLEANER = Cleaner.create();
}

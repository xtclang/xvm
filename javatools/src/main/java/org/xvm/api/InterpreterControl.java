package org.xvm.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import java.time.Instant;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.MultiMethodStructure;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

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

import static org.xvm.asm.ErrorListener.at;

import static org.xvm.api.EmbeddingSupport.ERR_UNHANDLED_EXCEPTION;

/**
 * Interpreter-backed management and monitoring for one runner task.
 */
class InterpreterControl
        implements EmbeddingSupport.Control {
    /**
     * Create and start the shared interpreter connector that hosts the runner module. Tasks are
     * registered and started directly, so the runner's own run() method, which starts its HTTP
     * endpoint, is not invoked.
     */
    static Connector createConnector(ModuleRepository repository) {
        InterpreterConnector connector = new InterpreterConnector(repository);
        connector.loadModule("runner.xtclang.org");
        connector.start(null);
        return connector;
    }

    /**
     * Create and start a control for the specified module.
     *
     * @param methodName  the method to invoke
     * @param args        the argument array to pass to the method, or null to pass none
     */
    static EmbeddingSupport.Control create(Connector connector, ModuleStructure module,
                                           ModuleRepository repository, String methodName,
                                           String[] args, PrintWriter console, File rootDir,
                                           ErrorListener errs) {
        if (!(connector instanceof InterpreterConnector interpreter)) {
            throw new IllegalArgumentException("An InterpreterConnector is required");
        }

        Instant started   = Instant.now();
        Long    consoleId = null;

        try {
            FileStructure file     = prepareModule(interpreter, module, repository);
            MainContainer main     = interpreter.getMainContainer();
            String[]      callArgs = entryArguments(file.getModule(), methodName, args);

            if (console != null) {
                consoleId = xExternalConsole.register(interpreter.getNativeContainer(), console,
                        consoleIn::readLine);
            }

            ObjectHandle hModule     = xRTModuleTemplate.makeHandle(main, file.getModule());
            ObjectHandle hRepository = xCoreRepository.INSTANCE.makeHandle(repository);
            ObjectHandle hConsoleId  = consoleId == null
                    ? xNullable.NULL
                    : xInt64.makeHandle(consoleId);
            ObjectHandle hRootDir    = rootDir == null
                    ? xNullable.NULL
                    : xString.makeHandle(rootDir.getAbsolutePath());
            ObjectHandle hMethod     = xString.makeHandle(methodName);
            ObjectHandle hArgs       = callArgs == null
                    ? xNullable.NULL
                    : xString.makeArrayHandle(callArgs);

            ObjectHandle hTaskId = postRequest(
                    interpreter, "registerTask",
                    hModule, hRepository, hConsoleId, hRootDir, hMethod, hArgs).join();
            long taskId = ((JavaLong) hTaskId).getValue();

            CompletableFuture<Void> completion = new CompletableFuture<>();
            InterpreterControl      control    = new InterpreterControl(interpreter, module, rootDir,
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
            }).whenComplete((_, e) -> {
                if (e == null) {
                    completion.complete(null);
                } else {
                    completion.completeExceptionally(e);
                }
            });
            return control;
        } catch (RuntimeException e) {
            unregisterConsole(interpreter, consoleId);
            throw e;
        }
    }

    /**
     * Select the module's entry method and decide what it is passed, following the command-line
     * runner: an argument array is passed when the method requires one, or when arguments are
     * supplied and the method accepts a parameter.
     *
     * @param module      the module to run
     * @param methodName  the name of the entry method
     * @param args        the supplied arguments, or null for none
     *
     * @return the argument array to pass, or null to pass none
     *
     * @throws IllegalArgumentException if the method is missing, ambiguous, or cannot accept
     *         the arguments
     */
    static String[] entryArguments(ModuleStructure module, String methodName, String[] args) {
        String[]  supplied = args == null ? new String[0] : args;
        Component child    = module.getChild(methodName);
        if (!(child instanceof MultiMethodStructure methods) || methods.methods().size() != 1) {
            throw new IllegalArgumentException((child instanceof MultiMethodStructure ? "Ambiguous" : "Missing")
                    + " method \"" + methodName + "\" in module " + module.getName());
        }

        MethodStructure method        = methods.methods().iterator().next();
        int             requiredCount = method.getRequiredParamCount();
        if (requiredCount > 1) {
            throw new IllegalArgumentException("Unsupported arguments for method \"" + methodName
                    + "\": " + method.getIdentityConstant().getSignature().getValueString());
        }

        boolean passArgs = requiredCount == 1 || supplied.length > 0 && method.getParamCount() > 0;
        if (!passArgs) {
            return null;
        }

        ConstantPool pool = module.getConstantPool();
        try (var _ = ConstantPool.withPool(pool)) {
            TypeConstant typeArg = method.getParam(0).getType();
            if (!pool.ensureArrayType(pool.typeString()).isA(typeArg)) {
                throw new IllegalArgumentException("Unsupported argument type "
                        + typeArg.getValueString() + " for method \"" + methodName + "\"");
            }
        }
        return supplied;
    }

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
                               File rootDir, ErrorListener errs, long taskId, Long consoleId,
                               Instant started, CompletableFuture<Void> completion) {
        this.connector  = connector;
        this.module     = module;
        this.rootDir    = rootDir;
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
                errs.error(ERR_UNHANDLED_EXCEPTION, at(module), failure);
            }
        } finally {
            this.stopped = Instant.now();
            this.running = false;
            unregisterConsole(connector, consoleId);
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
    public Long result() {
        return result;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            // another close stops and releases the run: return once it is done, not while the
            // run may still be going
            released.join();
            return;
        }

        try {
            if (running) {
                try {
                    postRequest(connector, "killTask", xInt64.makeHandle(taskId)).join();
                } catch (CompletionException e) {
                    // the run can end after the check above and before the runner gets the
                    // request, which then finds no task to stop
                    if (!"terminated".equals(taskStatus())) {
                        throw e;
                    }
                }
            }
            // wait for completion without rethrowing its failure so close() can release resources
            completion.exceptionally(_ -> null).join();

            if (rootDir == null) {
                postRequest(connector, "deleteTaskDirectory", xInt64.makeHandle(taskId),
                        xString.makeHandle(module.getSimpleName())).join();
            }
        } finally {
            released.complete(null);
        }
    }

    /**
     * @return the runner's status of the task: "running", "stopped" or "terminated" once the
     *         task has ended and is no longer registered
     */
    private String taskStatus() {
        return ((StringHandle) postRequest(connector, "taskStatus", xInt64.makeHandle(taskId))
                .join()).getStringValue();
    }

    private final InterpreterConnector    connector;
    private final ModuleStructure         module;
    private final File                    rootDir;
    private final ErrorListener           errs;
    private final long                    taskId;
    private final Long                    consoleId;
    private final Instant                 started;
    private final CompletableFuture<Void> completion;
    private final AtomicBoolean           closed   = new AtomicBoolean();
    private final CompletableFuture<Void> released = new CompletableFuture<>();

    private volatile boolean running = true;
    private volatile Instant stopped;
    private volatile Long    result;
}

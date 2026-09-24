package org.xvm.api;

import java.io.PrintWriter;

import java.time.Duration;
import java.time.Instant;

import java.util.List;
import java.util.Map;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.javajit.JitConnector;

import org.xvm.util.Deadline;

import static org.xvm.api.EmbeddingSupport.ERR_UNHANDLED_EXCEPTION;

import static org.xvm.util.Severity.ERROR;

/**
 * Owns one asynchronous JIT invocation in a fresh container of the session's shared runtime.
 *
 * <p>The current JIT executes an invocation synchronously on its Java thread. Closing interrupts
 * that thread and waits for it to exit. Generated code that does not respond to interruption can
 * exhaust the shutdown budget; it must never be reported as stopped while still executing.
 */
class JitControl
        implements EmbeddingSupport.Control {
    static EmbeddingSupport.Control create(JitConnector host, ModuleStructure module,
                                           ModuleRepository repository, PrintWriter console,
                                           String methodName, List<String> arguments,
                                           Map<String, List<String>> injections, ErrorListener errs) {
        PrintWriter output = console == null ? new PrintWriter(System.out, true) : console;
        var request = new JitConnector(repository, host.xvm, output);
        request.loadModule(module);
        // Native initialization must precede findMethods(), which builds the module's TypeInfo.
        // This matches the launcher and avoids caching incomplete constructor metadata.
        request.start(injections);

        var methods = request.findMethods(methodName);
        if (methods.size() != 1) {
            throw new IllegalArgumentException("Missing or ambiguous entry method: " + methodName);
        }
        MethodStructure method = methods.iterator().next();
        var pool = request.getConstantPool();
        int paramCount = method.getParamCount();
        int returnCount = method.getReturnCount();
        if (paramCount > 1 || paramCount == 1
                && !method.getParam(0).getType().equals(pool.ensureArrayType(pool.typeString()))) {
            throw new IllegalArgumentException("JIT entry method must accept zero arguments or String[]");
        }
        if (paramCount == 0 && !arguments.isEmpty()) {
            throw new IllegalArgumentException("JIT entry method does not accept arguments: " + methodName);
        }
        if (returnCount > 1 || returnCount == 1
                && !method.getReturn(0).getType().equals(pool.typeInt64())) {
            throw new IllegalArgumentException("JIT entry method must return void or Int");
        }
        return new JitControl(request, method, arguments, output, module, errs);
    }

    private JitControl(JitConnector request, MethodStructure method, List<String> arguments,
                       PrintWriter output, ModuleStructure module, ErrorListener errs) {
        worker = Thread.ofVirtual().name("XvmJit-" + module.getName()).unstarted(() -> {
            try {
                request.invoke0(method, arguments);
                if (request.failure() == null) {
                    result = request.result();
                } else if (errs != null) {
                    errs.log(ERROR, ERR_UNHANDLED_EXCEPTION, new Object[] {request.failure()}, module);
                }
            } catch (Throwable failure) {
                // Every worker failure must reach its host, including JIT VerifyError/LinkageError.
                output.println("JIT execution failed: " + failure);
                if (errs != null) {
                    errs.log(ERROR, ERR_UNHANDLED_EXCEPTION, new Object[] {failure}, module);
                }
            } finally {
                output.flush();
                stopped = Instant.now();
            }
        });
        worker.start();
    }

    @Override
    public boolean running() {
        return worker.isAlive();
    }

    @Override
    public void join() {
        try {
            worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for JIT execution", e);
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
        boolean interrupted = false;
        if (worker.isAlive()) {
            worker.interrupt();
        }
        try {
            while (worker.isAlive()) {
                try {
                    if (!worker.join(deadline.remaining())) {
                        throw new IllegalStateException("JIT execution did not stop within the shutdown budget");
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private final Instant started = Instant.now();
    private final Thread worker;
    private volatile Instant stopped;
    private volatile Long result;
}

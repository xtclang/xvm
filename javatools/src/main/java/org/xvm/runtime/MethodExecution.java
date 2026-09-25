package org.xvm.runtime;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.ExecutionCode;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.SingletonConstant;

import org.xvm.util.Lazy;

/**
 * Execution state for an exact method body in one service.
 *
 * <p>Retained by ServiceContext, independently of semantic metadata. The definition supplies
 * prepared code and constants. This service owns decoded Ops, frame layout, line mapping and
 * initialization completion; none are published back to the shared declaration. Mutable Op
 * caches and debugger wrappers stay with the service. Singleton values and in-flight
 * initialization remain with their existing container owners.
 */
public final class MethodExecution {
    MethodExecution(MethodStructure method) {
        code = Lazy.of(method::createExecutionCode);
    }

    /** @return this service's fully decoded Ops; failed decoding remains retryable */
    public Op[] getOps() {
        return code.get().ops();
    }

    public int getMaxVars() {
        return code.get().maxVars();
    }

    public int getMaxScopes() {
        return code.get().maxScopes();
    }

    Constant[] localConstants() {
        return code.get().constants();
    }

    /** Line mapping is captured before debugger instrumentation can replace any Op. */
    public int calculateLineNumber(int pc) {
        var lines = code.get().lines();
        return pc < 0 || pc >= lines.length ? 0 : lines[pc];
    }

    /**
     * Initialize this execution's singleton operands before calling the next frame. Failed
     * attempts never set the completion flag; another call retries through the singleton protocol.
     */
    int ensureInitialized(Frame frame, Frame next) {
        if (initialized) {
            return frame.call(next);
        }

        var singletons = new ArrayList<SingletonConstant>();
        var constants = localConstants();
        if (constants != null) {
            for (var constant : constants) {
                collectSingletons(frame, constant, singletons);
            }
        }
        if (singletons.isEmpty()) {
            initialized = true;
            return frame.call(next);
        }

        var fiber = frame.f_fiber;
        long deadline = fiber.getTimeoutStamp();
        var timeout = fiber.getTimeoutHandle();
        var container = frame.f_context.f_container;
        long pausedAt = deadline <= 0 ? 0 : container.currentTimeMillis();
        if (deadline > 0) {
            fiber.clearTimeout();
        }
        return Utils.initConstants(frame, singletons, caller -> {
            if (deadline > 0) {
                long elapsed = container.currentTimeMillis() - pausedAt;
                fiber.setTimeoutHandle(timeout, deadline + Math.max(0, elapsed));
            }
            initialized = true;
            return caller.call(next);
        });
    }

    private void collectSingletons(Frame frame, Constant constant, List<SingletonConstant> result) {
        if (constant instanceof SingletonConstant singleton) {
            // A local alias may still denote a parent-owned value. Preserve canonical owner routing.
            result.add(frame.f_context.f_container.ensureSingletonConstant(singleton));
        } else if (constant instanceof ArrayConstant array) {
            for (var element : array.getValue()) {
                collectSingletons(frame, element, result);
            }
        }
    }

    boolean isInitialized() {
        return initialized;
    }

    private final Lazy<ExecutionCode> code;
    private volatile boolean initialized;
}

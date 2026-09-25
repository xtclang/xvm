package org.xvm.runtime;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.SingletonConstant;

/**
 * Execution state for an exact method body in one service.
 *
 * <p>Retained by ServiceContext, independently of semantic metadata. The definition supplies
 * code and constants; executing it must not record initialization on the shared declaration.
 * Singleton values and in-flight initialization remain with their existing container owners.
 */
public final class MethodExecution {
    MethodExecution(MethodStructure method) {
        this.method = method;
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
        var constants = method.getLocalConstants();
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

    private final MethodStructure method;
    private volatile boolean initialized;
}

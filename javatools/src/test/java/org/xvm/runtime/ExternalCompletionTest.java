package org.xvm.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.xvm.asm.FileStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Fiber.FiberStatus;
import org.xvm.runtime.ServiceContext.CallLaterRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalCompletionTest {
    @Test
    void externalCompletionMarksTheFiberReadyBeforeScheduling() {
        try (var runtime = new Runtime()) {
            var container = new Container(runtime, null, new FileStructure("test").getModuleId()) {
                @Override
                public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type, ObjectHandle options) {
                    throw new UnsupportedOperationException();
                }
            };
            var active = new AtomicReference<Fiber>();
            var scheduled = new AtomicBoolean();
            var readyWhenScheduled = new AtomicBoolean();
            var context = new ServiceContext(container, "test", 0) {
                @Override
                protected void ensureScheduled(boolean asynchronous) {
                    scheduled.set(true);
                    readyWhenScheduled.set(active.get().isReady());
                }
            };
            var fiber = new Fiber(context, new CallLaterRequest(null, null, new ObjectHandle[0], 0));
            active.set(fiber);
            var frame = new Frame(fiber, 0, new Op[0], new ObjectHandle[0], Op.A_IGNORE, null) {
                @Override
                public int wait(CompletableFuture<ObjectHandle> future, int result, Continuation continuation) {
                    // Hold back the wait frame's separate readiness callback. This forces the
                    // ordering that previously allowed a wake-up to arrive before readiness.
                    fiber.setStatus(FiberStatus.Waiting, 0);
                    return Op.R_CALL;
                }
            };
            var completion = new CompletableFuture<ObjectHandle>();
            frame.waitForExternalCompletion(completion, Op.A_IGNORE, _ -> Op.R_NEXT);
            assertFalse(scheduled.get());
            assertFalse(fiber.isReady());

            completion.complete(null);
            assertTrue(scheduled.get());
            assertTrue(readyWhenScheduled.get(), "A wake-up must not depend on callback order");
        }
    }
}

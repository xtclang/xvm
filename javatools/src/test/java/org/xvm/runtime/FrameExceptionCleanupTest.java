package org.xvm.runtime;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.xvm.asm.FileStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ServiceContext.CallLaterRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameExceptionCleanupTest {
    private final Runtime runtime = new Runtime();

    @AfterEach
    void shutdown() {
        runtime.shutdownXVM();
    }

    @Test
    void failureReleasesPendingStateOnlyOnce() {
        var frame = frame();
        var calls = new AtomicInteger();
        frame.addExceptionCleanup(calls::incrementAndGet);
        frame.m_continuation.onException();
        frame.m_continuation.onException();
        assertEquals(1, calls.get());
    }

    @Test
    void successfulReturnDisarmsFailureCleanup() {
        var frame = frame();
        var calls = new AtomicInteger();
        frame.addExceptionCleanup(calls::incrementAndGet);
        assertEquals(Op.R_NEXT, frame.m_continuation.proceed(frame));
        frame.m_continuation.onException();
        assertEquals(0, calls.get());
    }

    @Test
    void asynchronousContinuationCarriesBothPendingCleanups() {
        var caller = frame();
        var nested = frame();
        var callerCalls = new AtomicInteger();
        var nestedCalls = new AtomicInteger();
        nested.addExceptionCleanup(nestedCalls::incrementAndGet);
        caller.addContinuation(frame -> {
            frame.m_frameNext = nested;
            return Op.R_CALL;
        });
        caller.addExceptionCleanup(callerCalls::incrementAndGet);

        assertEquals(Op.R_CALL, caller.m_continuation.proceed(caller));
        nested.m_continuation.onException();
        nested.m_continuation.onException();
        assertEquals(1, callerCalls.get());
        assertEquals(1, nestedCalls.get());
    }

    private Frame frame() {
        var container = new Container(runtime, null, new FileStructure("Test").getModuleId()) {
            @Override
            public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type, ObjectHandle options) {
                throw new UnsupportedOperationException();
            }
        };
        var context = new ServiceContext(container, "test", 0);
        var fiber = new Fiber(context, new CallLaterRequest(null, null, new ObjectHandle[0], 0));
        return new Frame(fiber, 0, new Op[0], new ObjectHandle[0], Op.A_IGNORE, null);
    }
}

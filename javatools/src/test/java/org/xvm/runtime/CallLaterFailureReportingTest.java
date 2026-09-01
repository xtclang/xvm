package org.xvm.runtime;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Op;

import org.xvm.runtime.template.xService;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * "callLater" promises that any failure of the called function is reported as an
 * UnhandledExceptionNotification. It reported the failure from inside a
 * {@link CompletableFuture} completion stage, which discards whatever is thrown out of it, so any
 * failure the reporting code could not handle was lost silently along with the original.
 */
public class CallLaterFailureReportingTest {
    /**
     * The reporting lambda cast the throwable straight to WrapperException. "callLater" hands its
     * future to the caller, and cancelling a CompletableFuture completes it with
     * CancellationException - so the cast raised ClassCastException inside the completion stage,
     * which swallowed it. The handler never ran and the cancellation was never reported anywhere:
     * a failure that the runtime is contractually required to surface just disappeared.
     */
    @Test
    public void aCancelledCallLaterIsStillReported() throws Exception {
        assumeTrue(RuntimeTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        ServiceContext context  = liveService();
        var            reported = recordUnhandledExceptions(context);

        CompletableFuture<ObjectHandle> future = context.callLater(doNothing(), Utils.OBJECTS_NONE);
        assertNotNull(future, "the service must accept the request");

        future.cancel(true);

        assertTrue(await(reported).toString().contains("cancelled"),
                "the reported failure must describe the original cancellation");
    }

    /**
     * The ordinary path - the called function raises an XTC exception, which arrives as a
     * WrapperException - must keep reporting the original exception handle unchanged.
     */
    @Test
    public void aFailedCallLaterReportsTheOriginalException() throws Exception {
        assumeTrue(RuntimeTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        ServiceContext context  = liveService();
        var            reported = recordUnhandledExceptions(context);

        FunctionHandle hRaise = new NativeFunctionHandle(
                (frame, _ahArg, _iReturn) -> frame.raiseException(FAILURE_TEXT));

        assertNotNull(context.callLater(hRaise, Utils.OBJECTS_NONE),
                "the service must accept the request");

        assertTrue(await(reported).toString().contains(FAILURE_TEXT),
                "the handler must receive the exception the function actually raised");
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Create a service that is properly formed, i.e. one that owns a service handle.
     * <p/>
     * A context without a handle reports itself terminated as soon as it runs its first frame
     * (see ServiceContext.isTerminated), and a terminated service silently drops posted requests -
     * including the unhandled-exception notification this test is about.
     */
    private static ServiceContext liveService() {
        ServiceContext context = RuntimeTestSupport.newContainer()
                .createServiceContext("callLater");

        xService.INSTANCE.createServiceHandle(context,
                xService.INSTANCE.getCanonicalClass(), xService.INSTANCE.getCanonicalType());
        return context;
    }

    /**
     * Install an unhandled-exception handler that captures what it is given.
     */
    private static CompletableFuture<ObjectHandle> recordUnhandledExceptions(ServiceContext context) {
        var reported = new CompletableFuture<ObjectHandle>();

        context.m_hExceptionHandler = new NativeFunctionHandle((_frame, ahArg, _iReturn) -> {
            reported.complete(ahArg[0]);
            return Op.R_NEXT;
        });
        return reported;
    }

    private static FunctionHandle doNothing() {
        return new NativeFunctionHandle((_frame, _ahArg, _iReturn) -> Op.R_NEXT);
    }

    private static ObjectHandle await(CompletableFuture<ObjectHandle> reported) throws Exception {
        try {
            return reported.get(REPORT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("a failed callLater must reach the unhandled exception "
                    + "handler, but the failure was discarded by the completion stage", e);
        }
    }


    // ----- constants -----------------------------------------------------------------------------

    private static final String FAILURE_TEXT = "deliberate callLater failure";

    private static final long REPORT_TIMEOUT_SECONDS = 15L;
}

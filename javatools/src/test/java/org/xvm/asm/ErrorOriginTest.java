package org.xvm.asm;


import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Compiler;
import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * A diagnostic records where it was raised FROM, not only what it is about.
 *
 * <p>With several compiles teeing into one host sink, "which of these produced this diagnostic" is
 * otherwise unanswerable from the diagnostic alone. The thread is captured automatically; the fiber
 * is stamped by {@link ErrorListener#onFiber}, because reading it ambiently would be the
 * hidden-ownership hazard this branch removed with {@code ServiceContext.getCurrentContext}.
 *
 * <p>The invariant that matters most here is the LAST test: origin must never reach the
 * deduplication key. If it did, the fan-in case this feature exists for would start emitting one
 * copy of every diagnostic per thread.
 */
public class ErrorOriginTest {
    @Test
    public void aDiagnosticKnowsWhichThreadRaisedIt() {
        ErrorListener.ErrorInfo err = error();

        assertEquals(Thread.currentThread().getName(), err.getOrigin().thread());
        assertEquals(ErrorListener.Origin.NO_FIBER, err.getOrigin().fiber(),
                "a compile-time diagnostic is on no fiber");
    }

    @Test
    public void onFiberStampsWithoutDisturbingTheDelegate() {
        var collected = ErrorList.unlimited();

        collected.onFiber(7L).log(error());

        ErrorListener.ErrorInfo logged = collected.getErrors().getFirst();
        assertEquals(7L, logged.getOrigin().fiber(), "the fiber the frame supplied");
        assertEquals(Thread.currentThread().getName(), logged.getOrigin().thread(),
                "and the thread is still the one that raised it");
        assertTrue(logged.getOrigin().toString().contains("fiber:7"));
    }

    @Test
    public void attributedToIsACopyAndLeavesTheOriginalAlone() {
        ErrorListener.ErrorInfo err     = error();
        ErrorListener.ErrorInfo onFiber = err.attributedTo(err.getOrigin().withFiber(3L));

        assertNotEquals(err.getOrigin(), onFiber.getOrigin());
        assertEquals(ErrorListener.Origin.NO_FIBER, err.getOrigin().fiber(),
                "the original is untouched");
        assertSame(err, err.attributedTo(err.getOrigin()),
                "and re-attributing to the same origin does not copy at all");
    }

    /**
     * The whole point of the fan-in case: eight threads reporting the SAME diagnostic is one
     * diagnostic. If the origin were part of the UID it would be eight.
     */
    @Test
    public void originIsNotPartOfTheDeduplicationKey() throws Exception {
        var errs = ErrorList.unlimited();

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, 8)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        errs.onFiber(i).log(error());
                        return null;
                    })
                    .toList();
            for (var future : pool.invokeAll(tasks)) {
                future.get();
            }
        }

        assertEquals(1, errs.getErrors().size(),
                "same diagnostic, eight threads and eight fibers - still one diagnostic");
    }

    private static ErrorListener.ErrorInfo error() {
        return new ErrorListener.ErrorInfo(
                Severity.ERROR, Compiler.FATAL_ERROR, new Object[]{"same"}, (XvmStructure) null);
    }
}

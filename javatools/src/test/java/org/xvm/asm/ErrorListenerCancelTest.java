package org.xvm.asm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.asm.ErrorList.UNLIMITED;
import static org.xvm.asm.ErrorListener.cancellable;
import static org.xvm.asm.ErrorListener.in;

/**
 * Abandoning the work because nobody wants the answer any more.
 *
 * The compiler already asks {@link ErrorListener#isAbortDesired} at around twenty points so that a
 * spent budget or a FATAL stops it. Nothing drove that from outside, so a host with a reason of
 * its own - an editor whose user has typed again, a cancelled request - had no way to say so and
 * a stale analysis ran to the end.
 */
public class ErrorListenerCancelTest {
    @Test
    public void testTheWorkIsNotAbandonedUntilSomebodySaysSo() {
        ErrorList     errs  = new ErrorList(UNLIMITED);
        AtomicBoolean stop  = new AtomicBoolean();
        ErrorListener quits = cancellable(errs, stop::get);

        quits.error(CODE, in(new Source(SOURCE), 0, 1), "a");
        assertFalse(quits.isAbortDesired(), "an error is not by itself a reason to stop");

        stop.set(true);
        assertTrue(quits.isAbortDesired(), "and now it is");
    }

    /**
     * Cancelling says nothing about what was reported. A host that cancels still has the
     * diagnostics gathered up to that point, and may well want to publish them.
     */
    @Test
    public void testCancellingDoesNotDiscardWhatWasAlreadyReported() {
        ErrorList     errs  = new ErrorList(UNLIMITED);
        AtomicBoolean stop  = new AtomicBoolean();
        ErrorListener quits = cancellable(errs, stop::get);

        quits.error(CODE, in(new Source(SOURCE), 0, 1), "a");
        stop.set(true);

        assertEquals(1, errs.getErrors().size(), "what it heard, it keeps");
        assertTrue(quits.hasSeriousErrors(), "and it still answers for it");
        assertTrue(quits.hasError(CODE));
    }

    /**
     * The wrapped listener's own reason to stop still works, so wrapping a budgeted listener does
     * not quietly remove its budget.
     */
    @Test
    public void testTheWrappedListenersOwnReasonToStopSurvives() {
        ErrorList     budget = new ErrorList(1);
        ErrorListener quits  = cancellable(budget, () -> false);

        quits.error(CODE, in(new Source(SOURCE), 0, 1), "a");

        assertTrue(quits.isAbortDesired(), "the budget is spent, and nobody cancelled");
    }

    /**
     * Speculative work inside a cancelled compilation is just as pointless, so a branch of a
     * cancellable listener is cancellable too.
     */
    @Test
    public void testABranchIsCancellableAsWell() {
        ErrorList     errs   = new ErrorList(UNLIMITED);
        AtomicBoolean stop   = new AtomicBoolean();
        ErrorListener branch = cancellable(errs, stop::get).branch(null);

        assertFalse(branch.isAbortDesired());
        stop.set(true);
        assertTrue(branch.isAbortDesired(), "a branch of a cancelled listener is cancelled");
    }

    /**
     * A branch still buffers: cancelling is about stopping the work, not about changing where the
     * work that did happen was reported.
     */
    @Test
    public void testCancellingDoesNotChangeWhereABranchReports() {
        ErrorList     errs   = new ErrorList(UNLIMITED);
        List<String>  seen   = new ArrayList<>();
        ErrorListener quits  = cancellable(ErrorListener.collecting(err -> seen.add(err.getCode())), () -> false);
        ErrorListener branch = quits.branch(null);

        branch.error(CODE, in(new Source(SOURCE), 0, 1), "a");
        assertEquals(List.of(), seen, "a branch buffers until it is merged");

        branch.merge();
        assertEquals(List.of(CODE), seen);
        assertEquals(0, errs.getErrors().size(), "and it merged into the listener it branched from");
    }

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = "PARSER-03";
}

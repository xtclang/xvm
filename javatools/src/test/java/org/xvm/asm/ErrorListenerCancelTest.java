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
 * <p>The compiler already asks {@link ErrorListener#isAbortDesired} at around twenty points so that a
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
        List<String>  seen   = new ArrayList<>();
        ErrorListener quits  = cancellable(ErrorListener.collecting(err -> seen.add(err.getCode())), () -> false);
        ErrorListener branch = quits.branch(null);

        branch.error(CODE, in(new Source(SOURCE), 0, 1), "a");
        assertEquals(List.of(), seen, "a branch buffers until it is merged");

        branch.merge();
        assertEquals(List.of(CODE), seen);
    }

    @Test
    public void testTeeRetainsCodesFromEitherListener() {
        ErrorList first  = new ErrorList(UNLIMITED);
        ErrorList second = new ErrorList(UNLIMITED);
        first.error(CODE, in(new Source(SOURCE), 0, 1), "a");
        second.error("PARSER-04", in(new Source(SOURCE), 0, 1), "b");

        ErrorListener tee = ErrorListener.tee(first, second);
        assertTrue(tee.hasError(CODE));
        assertTrue(tee.hasError("PARSER-04"));
        assertFalse(tee.hasError("unreported"));
    }

    @Test
    public void testCancellationSurvivesTeeAndNestedBranches() {
        ErrorList     errs   = new ErrorList(UNLIMITED);
        ErrorList     copy   = new ErrorList(UNLIMITED);
        AtomicBoolean stop   = new AtomicBoolean();
        ErrorListener tee    = ErrorListener.tee(cancellable(errs, stop::get), copy);
        ErrorListener branch = tee.branch(null).branch(null);

        branch.error(CODE, in(new Source(SOURCE), 0, 1), "a");
        assertFalse(branch.isAbortDesired());
        assertTrue(errs.getErrors().isEmpty());
        assertTrue(copy.getErrors().isEmpty());
        stop.set(true);
        assertTrue(branch.isAbortDesired());
        branch.merge().merge();
        assertEquals(1, errs.getErrors().size());
        assertEquals(1, copy.getErrors().size());
    }

    @Test
    public void testSilencingReportsPreservesCancellation() {
        for (ErrorListener.Silence why : ErrorListener.Silence.values()) {
            ErrorList     errs  = new ErrorList(UNLIMITED);
            AtomicBoolean stop  = new AtomicBoolean();
            ErrorListener quiet = cancellable(errs, stop::get).silence(why);
            quiet.error(CODE, in(new Source(SOURCE), 0, 1), "a");
            assertTrue(errs.getErrors().isEmpty());
            assertFalse(quiet.hasSeriousErrors());
            assertFalse(quiet.isAbortDesired());
            stop.set(true);
            assertTrue(quiet.isAbortDesired());
            assertTrue(quiet.branch(null).isAbortDesired());
        }
    }

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = "PARSER-03";
}

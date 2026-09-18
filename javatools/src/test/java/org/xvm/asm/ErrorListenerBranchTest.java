package org.xvm.asm;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.asm.ErrorListener.in;

/**
 * Tests that branching and merging behave the same for a listener supplied by a host as they do
 * for an {@link ErrorList}.
 *
 * An embedding host - an LSP server, say - implements {@link ErrorListener} to receive
 * diagnostics, and gets the interface's default {@code branch} and {@code merge}. Those defaults
 * have to agree with the ones {@link ErrorList} overrides, or the compiler behaves differently
 * depending on who is listening.
 */
public class ErrorListenerBranchTest {
    /**
     * A listener of the shape a host would write: it collects, and overrides nothing else.
     */
    private static class HostListener
            implements ErrorListener {
        @Override
        public void log(ErrorInfo err) {
            received.add(err.getCode());
        }

        final List<String> received = new ArrayList<>();
    }

    /**
     * A branch exists to buffer errors that may or may not be reported; deciding to abandon the
     * work is the parent's call, not the branch's. Inheriting a budget of one made a branch ask
     * for an abort as soon as it saw a single error, so a host that collected diagnostics got
     * less of the compiler's work done than one that used an ErrorList.
     */
    @Test
    public void testBranchOfAHostListenerDoesNotAbortAfterOneError() {
        Source        source = new Source(SOURCE);
        HostListener  host   = new HostListener();
        ErrorListener branch = host.branch(null);

        branch.error(CODE, in(source, 0, 1), "a");
        assertFalse(branch.isAbortDesired(), "one error is not a reason to abandon the work");

        branch.error(CODE, in(source, 2, 3), "b");
        assertFalse(branch.isAbortDesired());
    }

    @Test
    public void testBranchOfAHostListenerMergesEverythingItCollected() {
        Source        source = new Source(SOURCE);
        HostListener  host   = new HostListener();
        ErrorListener branch = host.branch(null);

        branch.error(CODE, in(source, 0, 1), "a");
        branch.error(CODE, in(source, 2, 3), "b");
        assertEquals(List.of(), host.received, "a branch buffers until it is merged");

        branch.merge();
        assertEquals(List.of(CODE, CODE), host.received);
    }

    /**
     * An ErrorList branch is the reference behaviour the above has to match.
     */
    @Test
    public void testBranchOfAnErrorListBehavesTheSame() {
        Source        source = new Source(SOURCE);
        ErrorList     parent = new ErrorList(0);
        ErrorListener branch = parent.branch(null);

        branch.error(CODE, in(source, 0, 1), "a");
        assertFalse(branch.isAbortDesired());

        branch.error(CODE, in(source, 2, 3), "b");
        branch.merge();
        assertEquals(2, parent.getErrors().size());
    }

    /**
     * Merging a listener that was never branched is a no-op rather than a failure: there is
     * nothing to merge, and the listener is already the sink.
     */
    @Test
    public void testMergingAnUnbranchedListenerIsHarmless() {
        HostListener host = new HostListener();

        assertSame(host, host.merge());
    }

    /**
     * The interface is functional, so a host can lambda it - and a lambda supplies only log(),
     * inheriting defaults that answer as though nothing had been reported. The compiler asks those
     * questions around a hundred times to decide whether a stage may proceed, so a bare lambda
     * tells it that the host's own diagnostics did not happen.
     */
    @Test
    public void testABareLambdaAnswersAsThoughNothingWasReported() {
        Source        source = new Source(SOURCE);
        List<String>  seen   = new ArrayList<>();
        ErrorListener naive  = err -> seen.add(err.getCode());

        naive.error(CODE, in(source, 0, 1), "a");

        assertEquals(1, seen.size(), "it did receive the diagnostic");
        assertFalse(naive.hasSeriousErrors(), "but it reports that nothing serious happened");
        assertFalse(naive.hasError(CODE));
    }

    /**
     * collecting() is the same one-liner for a host, and answers truthfully.
     */
    @Test
    public void testACollectingListenerAnswersTruthfully() {
        Source        source = new Source(SOURCE);
        List<String>  seen   = new ArrayList<>();
        ErrorListener host   = ErrorListener.collecting(err -> seen.add(err.getCode()));

        host.error(CODE, in(source, 0, 1), "a");

        assertEquals(1, seen.size());
        assertTrue(host.hasSeriousErrors());
        assertTrue(host.hasError(CODE));
        assertFalse(host.isAbortDesired(), "an error is not by itself a reason to abandon");

        host.fatal(CODE, in(source, 2, 3), "b");
        assertTrue(host.isAbortDesired(), "a fatal is");
    }

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = "PARSER-03";
}

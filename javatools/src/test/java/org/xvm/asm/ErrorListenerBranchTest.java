package org.xvm.asm;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

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
        public boolean log(ErrorInfo err) {
            received.add(err.getCode());
            return false;
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

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = "PARSER-03";
}

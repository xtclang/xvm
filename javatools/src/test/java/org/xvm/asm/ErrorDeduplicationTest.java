package org.xvm.asm;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.xvm.asm.ErrorListener.in;

/**
 * Tests that {@link ErrorList} suppresses only genuine duplicates.
 *
 * <p>An ErrorList drops any error whose UID it has already seen, so anything the UID fails to
 * distinguish is not merely reported once - it is discarded, uncounted and unannounced.
 */
public class ErrorDeduplicationTest {
    /**
     * An expression and the larger expression containing it start at the same character, so two
     * diagnostics differing only in where they end is an ordinary shape, not a contrived one.
     */
    @Test
    public void testErrorsDifferingInEndPositionAreBothKept() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        errs.error(CODE, in(source, 0, 10), "a", "b");
        errs.error(CODE, in(source, 0, 40), "a", "b");

        assertEquals(2, errs.getErrors().size());
    }

    /**
     * "Aa" and "BB" have the same String hash, so parameters that are digested rather than
     * compared make these two diagnostics indistinguishable.
     */
    @Test
    public void testErrorsWithCollidingParameterHashesAreBothKept() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        assertEquals("Aa".hashCode(), "BB".hashCode(), "the premise of this test");

        errs.error(CODE, in(source, 0, 10), "Aa", "x");
        errs.error(CODE, in(source, 0, 10), "BB", "x");

        assertEquals(2, errs.getErrors().size());
    }

    /**
     * The same diagnostic twice is still one diagnostic.
     */
    @Test
    public void testIdenticalErrorsAreSuppressed() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        errs.error(CODE, in(source, 0, 10), "a", "b");
        errs.error(CODE, in(source, 0, 10), "a", "b");

        assertEquals(1, errs.getErrors().size());
    }

    /**
     * The origin says which thread reported a diagnostic. It must not take part in deduplication:
     * two reports of the same problem from two threads are one problem, and keying on the thread
     * would turn every duplicate into a distinct diagnostic - which is the failure this class
     * exists to prevent.
     */
    @Test
    public void testTheOriginIsNotPartOfTheIdentity() throws Exception {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        errs.error(CODE, in(source, 0, 10), "a", "b");

        // the same diagnostic, reported from a different thread
        Thread other = new Thread(() ->
                errs.error(CODE, in(source, 0, 10), "a", "b"), "other");
        other.start();
        other.join();

        assertEquals(1, errs.getErrors().size(), "the same problem twice is still one problem");
    }

    @Test
    public void testTheOriginRecordsTheReportingThread() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        errs.error(CODE, in(source, 0, 10), "a", "b");

        assertEquals(Thread.currentThread().getName(), errs.getErrors().get(0).origin().thread());
    }

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = "PARSER-03";
}

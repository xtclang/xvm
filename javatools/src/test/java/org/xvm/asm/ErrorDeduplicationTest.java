package org.xvm.asm;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that {@link ErrorList} suppresses only genuine duplicates.
 *
 * An ErrorList drops any error whose UID it has already seen, so anything the UID fails to
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

        errs.log(Severity.ERROR, CODE, new Object[]{"a", "b"}, source, 0, 10);
        errs.log(Severity.ERROR, CODE, new Object[]{"a", "b"}, source, 0, 40);

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

        errs.log(Severity.ERROR, CODE, new Object[]{"Aa", "x"}, source, 0, 10);
        errs.log(Severity.ERROR, CODE, new Object[]{"BB", "x"}, source, 0, 10);

        assertEquals(2, errs.getErrors().size());
    }

    /**
     * The same diagnostic twice is still one diagnostic.
     */
    @Test
    public void testIdenticalErrorsAreSuppressed() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        errs.log(Severity.ERROR, CODE, new Object[]{"a", "b"}, source, 0, 10);
        errs.log(Severity.ERROR, CODE, new Object[]{"a", "b"}, source, 0, 10);

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

        errs.log(Severity.ERROR, CODE, new Object[]{"a", "b"}, source, 0, 10);

        // the same diagnostic, reported from a different thread
        Thread other = new Thread(() ->
                errs.log(Severity.ERROR, CODE, new Object[]{"a", "b"}, source, 0, 10), "other");
        other.start();
        other.join();

        assertEquals(1, errs.getErrors().size(), "the same problem twice is still one problem");
    }

    @Test
    public void testTheOriginRecordsTheReportingThread() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        errs.log(Severity.ERROR, CODE, new Object[]{"a", "b"}, source, 0, 10);

        assertEquals(Thread.currentThread().getName(),
                errs.getErrors().get(0).origin().thread());
    }

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = "PARSER-03";
}

package org.xvm.asm;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Source;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.asm.ErrorListener.in;

/**
 * Recording a diagnostic and deciding to abandon the work are two questions.
 */
public class ErrorListenerAbortTest {
    /**
     * The budget still stops the work; it is just asked for rather than returned from the act of
     * recording.
     */
    @Test
    public void testTheErrorBudgetStillAborts() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(2);

        errs.error(CODE, in(source, 0, 1), "a");
        assertFalse(errs.isAbortDesired(), "one of a budget of two");

        errs.error(CODE, in(source, 2, 3), "b");
        assertTrue(errs.isAbortDesired(), "the budget is spent");

        assertEquals(2, errs.getErrors().size());
    }

    @Test
    public void testUnlimitedNeverAbortsOnCount() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(ErrorList.UNLIMITED);

        IntStream.range(0, 50).forEach(i -> errs.error(CODE, in(source, i, i + 1), "a"));

        assertFalse(errs.isAbortDesired());
        assertTrue(errs.hasSeriousErrors());
    }

    /**
     * A FATAL abandons the work whatever the budget.
     */
    @Test
    public void testFatalAbortsRegardlessOfBudget() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(ErrorList.UNLIMITED);

        errs.fatal(CODE, in(source, 0, 1), "a");

        assertTrue(errs.isAbortDesired());
    }

    /**
     * RUNTIME is the listener of last resort, reached when nobody supplied one. It used to throw
     * from inside log(), which pre-empted whatever the detecting code was about to throw and made
     * the exception type a property of who was listening. It reports and answers the abort
     * question instead.
     */
    @Test
    public void testTheFallbackListenerReportsRatherThanThrowing() {
        Source source = new Source(SOURCE);
        var runtime = new ErrorListener.RuntimeErrorListener();

        assertDoesNotThrow(() ->
                runtime.error(CODE, in(source, 0, 1), "a", "b"));
        assertTrue(runtime.isAbortDesired(), "it still says the work should stop, when asked");
    }

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = "PARSER-03";
}

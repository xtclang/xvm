package org.xvm.asm;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.asm.ErrorListener.BLACKHOLE;
import static org.xvm.asm.ErrorListener.PROBE;
import static org.xvm.asm.ErrorListener.in;

/**
 * The compiler is silent for two different reasons, and says which it means.
 */
public class ErrorListenerSilenceTest {
    /**
     * The two are separate names for the same behaviour. They have to stay indistinguishable at
     * runtime: the moment anything can branch on which one it holds, the listener has become the
     * mode flag it exists to avoid.
     */
    @Test
    public void testTheTwoSilencesAreIndistinguishableInBehaviour() {
        Source source = new Source(SOURCE);

        assertNotSame(PROBE, BLACKHOLE, "they are separate constants");

        for (ErrorListener errs : new ErrorListener[]{PROBE, BLACKHOLE}) {
            errs.log(org.xvm.util.Severity.ERROR, CODE, in(source, 0, 1), "a");
            assertFalse(errs.isAbortDesired());
            assertFalse(errs.hasSeriousErrors());
            assertTrue(errs.isSilent());
            assertSame(errs, errs.merge(), "a silent listener absorbs its own branch");
        }
    }

    /**
     * Suppressing a cascade is a decision, not an absence: a result built from incomplete
     * information describes the incompleteness rather than the user's code, so the rest of that
     * computation is discarded rather than reported.
     */
    @Test
    public void testSuppressCascadeIsSilentAndDoesNotDisturbTheCaller() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        ErrorListener suppressed = errs.suppressCascade();
        suppressed.error(CODE, in(source, 0, 1), "a", "b");

        assertTrue(suppressed.isSilent());
        assertEquals(0, errs.getErrors().size(), "the caller's listener is untouched");
    }

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = "PARSER-03";
}

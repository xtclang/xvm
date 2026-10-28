package org.xvm.asm;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.asm.ErrorListener.Silence.CASCADE;
import static org.xvm.asm.ErrorListener.Silence.DISCARD;
import static org.xvm.asm.ErrorListener.Silence.PROBE;
import static org.xvm.asm.ErrorListener.in;
import static org.xvm.asm.ErrorListener.silent;

/**
 * The compiler is silent for three different reasons, and says which it means.
 */
public class ErrorListenerSilenceTest {
    /**
     * The reasons are separate names for the same behaviour. They have to stay indistinguishable
     * at runtime: the moment anything can branch on which one it holds, the listener has become
     * the mode flag it exists to avoid.
     */
    @Test
    public void testTheSilencesAreIndistinguishableInBehaviour() {
        Source source = new Source(SOURCE);

        assertNotSame(silent(PROBE), silent(DISCARD), "they are separate constants");

        List.of(silent(PROBE), silent(CASCADE), silent(DISCARD)).forEach(errs -> {
            errs.log(Severity.ERROR, CODE, in(source, 0, 1), "a");
            assertFalse(errs.isAbortDesired());
            assertFalse(errs.hasSeriousErrors());
            assertTrue(errs.isSilent());
            assertSame(errs, errs.merge(), "a silent listener absorbs its own branch");
        });
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

        ErrorListener suppressed = errs.silence(CASCADE);
        suppressed.error(CODE, in(source, 0, 1), "a", "b");

        assertTrue(suppressed.isSilent());
        assertEquals(0, errs.getErrors().size(), "the caller's listener is untouched");
    }

    /**
     * The reason makes a suppression decision inspectable. It does not retain discarded messages
     * or select a different compiler algorithm.
     */
    @Test
    public void testASilentListenerSaysWhyItIsSilent() {
        ErrorList errs = new ErrorList();

        assertEquals(PROBE,   silent(PROBE).silenceReason());
        assertEquals(CASCADE, errs.silence(CASCADE).silenceReason());
        assertEquals(DISCARD, silent(DISCARD).silenceReason());

        assertNull(errs.silenceReason(), "a listener that is not silent has no reason");
    }

    /**
     * A derived silence retains the original destination and its abort policy, not discarded
     * messages. A standalone silence has no parent to preserve.
     */
    @Test
    public void testADerivedSilenceKeepsTheListenerItSilenced() {
        ErrorList errs = new ErrorList();

        var derived = assertInstanceOf(ErrorListener.SilentErrorListener.class, errs.silence(CASCADE));
        assertSame(errs, derived.suppressed());

        var shared = assertInstanceOf(ErrorListener.SilentErrorListener.class, silent(PROBE));
        assertNull(shared.suppressed(), "there was no listener to derive it from");
    }

    /**
     * Silencing an already-silent listener is the same silence, so it stays safe to call per use
     * rather than having to be held.
     */
    @Test
    public void testSilencingASilenceIsIdempotent() {
        ErrorListener quiet = new ErrorList().silence(CASCADE);

        assertSame(quiet, quiet.silence(CASCADE));
        assertSame(quiet, quiet.silence(PROBE), "already silent; a second reason adds nothing");
    }

    /**
     * The one-argument constructor expresses a standalone sink without a null parent argument.
     */
    @Test
    public void testStandaloneConstructorDiscardsWithoutAnAbortPolicy() {
        var quiet = new ErrorListener.SilentErrorListener(PROBE);
        quiet.fatal(CODE, ErrorListener.NOWHERE, "a", "b");

        assertNull(quiet.suppressed());
        assertEquals(PROBE, quiet.silenceReason());
        assertTrue(quiet.isSilent());
        assertFalse(quiet.hasSeriousErrors());
        assertFalse(quiet.hasError(CODE));
        assertFalse(quiet.isAbortDesired());
    }

    /**
     * An explicitly supplied parent keeps its live abort policy, even after wrapper construction.
     */
    @Test
    public void testParentConstructorPreservesLaterAbortRequests() {
        ErrorList parent = new ErrorList(ErrorList.FIRST_ERROR);
        var quiet = new ErrorListener.SilentErrorListener(parent, CASCADE);
        quiet.error(CODE, ErrorListener.NOWHERE, "discarded", "report");
        assertTrue(parent.getErrors().isEmpty());
        assertFalse(quiet.isAbortDesired());

        parent.error(CODE, ErrorListener.NOWHERE, "retained", "report");
        assertSame(parent, quiet.suppressed());
        assertTrue(quiet.isAbortDesired());
        assertFalse(quiet.hasSeriousErrors());
    }

    /**
     * A missing reason is a caller error for both new and already-silent listeners.
     */
    @Test
    @SuppressWarnings("DataFlowIssue") // Deliberately exercise Java callers violating @NotNull.
    public void testSilenceAlwaysRequiresAReason() {
        assertThrows(NullPointerException.class, () -> new ErrorListener.SilentErrorListener(null));
        assertThrows(NullPointerException.class, () -> new ErrorList().silence(null));
        assertThrows(NullPointerException.class, () -> silent(PROBE).silence(null));
    }

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = "PARSER-03";
}

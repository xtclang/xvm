package org.xvm.asm;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener.Site;

import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.asm.ErrorListener.NOWHERE;
import static org.xvm.asm.ErrorListener.in;

/**
 * Tests the reporting API that takes the message parameters as a trailing varargs.
 */
public class ErrorListenerSiteTest {
    @Test
    public void testParametersArePassedAsATrailingVarargs() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        errs.error(CODE, in(source, 3, 7), "a", "b");

        ErrorListener.ErrorInfo err = errs.getErrors().get(0);
        assertEquals(Severity.ERROR, err.getSeverity());
        assertEquals(CODE, err.getCode());
        assertArrayEqualsAsList(new Object[]{"a", "b"}, err.getParams());
    }

    @Test
    public void testSeverityVerbsAgreeWithTheSeverityTheyName() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        errs.info (CODE, in(source, 0, 1), "x", "y");
        errs.warn (CODE, in(source, 1, 2), "x", "y");
        errs.error(CODE, in(source, 2, 3), "x", "y");
        errs.fatal(CODE, in(source, 3, 4), "x", "y");

        List<Severity> actual = new ArrayList<>();
        errs.getErrors().forEach(err -> actual.add(err.getSeverity()));
        assertEquals(List.of(Severity.INFO, Severity.WARNING, Severity.ERROR, Severity.FATAL),
                actual);
    }

    /**
     * The point of Site being a closed set: a host republishing diagnostics - an LSP server
     * turning them into editor squiggles - switches over it exhaustively, instead of testing
     * which of several nullable fields happened to be populated.
     */
    @Test
    public void testSiteIsExhaustivelySwitchable() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        errs.error(CODE, in(source, 3, 7), "a", "b");
        errs.error(CODE, NOWHERE, "c", "d");

        assertInstanceOf(Site.In.class, errs.getErrors().get(0).site());
        assertInstanceOf(Site.None.class, errs.getErrors().get(1).site());

        List<String> published = new ArrayList<>();
        for (ErrorListener.ErrorInfo err : errs.getErrors()) {
            published.add(switch (err.site()) {
                case Site.In site -> site.lPosStart() + ".." + site.lPosEnd();
                case Site.At site -> site.xs().getDescription();
                case Site.None ignore -> "whole file";
            });
        }
        assertEquals(List.of("3..7", "whole file"), published);
    }

    /**
     * A tee reports to both, and answers the abort question for either: a caller that wrapped a
     * budgeted listener has to keep getting the stop it asked for.
     */
    @Test
    public void testATeeReportsToBothAndKeepsEithersAbort() {
        Source    source  = new Source(SOURCE);
        ErrorList watcher = new ErrorList(ErrorList.UNLIMITED);
        ErrorList budget  = new ErrorList(1);

        ErrorListener both = ErrorListener.tee(budget, watcher);
        both.error(CODE, in(source, 0, 1), "a", "b");

        assertEquals(1, budget.getErrors().size());
        assertEquals(1, watcher.getErrors().size());
        assertTrue(both.isAbortDesired(), "the budgeted side still gets its stop");
    }

    /**
     * Recording with a tee and replaying with logTo is what lets a memoized result tell a later
     * caller what building it had to say. Deduplication makes the replay idempotent.
     */
    @Test
    public void testRecordedDiagnosticsReplayOnceToALaterCaller() {
        Source    source   = new Source(SOURCE);
        ErrorList first    = new ErrorList(ErrorList.UNLIMITED);
        ErrorList recorder = new ErrorList(ErrorList.UNLIMITED);

        ErrorListener.tee(first, recorder).error(CODE, in(source, 0, 1), "a", "b");
        assertEquals(1, first.getErrors().size());

        // a later caller, with its own listener, hears the same thing
        ErrorList later = new ErrorList(ErrorList.UNLIMITED);
        recorder.logTo(later);
        assertEquals(1, later.getErrors().size());

        // and asking twice does not say it twice
        recorder.logTo(later);
        assertEquals(1, later.getErrors().size());
    }

    private static void assertArrayEqualsAsList(Object[] expected, Object[] actual) {
        assertEquals(List.of(expected), List.of(actual));
    }

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = "PARSER-03";
}

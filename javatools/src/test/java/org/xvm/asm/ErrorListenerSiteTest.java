package org.xvm.asm;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.asm.ErrorListener.Site;

import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.asm.ErrorListener.NOWHERE;
import static org.xvm.asm.ErrorListener.in;

import static org.xvm.asm.ErrorListener.tee;

import static org.xvm.asm.ErrorList.FIRST_ERROR;

/**
 * Tests the reporting API that takes the message parameters as a trailing varargs.
 */
public class ErrorListenerSiteTest {
    @Test
    public void testParametersArePassedAsATrailingVarargs() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        errs.error(CODE, in(source, 3, 7), "a", "b");

        ErrorInfo err = errs.getErrors().getFirst();
        assertEquals(Severity.ERROR, err.getSeverity());
        assertEquals(CODE, err.getCode());
        assertEquals(List.of("a", "b"), List.of(err.getParams()));
    }

    @Test
    public void testSeverityVerbsAgreeWithTheSeverityTheyName() {
        Source    source = new Source(SOURCE);
        ErrorList errs   = new ErrorList(10);

        errs.info (CODE, in(source, 0, 1), "x", "y");
        errs.warn (CODE, in(source, 1, 2), "x", "y");
        errs.error(CODE, in(source, 2, 3), "x", "y");
        errs.fatal(CODE, in(source, 3, 4), "x", "y");

        var actual = errs.getErrors().stream().map(ErrorInfo::getSeverity).toList();
        assertEquals(List.of(Severity.INFO, Severity.WARNING, Severity.ERROR, Severity.FATAL), actual);
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

        assertInstanceOf(Site.In.class, errs.getErrors().getFirst().site());
        assertInstanceOf(Site.None.class, errs.getErrors().get(1).site());

        var published = errs.getErrors().stream().map(err -> switch (err.site()) {
            case Site.In site -> site.lPosStart() + ".." + site.lPosEnd();
            case Site.At site -> site.xs().getDescription();
            case Site.None _ -> "whole file";
        }).toList();
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
        ErrorList budget  = new ErrorList(FIRST_ERROR);

        ErrorListener both = tee(budget, watcher);
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

        tee(first, recorder).error(CODE, in(source, 0, 1), "a", "b");
        assertEquals(1, first.getErrors().size());

        // a later caller, with its own listener, hears the same thing
        ErrorList later = new ErrorList(ErrorList.UNLIMITED);
        recorder.logTo(later);
        assertEquals(1, later.getErrors().size());

        // and asking twice does not say it twice
        recorder.logTo(later);
        assertEquals(1, later.getErrors().size());
    }

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = "PARSER-03";
}

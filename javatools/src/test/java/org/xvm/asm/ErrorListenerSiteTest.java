package org.xvm.asm;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener.Site;

import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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

    private static void assertArrayEqualsAsList(Object[] expected, Object[] actual) {
        assertEquals(List.of(expected), List.of(actual));
    }

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = "PARSER-03";
}

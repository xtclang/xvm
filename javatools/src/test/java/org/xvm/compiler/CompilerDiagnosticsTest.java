package org.xvm.compiler;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.ErrorListener.Site;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.asm.ErrorListener.collecting;

/**
 * What a host is told when it compiles source that does not compile.
 *
 * These go through {@link Parser} rather than the whole compiler, because the later stages need a
 * built XDK and these assertions are about the diagnostics themselves - that they arrive, that
 * they carry a usable location, and that a host's own listener hears them.
 */
public class CompilerDiagnosticsTest {
    @Test
    public void testAHostListenerHearsTheDiagnostics() {
        List<ErrorListener.ErrorInfo> heard = new ArrayList<>();
        ErrorListener host = collecting(heard::add);

        parse("""
                module TestSimple {
                    void run() {
                        console.print("no semicolon")
                    }
                }
                """, host);

        assertEquals(1, heard.size());
        assertEquals(Parser.MISSING_SEMICOLON, heard.get(0).getCode());
        assertTrue(host.hasSeriousErrors(), "the listener answers for what it heard");
    }

    /**
     * A diagnostic is only useful to a problem view if it says where. Every one from a source
     * compile should carry a span, not just a code.
     */
    @Test
    public void testEveryDiagnosticCarriesASpan() {
        List<ErrorListener.ErrorInfo> heard = new ArrayList<>();

        parse("""
                module TestSimple {
                    void run() {
                        Int x = ;
                        Int y = ;
                    }
                }
                """, collecting(heard::add));

        assertFalse(heard.isEmpty(), "broken source produces diagnostics");
        for (ErrorListener.ErrorInfo err : heard) {
            Site site = err.site();
            assertInstanceOf(Site.In.class, site, "a source diagnostic belongs in the source");

            Site.In in = (Site.In) site;
            assertTrue(in.lPosEnd() >= in.lPosStart(), "the span does not run backwards");
            assertTrue(err.getSeverity().compareTo(Severity.WARNING) >= 0);
        }
    }

    /**
     * Distinct problems stay distinct. A problem list needs every problem in it, so two
     * diagnostics from one file must both survive to the host - deduplication exists to collapse
     * the same diagnostic reported twice, and must not collapse two different ones.
     */
    @Test
    public void testDistinctProblemsAreBothReported() {
        List<ErrorListener.ErrorInfo> heard = new ArrayList<>();

        parse("""
                module TestSimple {
                    void run() {
                        console.print("one")
                        console.print("two")
                    }
                }
                """, collecting(heard::add));

        assertEquals(2, heard.size(), "both problems reach the host");
        assertNotEquals(heard.get(0).genUID(), heard.get(1).genUID(),
                "two different problems must not share an identity");
    }

    /**
     * The same host listener used for two documents accumulates both, which is what an adapter
     * compiling several files into one problem list relies on.
     *
     * A diagnostic's identity includes the name of the source it came from, so the documents have
     * to be named for this to hold: two unnamed in-memory sources with a problem at the same
     * offset produce the same identity, and the second is dropped as a duplicate. An editor's
     * unsaved buffers are exactly that case - the text is not on disk, but the host knows the
     * URI, so it can name them.
     */
    @Test
    public void testOneListenerAccumulatesAcrossNamedDocuments() {
        ErrorList errs = new ErrorList(ErrorList.UNLIMITED);

        parse(new Source(DOC, "file:///A.x"), errs);
        int afterFirst = errs.getErrors().size();
        parse(new Source(DOC, "file:///B.x"), errs);

        assertTrue(afterFirst > 0);
        assertTrue(errs.getErrors().size() > afterFirst, "the second compile adds to the same list");
    }

    /**
     * The positive control for the test above: without names there is nothing to tell the two
     * documents apart, so this pins why a host has to supply them.
     */
    @Test
    public void testUnnamedDocumentsAreIndistinguishable() {
        ErrorList errs = new ErrorList(ErrorList.UNLIMITED);

        parse(new Source(DOC), errs);
        parse(new Source(DOC), errs);

        assertEquals(1, errs.getErrors().size(), "identical unnamed sources produce one identity, so one diagnostic");
    }

    private static void parse(String source, ErrorListener errs) {
        parse(new Source(source), errs);
    }

    private static void parse(Source source, ErrorListener errs) {
        try {
            new Parser(source, errs).parseSource();
        } catch (CompilerException _) {
            // an unrecoverable parse abandons its progress; the diagnostics are the point here
        }
    }

    private static final String DOC = "module M { void run() { console.print(\"x\") } }";
}

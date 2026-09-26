package org.xvm.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.asm.ErrorListener.Site;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.asm.ErrorList.UNLIMITED;
import static org.xvm.asm.ErrorListener.collecting;

/**
 * What an editor gets, end to end.
 *
 * The rest of the listener tests check the contract a piece at a time. This one does what a
 * language server does - hold several unsaved documents, compile them through one listener, and
 * turn what comes back into placed, per-document problems - because every piece passing
 * separately is not the same as the whole thing working.
 *
 * It deliberately stops at the shape a host needs rather than at any particular protocol: a
 * severity, a code, a message, and a range in a named document.
 */
public class LspRoundTripTest {
    /**
     * The minimum a problem view needs: which file, which line and column, how bad, and why.
     */
    private record Published(String uri, int startLine, int startCol, int endLine, int endCol,
                             Severity severity, String code, String message) {}

    /**
     * The adapter an editor would write, in the shape the API is meant to be used in.
     */
    private static List<Published> compile(String uri, String text, ErrorListener errs) {
        List<ErrorInfo> heard  = new ArrayList<>();
        Source          source = new Source(text, uri);

        try {
            new Parser(source, ErrorListener.tee(errs, collecting(heard::add))).parseSource();
        } catch (CompilerException _) {
            // source that does not compile is the ordinary case for an editor, not an exception
        }

        return heard.stream().map(err -> switch (err.site()) {
            case Site.In in -> new Published(uri,
                    Source.calculateLine(in.lPosStart()), Source.calculateOffset(in.lPosStart()),
                    Source.calculateLine(in.lPosEnd()),   Source.calculateOffset(in.lPosEnd()),
                    err.getSeverity(), err.getCode(), err.getMessage());
            case Site.At at -> new Published(uri, 0, 0, 0, 0,
                    err.getSeverity(), err.getCode(), at.xs().getDescription());
            case Site.None _ -> new Published(uri, 0, 0, 0, 0, err.getSeverity(), err.getCode(), err.getMessage());
        }).toList();
    }

    /**
     * One editing session: two unsaved buffers, one problem list, both documents represented and
     * every problem placed somewhere a squiggle can go.
     */
    @Test
    public void testAnEditorGetsPlacedProblemsForEveryOpenDocument() {
        ErrorList errs = new ErrorList(UNLIMITED);

        Map<String, List<Published>> byDocument = new LinkedHashMap<>();
        byDocument.put(URI_A, compile(URI_A, BROKEN_A, errs));
        byDocument.put(URI_B, compile(URI_B, BROKEN_B, errs));

        byDocument.forEach((uri, found) -> {
            assertFalse(found.isEmpty(), uri + " does not compile, so it must produce problems");
            found.forEach(p -> {
                assertEquals(uri, p.uri(), "a problem belongs to the document it came from");
                assertTrue(p.startLine() >= 0 && p.startCol() >= 0, "the range is placeable");
                assertTrue(p.endLine() > p.startLine()
                        || p.endLine() == p.startLine() && p.endCol() >= p.startCol(),
                        "the range does not run backwards");
                assertNotNull(p.code(), "a problem view groups by code");
                assertNotNull(p.message(), "and shows a message");
                assertTrue(p.severity().isAtLeast(Severity.WARNING));
            });
        });

        assertTrue(errs.hasSeriousErrors(), "the shared listener answers for the whole session");
    }

    /**
     * The shared listener keeps both documents' problems rather than collapsing them, which is
     * what a workspace-wide problem list relies on. This is the case that silently lost
     * diagnostics until an in-memory document could be named.
     */
    @Test
    public void testOneSessionListenerKeepsEveryDocumentsProblems() {
        ErrorList errs = new ErrorList(UNLIMITED);

        compile(URI_A, BROKEN_A, errs);
        int afterFirst = errs.getErrors().size();
        compile(URI_B, BROKEN_B, errs);

        assertTrue(afterFirst > 0);
        assertTrue(errs.getErrors().size() > afterFirst, "the second document adds to the list");

        var names = errs.getErrors().stream().map(err -> {
            Site.In in = assertInstanceOf(Site.In.class, err.site(), "every problem is placed");
            String name = in.source().getFileName();
            assertNotNull(name, "and names the document it came from");
            return name;
        }).distinct().toList();
        assertEquals(List.of(URI_A, URI_B), names, "both documents are represented");
    }

    /**
     * A line and column an editor can actually use: the first problem in a document whose only
     * fault is on line 3 is reported on line 3, not at the start of the file.
     */
    @Test
    public void testTheRangePointsAtTheOffendingLine() {
        List<Published> found = compile(URI_A, BROKEN_A, new ErrorList(UNLIMITED));

        Published first = found.getFirst();
        assertEquals(2, first.startLine(), "zero-based line 2 is the console.print line");
        assertTrue(first.startCol() > 0, "and it is not column zero");
    }

    private static final String URI_A = "file:///Untitled-1.x";
    private static final String URI_B = "file:///Untitled-2.x";

    private static final String BROKEN_A = """
            module A {
                void run() {
                    console.print("no semicolon")
                }
            }
            """;

    private static final String BROKEN_B = """
            module B {
                void run() {
                    Int x = ;
                }
            }
            """;
}

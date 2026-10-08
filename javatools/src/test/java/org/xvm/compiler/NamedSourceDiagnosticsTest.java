package org.xvm.compiler;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A shared host collector distinguishes unsaved documents by their supplied source names. */
class NamedSourceDiagnosticsTest {
    @Test
    void namedDocumentsRetainBothDiagnostics() {
        var errors = new ErrorList(0);
        parse(new Source(DOCUMENT, "file:///A.x"), errors);
        parse(new Source(DOCUMENT, "file:///B.x"), errors);

        assertEquals(2, errors.getErrors().size());
    }

    @Test
    void unnamedDocumentsStillDeduplicateIdenticalDiagnostics() {
        var errors = new ErrorList(0);
        parse(new Source(DOCUMENT), errors);
        parse(new Source(DOCUMENT), errors);

        assertEquals(1, errors.getErrors().size());
    }

    private static void parse(Source source, ErrorList errors) {
        try {
            new Parser(source, errors).parseSource();
        } catch (CompilerException _) {
            // Parsing may abort; the host's collected diagnostics are the result under test.
        }
    }

    private static final String DOCUMENT = "module M { void run() { console.print(\"x\") } }";
}

package org.xvm.asm;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener.ErrorInfo;

import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Lexer;
import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises listener ownership through the real lexer, without installed XDK modules.
 *
 * <p>These tests use API shapes shared by the old and new listener contracts so the same source can
 * demonstrate the failures on the pre-C1 compiler. Ignoring the old log return value is deliberate:
 * the lexer itself decides when to stop, and the assertions observe its externally visible result.
 */
class ErrorListenerHostRegressionTest {
    /**
     * A host's buffering branch must not stop the lexer at the first recoverable error merely
     * because the host uses a callback instead of ErrorList. Both bad escapes belong to this source.
     */
    @Test
    void hostBranchLetsLexerReportBothErrors() {
        var received = new ArrayList<ErrorInfo>();
        ErrorListener host = received::add;
        var branch = host.branch(null);
        var source = new Source("\"\\q\" \"\\w\"");

        assertDoesNotThrow(() -> new Lexer(source, branch).forEachRemaining(token -> {}));
        assertEquals(0, received.size(), "speculative reports stay buffered until accepted");
        assertSame(host, branch.merge());
        assertEquals(List.of(Lexer.STRING_BAD_ESC, Lexer.STRING_BAD_ESC),
                received.stream().map(ErrorInfo::getCode).toList());
        received.forEach(error -> assertSame(source, error.getSource()));
    }

    /**
     * Once the owner requests an abort, a previously created branch must not keep lexing just
     * because its local count is unlimited. Suppression and buffering cannot revoke that request.
     */
    @Test
    void parentAbortReachesTheLexerThroughAnExistingBranch() {
        var parent = new ErrorList(0);
        var branch = parent.branch(null);
        parent.log(new ErrorInfo(Severity.FATAL, "PARSER-03", new Object[]{"a", "b"}, null));

        assertThrows(CompilerException.class,
                () -> new Lexer(new Source("\"\\q\""), branch).forEachRemaining(token -> {}));
        assertEquals(1, parent.getErrors().size(), "aborted trial reports were not merged");
    }
}

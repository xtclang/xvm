package org.xvm.compiler;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.asm.ErrorList.UNLIMITED;

/**
 * A speculative parse is a branch: keeping it keeps what it had to say, dropping it drops the lot.
 *
 * The parser cannot currently report anything below ERROR, so the kept-warning case here is a
 * guard rather than a reproduction. It is worth guarding: the machinery this replaced discarded
 * every sub-ERROR diagnostic unconditionally, and a kept attempt is the real parse - its tokens
 * are not put back and nothing re-reads them - so the first warning the parser ever learned to
 * report would have vanished with no trace.
 */
public class ParserAttemptTest {
    /**
     * A kept attempt's diagnostics reach the caller.
     */
    @Test
    public void testAKeptAttemptKeepsWhatItReported() {
        ErrorList errs   = new ErrorList(UNLIMITED);
        Parser    parser = new Parser(new Source(SOURCE), errs);

        try (Parser.Attempt attempt = parser.attempt()) {
            parser.log(Severity.WARNING, CODE, 0, 1);
            assertTrue(attempt.isClean(), "a warning is not a reason to abandon the attempt");
            assertEquals(0, errs.getErrors().size(), "a branch buffers until it is kept");
            attempt.keep();
        }

        assertEquals(1, errs.getErrors().size(), "the attempt was kept, so its report counts");
        assertEquals(Severity.WARNING, errs.getErrors().get(0).getSeverity());
    }

    /**
     * A discarded attempt's diagnostics do not. This is the half that already worked, and it has
     * to keep working: speculative parses are the compiler's normal mode, and reporting from the
     * roads it did not take would bury the ones it did.
     */
    @Test
    public void testADiscardedAttemptReportsNothing() {
        ErrorList errs   = new ErrorList(UNLIMITED);
        Parser    parser = new Parser(new Source(SOURCE), errs);

        try (Parser.Attempt attempt = parser.attempt()) {
            parser.log(Severity.WARNING, CODE, 0, 1);
            // never kept
        }

        assertEquals(0, errs.getErrors().size(), "the attempt did not happen");
    }

    /**
     * An error abandons the attempt immediately rather than letting the parser try to recover
     * inside a guess, and still reaches nobody unless the attempt is somehow kept.
     */
    @Test
    public void testAnErrorAbandonsTheAttemptAndStaysBuffered() {
        ErrorList errs   = new ErrorList(UNLIMITED);
        Parser    parser = new Parser(new Source(SOURCE), errs);

        assertThrows(CompilerException.class, () -> {
            try (Parser.Attempt attempt = parser.attempt()) {
                parser.log(Severity.ERROR, CODE, 0, 1);
            }
        });

        assertEquals(0, errs.getErrors().size(), "a failed guess is not the user's problem");
    }

    /**
     * The parser does not try to recover inside an attempt: a syntax error there is the answer.
     */
    @Test
    public void testRecoveryIsOffWhileSpeculating() {
        Parser parser = new Parser(new Source(SOURCE), new ErrorList(UNLIMITED));

        assertTrue(parser.recoverable(), "the real parse recovers");
        try (Parser.Attempt attempt = parser.attempt()) {
            assertFalse(parser.recoverable(), "a guess does not");
        }
        assertTrue(parser.recoverable(), "and recovery comes back afterwards");
    }

    @Test
    public void testNestedKeptAttemptStillBelongsToOuterAttempt() {
        var errors = new ErrorList(UNLIMITED);
        var parser = new Parser(new Source(SOURCE), errors);
        var first = parser.peek();
        try (var outer = parser.attempt()) {
            parser.current();
            try (var inner = parser.attempt()) {
                parser.log(Severity.WARNING, CODE, 0, 1);
                inner.keep();
            }
            assertTrue(outer.hasError(CODE));
            assertTrue(errors.getErrors().isEmpty());
            assertFalse(parser.recoverable());
        }
        assertEquals(first.getId(), parser.peek().getId());
        assertEquals(first.getStartPosition(), parser.peek().getStartPosition());
        assertTrue(parser.recoverable());
        assertTrue(errors.getErrors().isEmpty());
        parser.log(Severity.WARNING, CODE, 2, 3);
        assertEquals(1, errors.getErrors().size(), "the caller's destination is restored");
    }

    @Test
    public void testExceptionRewindsTokensAndRestoresRecovery() {
        var errors = new ErrorList(UNLIMITED);
        var parser = new Parser(new Source(SOURCE), errors);
        var first = parser.peek();
        assertThrows(CompilerException.class, () -> {
            try (var attempt = parser.attempt()) {
                parser.current();
                parser.log(Severity.ERROR, CODE, 0, 1);
            }
        });
        assertEquals(first.getId(), parser.peek().getId());
        assertEquals(first.getStartPosition(), parser.peek().getStartPosition());
        assertTrue(parser.recoverable());
        parser.log(Severity.WARNING, CODE, 2, 3);
        assertEquals(1, errors.getErrors().size());
    }

    @Test
    public void testKeptAttemptAdvancesTokensAndRestoresEvenWhenHostThrows() {
        var failure = new IllegalStateException("host rejected report");
        var parser = new Parser(new Source(SOURCE), err -> { throw failure; });
        assertThrows(IllegalStateException.class, () -> {
            try (var attempt = parser.attempt()) {
                parser.current();
                parser.log(Severity.WARNING, CODE, 0, 1);
                attempt.keep();
            }
        });
        assertEquals(Token.Id.IDENTIFIER, parser.peek().getId());
        assertTrue(parser.recoverable());
        assertThrows(IllegalStateException.class, () -> parser.log(Severity.WARNING, CODE, 2, 3));
    }

    @Test
    public void testNestedAttemptsObserveCallerAbort() {
        var errors = new ErrorList(ErrorList.FIRST_ERROR);
        var parser = new Parser(new Source(SOURCE), errors);
        try (var outer = parser.attempt(); var inner = parser.attempt()) {
            errors.error(CODE, ErrorListener.NOWHERE);
            assertTrue(outer.isAbortDesired());
            assertTrue(inner.isAbortDesired());
        }
    }

    @Test
    public void testModuleNameScanRestoresDestinationForValidAndMalformedNames() {
        for (var source : new String[] {"module example.com {}", "module 123 {}", "class Other {}"}) {
            var errors = new ErrorList(UNLIMITED);
            var parser = new Parser(new Source(source), errors);
            assertEquals(source.startsWith("module example") ? "example.com" : null,
                    parser.parseModuleNameIgnoreEverythingElse());
            assertTrue(errors.getErrors().isEmpty());
            parser.log(Severity.WARNING, CODE, 0, 1);
            assertEquals(1, errors.getErrors().size());
        }
    }

    private static final String SOURCE = "module TestSimple { void run() {} }";
    private static final String CODE   = Parser.MISSING_SEMICOLON;
}

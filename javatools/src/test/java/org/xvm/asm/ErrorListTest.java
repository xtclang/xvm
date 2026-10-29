package org.xvm.asm;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener.ErrorInfo;

import org.xvm.compiler.Lexer;
import org.xvm.compiler.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.xvm.asm.ErrorListener.in;

/**
 * Which errors an ErrorList keeps: each one once, and none that only resembles another.
 */
public class ErrorListTest {
    @Test
    public void errorsThatDifferOnlyInTheirParametersAreAllKept() {
        // "Aa" and "BB" have the same String hash, so a hash of the parameters took the two errors
        // for one, and the second was dropped
        assertEquals("Aa".hashCode(), "BB".hashCode());

        ErrorList errs   = new ErrorList(10);
        Source    source = new Source("module Test {}");
        errs.error(Lexer.STRING_NO_TERM, in(source, 0L, 0L), "Aa");
        errs.error(Lexer.STRING_NO_TERM, in(source, 0L, 0L), "BB");
        // the same error again is a duplicate
        errs.error(Lexer.STRING_NO_TERM, in(source, 0L, 0L), "BB");

        assertEquals(List.of("Aa", "BB"), errs.getErrors().stream().map(ErrorInfo::getParams).map(params -> params[0]).toList());
    }
}

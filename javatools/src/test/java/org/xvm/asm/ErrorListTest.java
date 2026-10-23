package org.xvm.asm;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener.ErrorInfo;

import org.xvm.compiler.Lexer;
import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        errs.log(Severity.ERROR, Lexer.STRING_NO_TERM, new Object[] {"Aa"}, source, 0L, 0L);
        errs.log(Severity.ERROR, Lexer.STRING_NO_TERM, new Object[] {"BB"}, source, 0L, 0L);
        // the same error again is a duplicate
        errs.log(Severity.ERROR, Lexer.STRING_NO_TERM, new Object[] {"BB"}, source, 0L, 0L);

        assertEquals(List.of("Aa", "BB"), errs.getErrors().stream().map(ErrorInfo::getParams).map(params -> params[0]).toList());
    }
}

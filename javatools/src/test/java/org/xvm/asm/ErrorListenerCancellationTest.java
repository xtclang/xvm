package org.xvm.asm;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A cancellation policy survives delegation without becoming a diagnostic. */
class ErrorListenerCancellationTest {
    @Test
    void cancellationSurvivesBranchingAndMerging() {
        var cancelled = new AtomicBoolean();
        var errors = new ErrorList();
        var listener = ErrorListener.cancellable(errors, cancelled::get);
        var branch = listener.branch(null);
        branch.warn("PARSER-03", ErrorListener.NOWHERE, "identifier");
        assertTrue(errors.getErrors().isEmpty());
        cancelled.set(true);
        assertTrue(branch.isAbortDesired());
        var merged = branch.merge();
        assertTrue(merged.isAbortDesired());
        assertEquals(1, errors.getErrors().size());
        assertFalse(errors.hasSeriousErrors());
        assertTrue(merged.hasError("PARSER-03"));
        cancelled.set(false);
        assertFalse(merged.isAbortDesired());
    }

    @Test
    void cancellationDoesNotReplaceTheWrappedBudgetOrAffectAnotherAttempt() {
        var errors = new ErrorList(1);
        var first = ErrorListener.cancellable(errors, () -> false);
        first.error("PARSER-03", ErrorListener.NOWHERE, "identifier");
        assertTrue(first.isAbortDesired());
        assertTrue(first.hasSeriousErrors());
        var second = ErrorListener.cancellable(new ErrorList(), () -> false);
        assertFalse(second.isAbortDesired());
        assertFalse(second.hasSeriousErrors());
    }
}

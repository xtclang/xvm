package org.xvm.asm;


import org.junit.jupiter.api.Test;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Source;
import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * {@link ErrorList} treats {@code ErrorInfo.genUID()} as identity and silently drops anything whose
 * UID it has already seen. So whatever {@code genUID} merges wrongly is a real diagnostic the user
 * never sees, and precision there is a correctness property rather than a nicety.
 *
 * <p>It merged wrongly two ways, both recorded as master issue 47, and both are pinned here because
 * each fix is a change small enough to be undone by accident:
 *
 * <ul>
 * <li>the END position was not in the key - the source clause appended {@code m_lPosStart} twice,
 *     where the second was meant to be {@code m_lPosEnd};</li>
 * <li>parameters were compared by {@code Arrays.hashCode}, a 32-bit digest, so two unrelated
 *     diagnostics whose parameters happened to collide became indistinguishable.</li>
 * </ul>
 */
public class ErrorDeduplicationTest {
    /**
     * Two diagnostics that start at the same offset and cover different spans are two diagnostics.
     *
     * <p>Not a contrived shape: an expression and the larger expression containing it begin at the
     * same character, so a diagnostic about each is exactly this case.
     */
    @Test
    public void spansThatDifferOnlyInWhereTheyEndAreNotDuplicates() {
        Source source = new Source("class Test {}");
        var    errs   = ErrorList.unlimited();

        errs.log(new ErrorListener.ErrorInfo(
                Severity.ERROR, Compiler.FATAL_ERROR, null, source, 0L, 5L));
        errs.log(new ErrorListener.ErrorInfo(
                Severity.ERROR, Compiler.FATAL_ERROR, null, source, 0L, 9L));

        assertEquals(2, errs.getErrors().size(),
                "same start, different end - dropping one loses a real diagnostic");
    }

    /**
     * Parameters are compared by value, not by hash.
     *
     * <p>{@code "Aa"} and {@code "BB"} have the same {@code String.hashCode} (2112), so
     * {@code Arrays.hashCode} of the two single-element arrays is identical. Under the old key these
     * two diagnostics were one.
     */
    @Test
    public void parametersThatMerelyHashAlikeAreNotDuplicates() {
        var errs = ErrorList.unlimited();

        errs.log(new ErrorListener.ErrorInfo(
                Severity.ERROR, Compiler.FATAL_ERROR, new Object[]{"Aa"}, (XvmStructure) null));
        errs.log(new ErrorListener.ErrorInfo(
                Severity.ERROR, Compiler.FATAL_ERROR, new Object[]{"BB"}, (XvmStructure) null));

        assertEquals(2, errs.getErrors().size(),
                "\"Aa\" and \"BB\" hash alike; keying on the hash merged unrelated diagnostics");
    }

    /**
     * The other half of the contract: genuine duplicates still collapse. A fix that made every
     * diagnostic unique would pass the two tests above and be just as wrong.
     */
    @Test
    public void genuineDuplicatesStillCollapse() {
        Source source = new Source("class Test {}");
        var    errs   = ErrorList.unlimited();

        for (int i = 0; i < 5; ++i) {
            errs.log(new ErrorListener.ErrorInfo(
                    Severity.ERROR, Compiler.FATAL_ERROR, new Object[]{"same"}, source, 0L, 5L));
        }

        assertEquals(1, errs.getErrors().size(), "identical diagnostics are one diagnostic");
    }
}

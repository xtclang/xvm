package org.xvm.asm;


import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Pins the memory-visibility contract of {@link MethodStructure}'s native/code state.
 *
 * <p>The native flag and the decoded {@code Code} are written by one thread ({@code markNative()},
 * {@code ensureCode()}) and read by others ({@code getOps()} -&gt; {@code ensureCode()} -&gt;
 * {@code isNative()}). They are read TOGETHER, so they must be published together.</p>
 *
 * <p>Two separate {@code volatile} fields do NOT achieve that. Volatile makes each write visible,
 * but a reader still performs two separate reads, and {@code markNative()} is a multi-step
 * transition: it used to clear the code and set native=false, and only then set native=true. A
 * reader interleaving with that window observes {@code (native=false, code=null)} - a pair that
 * was never a settled state - and takes the "build the Code" branch for a method that is about to
 * be native, surfacing later as {@code getOps()}'s "has no code" {@code IllegalStateException}.</p>
 *
 * <p>The fields are therefore one immutable {@code CodeState} record behind a single volatile
 * reference: readers get an atomic snapshot, writers publish once.</p>
 *
 * <p>This is a REGRESSION PIN, not a race reproduction: the interleaving is provable from the Java
 * Memory Model but not deterministically reproducible, because the transition runs at link time and
 * the window does not reliably overlap. What this test guarantees is that the edge cannot be
 * removed without someone deciding to remove it.</p>
 */
public class MethodStructureVisibilityTest {
    @Test
    public void nativeAndCodeStateArePublishedAsOneSnapshot() throws Exception {
        var field = MethodStructure.class.getDeclaredField("m_codeState");

        assertTrue(Modifier.isVolatile(field.getModifiers()),
                "MethodStructure.m_codeState must be volatile: it is written by one thread and read"
                + " by others, and the volatile read is what makes the snapshot visible");

        assertTrue(field.getType().isRecord(),
                "MethodStructure.m_codeState must hold an immutable record so that the native flag"
                + " and the Code are read as one consistent pair; two separate fields - volatile or"
                + " not - let a reader interleave two reads with a multi-step writer transition");

        assertEquals(2, field.getType().getRecordComponents().length,
                "CodeState must carry exactly the two values that are read together");
    }

    /**
     * The old shape is gone. If either field comes back as an independent field, the snapshot
     * guarantee is silently lost even if both are marked volatile.
     */
    @Test
    public void theSeparateFieldsAreNotReintroduced() {
        for (String sField : new String[] {"m_fNative", "m_code"}) {
            boolean fFound = true;
            try {
                MethodStructure.class.getDeclaredField(sField);
            } catch (NoSuchFieldException e) {
                fFound = false;
            }
            assertTrue(!fFound,
                    "MethodStructure." + sField + " must not exist as an independent field: the"
                    + " native flag and the Code are read together and must be published together"
                    + " via CodeState. Marking them volatile individually is not sufficient.");
        }
    }
}

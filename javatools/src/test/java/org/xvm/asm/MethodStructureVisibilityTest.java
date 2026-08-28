package org.xvm.asm;


import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Pins the memory-visibility contract of {@link MethodStructure}'s native/code state.
 *
 * <p>These two fields are written by one thread ({@code markNative()}, {@code ensureCode()}) and
 * read by others ({@code getOps()} -> {@code ensureCode()} -> {@code isNative()}). They are read
 * TOGETHER - {@code ensureCode()} tests {@code isNative()} before touching {@code m_code} - so
 * without a happens-before edge a reader can pair a stale flag with a null or partially constructed
 * object.</p>
 *
 * <p>This is a REGRESSION PIN, not a race reproduction: the data race is provable from the Java
 * Memory Model but not deterministically reproducible, because the transition runs at link time and
 * the window does not reliably overlap. What this test does guarantee is that the edge cannot be
 * silently removed later by someone dropping the modifier.</p>
 */
public class MethodStructureVisibilityTest {
    @Test
    public void nativeAndCodeStateArePublishedSafely() throws Exception {
        assertVolatile("m_fNative");
        assertVolatile("m_code");
    }

    private static void assertVolatile(String sField) throws Exception {
        var field = MethodStructure.class.getDeclaredField(sField);
        assertTrue(Modifier.isVolatile(field.getModifiers()),
                () -> "MethodStructure." + sField + " must be volatile: it is written by one thread"
                    + " and read by others with no other happens-before edge");
    }
}

package org.xvm.javajit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.xvm.javajit.TypeSystem.HASH;
import static org.xvm.javajit.TypeSystem.HASH_MARKER;
import static org.xvm.javajit.TypeSystem.appendJitSuffix;
import static org.xvm.javajit.TypeSystem.escapeJitName;
import static org.xvm.javajit.TypeSystem.findJitSuffix;
import static org.xvm.javajit.TypeSystem.getJitSuffix;
import static org.xvm.javajit.TypeSystem.removeJitSuffix;

/**
 * Tests for TypeSystem.
 */
public class TypeSystemTest {
    @Test
    public void appendAndReadSuffix() {
        StringBuilder name = new StringBuilder("pkg.Widget");
        assertSame(name, appendJitSuffix(name, 0));
        assertEquals("pkg.Widget" + HASH_MARKER + "0", name.toString());

        String escaped = escapeJitName(name.toString(), true);
        assertEquals("pkg.Widget" + HASH + "0", escaped);
        assertEquals(10, findJitSuffix(escaped));
        assertEquals(0, getJitSuffix(escaped));
        assertEquals("pkg.Widget", removeJitSuffix(escaped));
    }

    @Test
    public void replaceSuffixAndPreserveNestedClassPath() {
        StringBuilder name = new StringBuilder("pkg.Outer" + HASH_MARKER + "7$Inner");
        appendJitSuffix(name, 42);
        assertEquals("pkg.Outer$Inner" + HASH_MARKER + "42", name.toString());

        String escaped = escapeJitName(name.toString(), true);
        assertEquals(42, getJitSuffix(escaped));
        assertEquals("pkg.Outer$Inner", removeJitSuffix(escaped));

        String nestedSuffix = escapeJitName("pkg.Outer" + HASH_MARKER + "7$Inner", true);
        assertEquals(7, getJitSuffix(nestedSuffix));
        assertEquals("pkg.Outer$Inner", removeJitSuffix(nestedSuffix));
    }

    @Test
    public void escapedUserHashIsNotASuffix() {
        String escaped = escapeJitName("pkg.Widget" + HASH + "123", true);
        assertEquals(-1, findJitSuffix(escaped));
        assertEquals(-1, getJitSuffix(escaped));
        assertSame(escaped, removeJitSuffix(escaped));
    }

    @Test
    public void rejectsMultipleInternalMarkers() {
        StringBuilder name = new StringBuilder("pkg.Widget" + HASH_MARKER + "1"
                + HASH_MARKER + "2");
        assertThrows(IllegalArgumentException.class, () -> appendJitSuffix(name, 3));
    }
}

package org.xvm.asm;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.StringConstant;

import org.xvm.util.PackedInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Unit tests for ConstantPool behavior: interning, transfer between pools.
 * These tests verify current behavior before refactoring to ensure nothing breaks.
 */
public class ConstantPoolTest {

    private FileStructure file1;
    private FileStructure file2;
    private ConstantPool pool1;
    private ConstantPool pool2;

    @BeforeEach
    void setUp() {
        // Create two independent file structures with pools for testing transfer
        file1 = new FileStructure("TestModule1");
        file2 = new FileStructure("TestModule2");
        pool1 = file1.getConstantPool();
        pool2 = file2.getConstantPool();
    }

    // ---- Interning tests ----

    @Test
    void testEnsureIntConstant_sameValueReturnsSameInstance() {
        IntConstant c1 = pool1.ensureIntConstant(42);
        IntConstant c2 = pool1.ensureIntConstant(42);

        assertSame(c1, c2, "Same value should return interned constant");
    }

    @Test
    void testEnsureIntConstant_differentValuesReturnDifferentInstances() {
        IntConstant c1 = pool1.ensureIntConstant(42);
        IntConstant c2 = pool1.ensureIntConstant(43);

        assertNotSame(c1, c2);
        assertEquals(PackedInteger.valueOf(42), c1.getValue());
        assertEquals(PackedInteger.valueOf(43), c2.getValue());
    }

    @Test
    void testEnsureStringConstant_sameValueReturnsSameInstance() {
        StringConstant c1 = pool1.ensureStringConstant("hello");
        StringConstant c2 = pool1.ensureStringConstant("hello");

        assertSame(c1, c2, "Same string should return interned constant");
    }

    @Test
    void testEnsureLiteralConstant_differentFormats() {
        // Test that different literal formats are properly handled
        LiteralConstant intLit = pool1.ensureLiteralConstant(Constant.Format.IntLiteral, "42");
        LiteralConstant decLit = pool1.ensureLiteralConstant(Constant.Format.FPLiteral, "3.14");

        assertNotSame(intLit, decLit);
        assertEquals("42", intLit.getValue());
        assertEquals("3.14", decLit.getValue());
    }

    // ---- Pool isolation tests ----

    @Test
    void testConstant_belongsToCreatingPool() {
        IntConstant c = pool1.ensureIntConstant(123);

        assertSame(pool1, c.getConstantPool());
    }

    @Test
    void testConstant_differentPoolsCreateDifferentInstances() {
        IntConstant c1 = pool1.ensureIntConstant(42);
        IntConstant c2 = pool2.ensureIntConstant(42);

        assertNotSame(c1, c2, "Same value in different pools should be different instances");
        assertSame(pool1, c1.getConstantPool());
        assertSame(pool2, c2.getConstantPool());
    }

    // ---- Transfer (transferTo) tests ----

    @Test
    void testTransferTo_createsNewConstantInTargetPool() {
        IntConstant original = pool1.ensureIntConstant(42);

        IntConstant transferred = original.transferTo(pool2);

        assertNotSame(original, transferred);
        assertSame(pool2, transferred.getConstantPool());
        assertEquals(original.getValue(), transferred.getValue());
    }

    @Test
    void testTransferTo_samePoolReturnsSameInstance() {
        IntConstant original = pool1.ensureIntConstant(42);

        // When transferring to same pool, should return same instance
        IntConstant transferred = original.transferTo(pool1);

        assertSame(original, transferred);
    }

    @Test
    void testTransferTo_stringConstant_preservesValue() {
        StringConstant original = pool1.ensureStringConstant("hello world");

        StringConstant transferred = original.transferTo(pool2);

        assertEquals("hello world", transferred.getValue());
        assertSame(pool2, transferred.getConstantPool());
    }

    // ---- Register tests ----

    @Test
    void testRegister_newConstant_addsToPool() {
        IntConstant c = new IntConstant(pool1, Constant.Format.Int64, PackedInteger.valueOf(999));

        IntConstant registered = pool1.register(c);

        assertSame(c, registered);
        assertSame(pool1, registered.getConstantPool());
    }

    @Test
    void testRegister_duplicateValue_returnsExisting() {
        IntConstant c1 = pool1.ensureIntConstant(42);
        IntConstant c2 = new IntConstant(pool1, c1.getFormat(), PackedInteger.valueOf(42));

        IntConstant registered = pool1.register(c2);

        // Should return the existing interned constant, not the new one
        assertSame(c1, registered);
    }

    @Test
    @Disabled("Need to verify: does register() of foreign constant call adoptedBy?")
    void testRegister_foreignConstant_transfersToPool() {
        IntConstant fromPool1 = pool1.ensureIntConstant(42);

        // Registering a constant from another pool should transfer it
        IntConstant registered = pool2.register(fromPool1);

        assertNotSame(fromPool1, registered);
        assertSame(pool2, registered.getConstantPool());
        assertEquals(PackedInteger.valueOf(42), registered.getValue());
    }

    // ---- Composite constant transfer tests ----

    @Test
    @Disabled("Requires module setup - integration test")
    void testAdoptedBy_typeConstant_transfersChildConstants() {
        // TypeConstants contain references to other constants (e.g., class constant)
        // When transferred, all child constants should also be transferred
        // This is complex and may need integration test setup
    }

    // ---- Edge cases ----

    @Test
    void testEnsureIntConstant_zeroValue() {
        IntConstant c = pool1.ensureIntConstant(0);

        assertNotNull(c);
        assertEquals(PackedInteger.valueOf(0), c.getValue());
    }

    @Test
    void testEnsureIntConstant_negativeValue() {
        IntConstant c = pool1.ensureIntConstant(-42);

        assertNotNull(c);
        assertEquals(PackedInteger.valueOf(-42), c.getValue());
    }

    @Test
    void testEnsureIntConstant_maxValue() {
        IntConstant c = pool1.ensureIntConstant(Long.MAX_VALUE);

        assertNotNull(c);
        assertEquals(PackedInteger.valueOf(Long.MAX_VALUE), c.getValue());
    }

    @Test
    void testEnsureStringConstant_emptyString() {
        StringConstant c = pool1.ensureStringConstant("");

        assertNotNull(c);
        assertEquals("", c.getValue());
    }

    @Test
    @Disabled("Need to verify null handling - may throw NPE")
    void testEnsureStringConstant_nullValue() {
        // Document what happens with null - should it throw or create special constant?
        assertThrows(NullPointerException.class, () -> {
            pool1.ensureStringConstant(null);
        });
    }
}

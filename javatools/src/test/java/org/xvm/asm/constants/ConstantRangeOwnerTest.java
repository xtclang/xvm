package org.xvm.asm.constants;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;

import org.xvm.compiler.Token.Id;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for owner-local range constants produced by compile-time numeric operations.
 */
public class ConstantRangeOwnerTest {
    @Test
    public void byteRangeUsesReceiverPoolWithoutAmbientPool() {
        var pool  = new FileStructure("owner").getConstantPool();
        var first = pool.ensureByteConstant(Format.UInt8, 1);
        var last  = pool.ensureByteConstant(Format.UInt8, 3);

        try (var _ = ConstantPool.withPool(null)) {
            var range = (RangeConstant) first.apply(Id.I_RANGE_I, last);

            assertSame(pool, range.getConstantPool());
            assertSame(first, range.getFirst());
            assertSame(last, range.getLast());
            assertFalse(range.isFirstExcluded());
            assertFalse(range.isLastExcluded());
        }
    }

    @Test
    public void byteRangeIgnoresWrongAmbientPool() {
        var poolOwner = new FileStructure("owner").getConstantPool();
        var poolOther = new FileStructure("other").getConstantPool();
        var first     = poolOwner.ensureByteConstant(Format.UInt8, 1);
        var last      = poolOwner.ensureByteConstant(Format.UInt8, 3);

        try (var _ = ConstantPool.withPool(poolOther)) {
            var range = (RangeConstant) first.apply(Id.E_RANGE_E, last);

            assertSame(poolOwner, range.getConstantPool());
            assertTrue(range.isFirstExcluded());
            assertTrue(range.isLastExcluded());
        }
    }

    @Test
    public void intRangeUsesReceiverPoolWithoutAmbientPool() {
        var pool  = new FileStructure("owner").getConstantPool();
        var first = pool.ensureIntConstant(1);
        var last  = pool.ensureIntConstant(3);

        try (var _ = ConstantPool.withPool(null)) {
            var range = (RangeConstant) first.apply(Id.I_RANGE_E, last);

            assertSame(pool, range.getConstantPool());
            assertSame(first, range.getFirst());
            assertSame(last, range.getLast());
            assertFalse(range.isFirstExcluded());
            assertTrue(range.isLastExcluded());
        }
    }
}

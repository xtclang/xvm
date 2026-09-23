package org.xvm.asm;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Token.Id;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Ambient guards preserve an explicit binding and support Java callers without one. */
class ConstantPoolAmbientTest {
    @Test
    void fallbackAppliesOnlyWhenNoPoolIsBound() {
        var bound = new FileStructure("bound").getConstantPool();
        var fallback = new FileStructure("fallback").getConstantPool();
        try (var empty = ConstantPool.withPool(null)) {
            assertNull(ConstantPool.getCurrentPool());
            assertSame(fallback, ConstantPool.currentOr(fallback));
            try (var scope = ConstantPool.withPool(bound)) {
                assertSame(bound, ConstantPool.currentOr(fallback));
            }
            assertNull(ConstantPool.getCurrentPool());
            assertSame(fallback, ConstantPool.currentOr(fallback));
        }
    }

    @Test
    void rangeOperationsUseTheOwnerWithoutAnAmbientBinding() {
        try (var empty = ConstantPool.withPool(null)) {
            var owner = new FileStructure("source").getConstantPool();
            checkRanges(owner, owner);
            assertNull(ConstantPool.getCurrentPool());
        }
    }

    @Test
    void rangeOperationsHonorAnExplicitDestinationPool() {
        var owner = new FileStructure("source").getConstantPool();
        var destination = new FileStructure("destination").getConstantPool();
        try (var scope = ConstantPool.withPool(destination)) {
            checkRanges(owner, destination);
            assertSame(destination, ConstantPool.getCurrentPool());
        }
    }

    private static void checkRanges(ConstantPool owner, ConstantPool expected) {
        for (var format : List.of(Constant.Format.Int8, Constant.Format.UInt8,
                Constant.Format.Nibble, Constant.Format.Int64)) {
            Constant first = format == Constant.Format.Int64
                    ? owner.ensureIntConstant(1) : owner.ensureByteConstant(format, 1);
            Constant last = format == Constant.Format.Int64
                    ? owner.ensureIntConstant(3) : owner.ensureByteConstant(format, 3);
            var operations = switch (format) {
                case Nibble -> List.of(Id.I_RANGE_I);
                case Int8 -> List.of(Id.I_RANGE_I, Id.I_RANGE_E);
                default -> List.of(Id.I_RANGE_I, Id.I_RANGE_E, Id.E_RANGE_I, Id.E_RANGE_E);
            };
            for (var operation : operations) {
                assertSame(expected, first.apply(operation, last).getConstantPool(),
                        format + " " + operation);
            }
            assertSame(owner, first.getConstantPool());
            assertSame(owner, last.getConstantPool());
        }
    }
}

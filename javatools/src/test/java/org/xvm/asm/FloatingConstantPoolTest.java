package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.math.BigDecimal;

import java.util.List;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.ValueConstant;

import org.xvm.type.Decimal128;
import org.xvm.type.Decimal32;
import org.xvm.type.Decimal64;

import org.xvm.util.PackedInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A constant must have its serialized identity before it is interned in a pool.
 */
public class FloatingConstantPoolTest {
    @Test
    public void narrowFormatsInternValuesAfterRounding() throws IOException {
        for (var factory : NARROW_FORMATS) {
            FileStructure file = new FileStructure("test");
            ConstantPool pool = file.getConstantPool();
            ValueConstant one = factory.apply(pool, 1.0f);

            assertSame(one, factory.apply(pool, 1.0001f));
            assertSame(factory.apply(pool, -1.0f), factory.apply(pool, -1.0001f));
            assertNotEquals(factory.apply(pool, 0.0f), factory.apply(pool, -0.0f));
            assertStablePool(file);
        }
    }

    @Test
    public void bfloat16RegressionValuesRemainInternedAfterReading() throws IOException {
        FileStructure file = new FileStructure("test");
        ConstantPool pool = file.getConstantPool();

        assertSame(pool.ensureBFloat16Constant(-1.578125f),
                pool.ensureBFloat16Constant(-1.5748398f));
        assertSame(pool.ensureBFloat16Constant(1.0f),
                pool.ensureBFloat16Constant(1.00390625f));
        assertStablePool(file);
    }

    @Test
    public void floatingConstantsSurviveWriteReadAndFurtherRegistration() throws IOException {
        for (var factory : FLOAT_FORMATS) {
            FileStructure file = new FileStructure("test");
            ConstantPool pool = file.getConstantPool();
            for (float value : new float[] {0.0f, -0.0f, 1.0f, -1.0f, 1.0001f,
                    Float.MIN_VALUE, -Float.MIN_VALUE, Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY, Float.NaN}) {
                factory.apply(pool, value);
            }
            assertStablePool(file);
        }
    }

    @Test
    public void signedFp8NaNsKeepDistinctEncodedIdentities() throws IOException {
        for (var factory : NARROW_FORMATS.subList(0, 2)) {
            FileStructure file = new FileStructure("test");
            ConstantPool pool = file.getConstantPool();
            ValueConstant positive = factory.apply(pool, Float.NaN);
            ValueConstant negative = factory.apply(pool, Float.intBitsToFloat(0xFFC00000));

            assertNotEquals(positive, negative);
            assertSame(positive, factory.apply(pool, Float.NaN));
            assertSame(negative, factory.apply(pool, Float.intBitsToFloat(0xFFC00000)));
            assertStablePool(file);
        }
    }

    @Test
    public void otherNumericRepresentationsKeepTheirSerializedIdentity() throws IOException {
        FileStructure file = new FileStructure("test");
        ConstantPool pool = file.getConstantPool();
        for (String text : List.of("0", "1", "-1", "1.23456789012345678901234567890123456789")) {
            BigDecimal value = new BigDecimal(text);
            pool.ensureDecConstant(new Decimal32(value));
            pool.ensureDecConstant(new Decimal64(value));
            pool.ensureDecConstant(new Decimal128(value));
        }
        for (Constant.Format format : List.of(Constant.Format.Int8, Constant.Format.UInt8,
                Constant.Format.Bit, Constant.Format.Nibble)) {
            pool.ensureByteConstant(format, 0);
            pool.ensureByteConstant(format, 1);
        }
        for (Constant.Format format : List.of(Constant.Format.Int16, Constant.Format.UInt16,
                Constant.Format.Int32, Constant.Format.UInt32, Constant.Format.Int64,
                Constant.Format.UInt64, Constant.Format.Int128, Constant.Format.UInt128,
                Constant.Format.IntN, Constant.Format.UIntN)) {
            pool.ensureIntConstant(PackedInteger.ZERO, format);
            pool.ensureIntConstant(PackedInteger.valueOf(12345), format);
        }
        pool.ensureFloat128Constant(new byte[16]);
        for (int length : new int[] {4, 8, 16, 32, 64}) {
            pool.ensureFloatNConstant(new byte[length]);
            pool.ensureDecNConstant(new byte[length]);
        }
        assertStablePool(file);
    }

    private static void assertStablePool(FileStructure file) throws IOException {
        // Retain these constants even though the synthetic module has no code referencing them.
        file.reregisterConstants(false);
        var expected = List.of(file.getConstantPool().getConstants());
        var bytes = new ByteArrayOutputStream();
        file.assemble(new DataOutputStream(bytes));
        ConstantPool reread = new FileStructure(new ByteArrayInputStream(bytes.toByteArray()))
                .getConstantPool();

        assertEquals(expected, List.of(reread.getConstants()));
        // Reading alone does not build the lookup tables; the JIT registers additional constants.
        assertDoesNotThrow(() -> reread.ensureStringConstant("after reading"));
        for (Constant constant : expected) {
            Constant registered = assertDoesNotThrow(() -> reread.register(constant));
            assertEquals(constant.getPosition(), registered.getPosition());
        }
    }

    private static final List<BiFunction<ConstantPool, Float, ValueConstant>> NARROW_FORMATS =
            List.of(ConstantPool::ensureFloat8e4Constant, ConstantPool::ensureFloat8e5Constant,
                    ConstantPool::ensureFloat16Constant, ConstantPool::ensureBFloat16Constant);

    private static final List<BiFunction<ConstantPool, Float, ValueConstant>> FLOAT_FORMATS =
            List.of(ConstantPool::ensureFloat8e4Constant, ConstantPool::ensureFloat8e5Constant,
                    ConstantPool::ensureFloat16Constant, ConstantPool::ensureBFloat16Constant,
                    ConstantPool::ensureFloat32Constant,
                    (pool, value) -> pool.ensureFloat64Constant(value));
}

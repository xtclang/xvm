package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import java.util.stream.IntStream;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.Orderable;
import org.xtclang.ecstasy.Ordered;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.constants.Float8e4Constant;

import org.xvm.javajit.Ctx;

/**
 * Native Float8e4 ("E4M3") wrapper.
 *
 * <p>Unlike [Float16], the value is carried as the 8-bit FP8 encoding held in a Java int, not as a
 * Java float. An FP8 format has only 256 values, so the encoding is the natural carrier: every
 * representable value is exact by construction, and the box cache below is complete.</p>
 */
public class Float8e4 extends BinaryFPNumber {
    /**
     * Construct an Ecstasy Float8e4 object.
     *
     * @param value  the 8-bit E4M3 encoding, in the low 8 bits
     */
    private Float8e4(int value) {
        $value = value;
    }

    /**
     * The 8-bit E4M3 encoding, in the low 8 bits.
     */
    public final int $value;

    /**
     * Every E4M3 value. An 8-bit format has only 256 of them, so the table is built once during
     * class initialization rather than filled lazily: boxing never allocates, never races, and
     * {@link #$box} always returns the same reference for the same encoding.
     */
    private static final Float8e4[] CACHE =
            IntStream.range(0, 256).mapToObj(Float8e4::new).toArray(Float8e4[]::new);

    /**
     * Obtain a Float8e4 for an 8-bit E4M3 encoding.
     *
     * @param value  an 8-bit E4M3 encoding
     *
     * @return a Float8e4 reference
     */
    public static Float8e4 $box(int value) {
        return CACHE[value & 0xFF];
    }

    /**
     * @param bits  an 8-bit E4M3 encoding
     *
     * @return the value it encodes, as a Java float
     */
    public static float $toFloat(int bits) {
        return Float8e4Constant.toFloat(bits & 0xFF);
    }

    /**
     * E4M3FN reserves no exponent: the only non-finite values are the two NaN encodings.
     *
     * @return true iff the encoded value is finite
     */
    public static boolean $finite(int bits) {
        return (bits & 0x7F) != 0x7F;
    }

    /**
     * @return false always: the E4M3FN format has no infinities
     */
    public static boolean $infinity(int bits) {
        return false;
    }

    /**
     * @return true iff the encoded value is a NaN, i.e. #7F or #FF
     */
    public static boolean $NaN(int bits) {
        return (bits & 0x7F) == 0x7F;
    }

    /**
     * The arithmetic below decodes both encodings to Java floats, operates, and re-encodes. A
     * single rounding from float32 down to an FP8 format is correctly rounded, because float32
     * carries far more than the 2p+2 bits that requires, and its exponent range means an FP8
     * subnormal result is still a normal float32.
     */
    public static int $add(int bits1, int bits2) {
        return $toBits($toFloat(bits1) + $toFloat(bits2));
    }

    public static int $sub(int bits1, int bits2) {
        return $toBits($toFloat(bits1) - $toFloat(bits2));
    }

    public static int $mul(int bits1, int bits2) {
        return $toBits($toFloat(bits1) * $toFloat(bits2));
    }

    public static int $div(int bits1, int bits2) {
        return $toBits($toFloat(bits1) / $toFloat(bits2));
    }

    public static int $mod(int bits1, int bits2) {
        float divisor = $toFloat(bits2);
        float mod     = $toFloat(bits1) % divisor;
        // Ecstasy "mod" is a floored modulo, so it takes the sign of the divisor
        return $toBits(mod != 0 && (mod < 0) != (divisor < 0) ? mod + divisor : mod);
    }

    public static int $rem(int bits1, int bits2) {
        return $toBits($toFloat(bits1) % $toFloat(bits2));
    }

    /**
     * Compare two FP8 encodings by the values they encode. The encodings are sign-magnitude, so
     * they cannot simply be compared as integers.
     *
     * @return a negative, zero or positive int, as {@link Float#compare} defines
     */
    public static int $compare(int bits1, int bits2) {
        return Float.compare($toFloat(bits1), $toFloat(bits2));
    }

    /**
     * @param value  a Java float
     *
     * @return the 8-bit encoding of that value, rounding to nearest with ties to even
     */
    public static int $toBits(float value) {
        return Float8e4Constant.toBits(value);
    }

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Float.toString($toFloat($value)));
    }

    public static String toString$p(int thi$, Ctx ctx) {
        return String.of(ctx, Float.toString($toFloat(thi$)));
    }

    public static long estimateStringLength$p(int thi$, Ctx ctx) {
        return Float.toString($toFloat(thi$)).length();
    }

    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        return appendTo$p($value, ctx, appender);
    }

    public static AppenderᐸCharᐳ appendTo$p(int thi$, Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Float.toString($toFloat(thi$)).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return new BigDecimal(Float.toString($toFloat($value)));
    }

    // ----- Orderable interface -------------------------------------------------------------------

    /**
     * The primitive implementation of:
     *
     * static <CompileType extends Orderable> Ordered compare(CompileType value1, CompileType value2);
     */
    public static Ordered compare(Ctx ctx, nType type, Orderable value1, Orderable value2) {
        float l1 = $toFloat(((Float8e4) value1).$value);
        float l2 = $toFloat(((Float8e4) value2).$value);
        return l1 < l2    ? Ordered.Lesser.$INSTANCE
                : l1 == l2 ? Ordered.Equal.$INSTANCE
                : Ordered.Greater.$INSTANCE;
    }

    /**
     * The primitive implementation of:
     *
     *  static <CompileType extends Orderable> Boolean equals(CompileType value1, CompileType value2);
     */
    public static Boolean equals(Ctx ctx, nType type, Object value1, Object value2) {
        float l1 = $toFloat(((Float8e4) value1).$value);
        float l2 = $toFloat(((Float8e4) value2).$value);
        return l1 == l2 ? Boolean.TRUE : Boolean.FALSE;
    }

    // ----- conversion ----------------------------------------------------------------------------

    public static float toFloat32$p(int thi$, Ctx ctx) {
        return $toFloat(thi$);
    }

    public static double toFloat64$p(int thi$, Ctx ctx) {
        return $toFloat(thi$);
    }
}

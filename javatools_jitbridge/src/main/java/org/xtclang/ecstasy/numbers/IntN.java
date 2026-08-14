package org.xtclang.ecstasy.numbers;

import java.math.BigInteger;

import java.nio.ByteBuffer;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.Boolean;
import org.xtclang.ecstasy.Ordered;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native shell for "ecstasy.numbers.IntN".
 */
public class IntN extends IntNumber {

    /**
     * Static constant Zero IntN value.
     */
    private static final IntN ZERO = new IntN(BigInteger.ZERO);

    /**
     * Static constant One IntN value.
     */
    private static final IntN ONE = new IntN(BigInteger.ONE);

    /**
     * The value of this IntN.
     */
    private final BigInteger $value;

    /**
     * Construct an {@link IntN} wrapping a {@link BigInteger}.
     *
     * @param value  the {@link BigInteger} to wrap
     */
    private IntN(BigInteger value) {
        this.$value = value;
    }

    /**
     * Create a new {@link IntN} from an Ecstasy String value.
     */
    public static IntN $new(Ctx ctx, String value) {
        return new IntN(new BigInteger(value.toString()));
    }

    /**
     * Create a new {@link IntN} from a Java String value.
     */
    public static IntN $new(java.lang.String value) {
        return new IntN(new BigInteger(value));
    }

    /**
     * Box a {@link BigInteger} into an {@link IntN}.
     */
    public static IntN $box(BigInteger value) {
        return new IntN(value);
    }

    /**
     * Box a Java {@code long} into an {@link IntN}.
     */
    public static IntN $box(long value) {
        return new IntN(BigInteger.valueOf(value));
    }

    /**
     * Box a 128-bit number represented by two Java {@code long} values into an {@link IntN}.
     */
    public static IntN $box(long low, long high) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(high);
        buffer.putLong(low);
        return new IntN(new BigInteger(buffer.array()));
    }

    @Override
    protected long[] $longValues() {
        byte[] ab = $value.toByteArray();
        int    cb = ab.length;
        int    cl = (cb + 7) / 8;
        long[] al = new long[cl];
        for (int i = 0; i < cb; ++i) {
            al[i / 8] |= (long) (ab[i] & 0xFF) << (56 - (i % 8) * 8);
        }
        return al;
    }

    // ----- Numeric -------------------------------------------------------------------------------

    /**
     * The native implementation of;
     * <pre>
     *     static Numeric zero();
     * </pre>
     */
    public static IntN zero(Ctx ctx) {
        return ZERO;
    }

    /**
     * The native implementation of;
     * <pre>
     *     static Numeric one();
     * </pre>
     */
    public static IntN one(Ctx ctx) {
        return ONE;
    }

    /**
     * The native implementation of:
     * <pre>
     *     static conditional Range<Numeric> range();
     * </pre>
     */
    public static Boolean range(Ctx ctx) {
        return Boolean.False;
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     static conditional Range<Numeric> range();
     * </pre>
     */
    public static boolean range$p(Ctx ctx) {
        return false;
    }

    // ----- Number --------------------------------------------------------------------------------

    @Override
    protected long bitLength$get$p() {
        return $value.bitLength();
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     Int bitLength.get()
     * </pre>
     */
    public long bitLength$get$p(Ctx ctx) {
        return $value.bitLength();
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntNumber bitCount.get()
     * </pre>
     */
    public UIntN magnitude$get$p(Ctx ctx) {
        return abs(ctx).toUIntN(ctx);
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     Int byteLength.get()
     * </pre>
     */
    protected long byteLength$get$p(Ctx ctx) {
        return ($value.bitLength() + 7) / 8;
    }

    /**
     * The native implementation of:
     * <pre>
     *    Number neg();
     * </pre>
     */
    public IntN neg(Ctx ctx) {
        return new IntN($value.negate());
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntN add(IntN n);
     * </pre>
     */
    public IntN add(Ctx ctx, IntN n) {
        return new IntN($value.add(n.$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntN sub(IntN n);
     * </pre>
     */
    public IntN sub(Ctx ctx, IntN n) {
        return new IntN($value.subtract(n.$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntN mul(IntN n);
     * </pre>
     */
    public IntN mul(Ctx ctx, IntN n) {
        return new IntN($value.multiply(n.$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntN div(IntN n);
     * </pre>
     */
    public IntN div(Ctx ctx, IntN n) {
        return new IntN($value.divide(n.$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntN mod(IntN n);
     * </pre>
     */
    public IntN mod(Ctx ctx, IntN n) {
        return new IntN($value.mod(n.$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntN divrem(IntN n);
     * </pre>
     */
    public IntN divrem(Ctx ctx, IntN n) {
        BigInteger[] results = $value.divideAndRemainder(n.$value);
        ctx.o0 = new IntN(results[1]);
        return new IntN(results[0]);
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntN remainder(IntN n);
     * </pre>
     */
    public IntN remainder(Ctx ctx, IntN n) {
        return new IntN($value.remainder(n.$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntN abs();
     * </pre>
     */
    public IntN abs(Ctx ctx) {
        if ($value.signum() >= 0) {
            return this;
        }
        return new IntN($value.abs());
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntN pow(IntN n);
     * </pre>
     */
    public IntN pow(Ctx ctx, IntN n) {
        if (n.$value.bitLength() > 32) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "");
        }
        try {
            return new IntN($value.pow(n.$value.intValue()));
        } catch (ArithmeticException e) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, e.getMessage());
        }
    }

    // ----- IntNumber -----------------------------------------------------------------------------

    /**
     * The native implementation of:
     * <pre>
     *     UIntN magnitude.get()
     * </pre>
     */
    public UIntN magnitude$get(Ctx ctx) {
        return UIntN.$box($value.abs());
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntNumber leftmostBit.get()
     * </pre>
     */
    public IntN leftmostBit$get(Ctx ctx) {
        if ($value.bitCount() == 0) {
            return this;
        }
        int len = $value.bitLength() - 1;
        for (int i = len; i >= 0; i--) {
            if ($value.testBit(1)) {
                return new IntN(BigInteger.ZERO.setBit(i));
            }
        }
        return this;
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntNumber rightmostBit.get()
     * </pre>
     */
    public IntN rightmostBit$get(Ctx ctx) {
        int lowest = $value.getLowestSetBit();
        if (lowest < 0) {
            return this;
        }
        return new IntN(BigInteger.ZERO.setBit(lowest));
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntNumber leadingZeroCount.get()
     * </pre>
     */
    public int leadingZeroCount$get$p(Ctx ctx) {
        byte[] bytes = $value.toByteArray();
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != 0) {
                return i * 8 - Integer.numberOfLeadingZeros(bytes[i]);
            }
        }
        return bytes.length * 8;
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntNumber trailingZeroCount.get()
     * </pre>
     */
    public int trailingZeroCount$get$p(Ctx ctx) {
        byte[] bytes = $value.toByteArray();
        for (int i = bytes.length - 1; i >= 0; i--) {
            if (bytes[i] != 0) {
                return (bytes.length - i - 1) * 8 - Integer.numberOfTrailingZeros(bytes[i]);
            }
        }
        return bytes.length * 8;
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntNumber bitCount.get()
     * </pre>
     */
    public int bitCount$get$p(Ctx ctx) {
        return $value.bitCount();
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntNumber and(IntN n);
     * </pre>
     */
    public IntNumber and(Ctx ctx, IntNumber that) {
        return new IntN($value.and(((IntN) that).$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntNumber or(IntN n);
     * </pre>
     */
    public IntNumber or(Ctx ctx, IntNumber that) {
        return new IntN($value.or(((IntN) that).$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntNumber xor(IntN n);
     * </pre>
     */
    public IntNumber xor(Ctx ctx, IntNumber that) {
        return new IntN($value.xor(((IntN) that).$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntNumber not();
     * </pre>
     */
    public IntNumber not(Ctx ctx) {
        return new IntN($value.not());
    }

    /**
     * The primitive implementation of:
     * <pre>
     *    IntNumber shiftLeft(Int n);
     * </pre>
     */
    public IntNumber shiftLeft$p(Ctx ctx, long n) {
        if (n == 0) {
            return this;
        }
        return new IntN($value.shiftLeft((int) n));
    }

    /**
     * The primitive implementation of:
     * <pre>
     *    IntNumber shiftRight(Int n);
     * </pre>
     */
    public IntNumber shiftRight$p(Ctx ctx, long n) {
        if (n == 0) {
            return this;
        }
        return new IntN($value.shiftRight((int) n));
    }

    /**
     * The primitive implementation of:
     * <pre>
     *    IntNumber shiftAllRight(Int n);
     * </pre>
     */
    public IntNumber shiftAllRight$p(Ctx ctx, long n) {
        if (n == 0) {
            return this;
        }
        return switch ($value.signum()) {
            case 0 -> this;
            case 1 -> new IntN($value.shiftRight((int) n));
            default -> new IntN(new BigInteger(1, $value.toByteArray()).shiftRight((int) n));
        };
    }

    /**
     * The primitive implementation of:
     * <pre>
     *    IntNumber rotateLeft(Int n);
     * </pre>
     */
    public IntNumber rotateLeft$p(Ctx ctx, long n) {
        // Handle distances larger than bitLength
        int bitLength = $value.bitLength();
        if (bitLength == 0) {
            return this;
        }
        int distance  = (int) (n % bitLength);
        if (distance == 0) {
            return this;
        }
        // Shift left and shift right to capture overflow
        BigInteger leftShift = $value.shiftLeft(distance);
        BigInteger rightShift = $value.shiftRight(bitLength - distance);

        // Combine and mask to the specified bitLength
        BigInteger mask   = BigInteger.ONE.shiftLeft(bitLength).subtract(BigInteger.ONE);
        BigInteger result = leftShift.or(rightShift).and(mask);

        return new IntN(result);
    }

    /**
     * The primitive implementation of:
     * <pre>
     *    IntNumber rotateRight(Int n);
     * </pre>
     */
    public IntNumber rotateRight$p(Ctx ctx, long n) {
        // Handle distances larger than bitLength
        int bitLength = $value.bitLength();
        if (bitLength == 0) {
            return this;
        }
        int distance  = (int) (n % bitLength);
        if (distance == 0) {
            return this;
        }
        // Ensure the value fits the width before right shifting
        BigInteger mask = BigInteger.ONE.shiftLeft(bitLength).subtract(BigInteger.ONE);
        BigInteger cleanVal = $value.and(mask);

        BigInteger rightShift = cleanVal.shiftRight(distance);
        BigInteger leftShift = cleanVal.shiftLeft(bitLength - distance);

        // Combine and mask
        BigInteger result = rightShift.or(leftShift).and(mask);

        return new IntN(result);
    }

    /**
     * The primitive implementation of:
     * <pre>
     *    IntNumber retainLSBits(Int count)
     * </pre>
     */
    public IntNumber retainLSBits$p(Ctx ctx, long count) {
        // Handle edge cases for zero or negative boundaries
        if ($value.signum() == 0 || count <= 0) {
            return ZERO;
        }

        long bits = $value.bitLength();
        if (count >= bits) {
            return this;
        }
        BigInteger mask   = BigInteger.ONE.shiftLeft((int) count).subtract(BigInteger.ONE);
        BigInteger result = $value.and(mask);
        return new IntN(result);
    }

    /**
     * The primitive implementation of:
     * <pre>
     *    IntNumber retainMSBits(Int count)
     * </pre>
     */
    public IntNumber retainMSBits$p(Ctx ctx, long count) {
        // Handle edge cases for zero or negative boundaries
        if ($value.signum() == 0 || count <= 0) {
            return ZERO;
        }

        int totalBits = $value.bitLength();
        // If the number has fewer bits than requested, return the number itself
        if (totalBits <= count) {
            return this;
        }
        // Shift right to discard the unwanted lower bits, then shift back
        int shift = totalBits - (int) count;
        BigInteger result = $value.shiftRight(shift).shiftLeft(shift);
        return new IntN(result);
    }

    // ----- Sequential interface ------------------------------------------------------------------

    /**
     * The native implementation of:
     * <pre>
     *     (Boolean, IntN) next()
     * </pre>
     */
    public boolean next$p(Ctx ctx) {
        ctx.o0 = new IntN($value.add(BigInteger.ONE));
        return true;
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntN nextValue()
     * </pre>
     */
    public IntN nextValue(Ctx ctx) {
        return new IntN($value.add(BigInteger.ONE));
    }

    /**
     * The native implementation of:
     * <pre>
     *     (Boolean, IntN) prev()
     * </pre>
     */
    public boolean prev$p(Ctx ctx) {
        ctx.o0 = new IntN($value.subtract(BigInteger.ONE));
        return true;
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntN prevValue()
     * </pre>
     */
    public IntN prevValue(Ctx ctx) {
        return new IntN($value.subtract(BigInteger.ONE));
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     Int stepsTo(IntNumber that)
     * </pre>
     */
    public long stepsTo$p(Ctx ctx, IntNumber that) {
        return ((IntN) that).$value.subtract(this.$value).longValue();
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     IntNumber skip(Int steps)
     * </pre>
     */
    public IntNumber skip$p(Ctx ctx, long steps) {
        return steps == 0 ? this : new IntN($value.add(BigInteger.valueOf(steps)));
    }

    // ----- conversion ----------------------------------------------------------------------------

    /**
     * Implementation of Int8 toInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as a Java {@code int}
     */
    public int toInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() >= Byte.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "IntN value " + $value + " is not a valid Int8 value");
        }
        return $value.byteValue();
    }

    /**
     * The primitive implementation of Int16 toInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as a Java {@code int}
     */
    public int toInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() >= Short.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "IntN value " + $value + " is not a valid Int16 value");
        }
        return $value.shortValue();
    }

    /**
     * The primitive implementation of Int32 toInt32(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as a Java {@code int}
     */
    public int toInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() >= Integer.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "IntN value " + $value + " is not a valid Int32 value");
        }
        return $value.intValue();
    }

    /**
     * Implementation of Int toInt(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as a Java long
     */
    public long toInt$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return toInt64$p(ctx, checkBounds, dfltCheckBounds);
    }

    /**
     * Implementation of Int64 toInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as a Java long
     */
    public long toInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() >= Long.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "IntN value " + $value + " is not a valid Int64 value");
        }
        return $value.longValue();
    }

    /**
     * The primitive implementation of Int128 toInt128(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as an Int128
     */
    public long toInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() >= 128) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "IntN value " + $value + " is not a valid Int128 value");
        }
        long[] longs = $longValues();
        ctx.i0 = longs.length > 1 ? longs[1] : 0;
        return longs.length > 0 ? longs[0] : 0;
    }

    /**
     * The primitive implementation of Nibble toNibble(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as a Java {@code int}
     */
    public int toNibble$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() > 4) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "IntN value " + $value + " is not a valid Nibble value");
        }
        return $value.byteValue() & 0x0F;
    }

    /**
     * The primitive implementation of UInt8 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as a Java {@code int}
     */
    public int toUInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() > Byte.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "IntN value " + $value + " is not a valid UInt8 value");
        }
        return $value.byteValue() & 0xFF;
    }

    /**
     * The primitive implementation of UInt16 toUInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as a Java {@code int}
     */
    public int toUInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() > Short.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "IntN value " + $value + " is not a valid UInt16 value");
        }
        return $value.shortValue() & 0xFFFF;
    }

    /**
     * The primitive implementation of UInt32 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as a Java {@code int}
     */
    public int toUInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() > Integer.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "IntN value " + $value + " is not a valid UInt32 value");
        }
        return $value.intValue();
    }

    /**
     * Implementation of UInt toUInt(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as a Java long
     */
    public long toUInt$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return toUInt64$p(ctx, checkBounds, dfltCheckBounds);
    }

    /**
     * The primitive implementation of UInt64 toUInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as a Java long
     */
    public long toUInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() > Long.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "IntN value " + $value + " is not a valid UInt64 value");
        }
        return $value.longValue();
    }

    /**
     * The primitive implementation of UInt128 toUInt128(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this IntN value as a UInt128
     */
    public long toUInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() > 128) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "IntN value " + $value + " is not a valid UInt128 value");
        }
        long[] longs = $longValues();
        ctx.i0 = longs.length > 1 ? longs[1] : 0;
        return longs.length > 0 ? longs[0] : 0;
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntN toIntN()
     * </pre>
     */
    public IntN toIntN(Ctx ctx) {
        return this;
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntN toIntN()
     * </pre>
     */
    public UIntN toUIntN(Ctx ctx) {
        if ($value.signum() < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "IntN value " + $value + " is not a valid UIntN value");
        }
        return UIntN.$box($value);
    }

    // ----- Orderable interface -------------------------------------------------------------------

    public static Ordered compare(Ctx ctx, nType type, IntN value1, IntN value2) {
        int r = value1.$value.compareTo(value2.$value);
        return r < 0 ? Ordered.Lesser.$INSTANCE
                : r == 0 ? Ordered.Equal.$INSTANCE
                  : Ordered.Greater.$INSTANCE;
    }

    public static boolean equals$p(Ctx ctx, nType type, IntN value1, IntN value2) {
        return value1.$value.equals(value2.$value);
    }

    // ----- Stringable interface ------------------------------------------------------------------

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, $value.toString());
    }

    public long estimateStringLength$p(Ctx ctx) {
        return $value.toString().length();
    }

    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : $value.toString().toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    @Override
    public java.lang.String toString() {
        return $value.toString();
    }
}

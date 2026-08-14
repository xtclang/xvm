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
 * Native shell for "ecstasy.numbers.UIntN".
 */
public class UIntN extends UIntNumber {

    /**
     * Static constant Zero UIntN value.
     */
    private static final UIntN ZERO = new UIntN(BigInteger.ZERO);

    /**
     * Static constant One UIntN value.
     */
    private static final UIntN ONE = new UIntN(BigInteger.ONE);

    /**
     * The value of this UIntN.
     */
    private final BigInteger $value;

    /**
     * Construct an {@link UIntN} wrapping a {@link BigInteger}.
     *
     * @param value  the {@link BigInteger} to wrap
     */
    private UIntN(BigInteger value) {
        this.$value = value;
    }

    /**
     * Create a new {@link UIntN} from an Ecstasy String value.
     */
    public static UIntN $new(Ctx ctx, String value) {
        return new UIntN(new BigInteger(value.toString()));
    }

    /**
     * Create a new {@link UIntN} from a Java String value.
     */
    public static UIntN $new(java.lang.String value) {
        return new UIntN(new BigInteger(value));
    }

    /**
     * Box a {@link BigInteger} into an {@link UIntN}.
     */
    public static UIntN $box(BigInteger value) {
        return new UIntN(value);
    }

    /**
     * Box a Java {@code long} into an {@link UIntN}.
     */
    public static UIntN $box(long value) {
        return new UIntN(BigInteger.valueOf(value));
    }

    /**
     * Box a 128-bit number represented by two Java {@code long} values into an {@link UIntN}.
     */
    public static UIntN $box(long low, long high) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(high);
        buffer.putLong(low);
        return new UIntN(new BigInteger(buffer.array()));
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
    public static UIntN zero(Ctx ctx) {
        return ZERO;
    }

    /**
     * The native implementation of;
     * <pre>
     *     static Numeric one();
     * </pre>
     */
    public static UIntN one(Ctx ctx) {
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
     *    UIntN add(UIntN n);
     * </pre>
     */
    public UIntN add(Ctx ctx, UIntN n) {
        return new UIntN($value.add(n.$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    UIntN sub(UIntN n);
     * </pre>
     */
    public UIntN sub(Ctx ctx, UIntN n) {
        if (n.$value.equals(BigInteger.ZERO)) {
            return this;
        }
        if ($value.compareTo(n.$value) < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Subtracting " + n.$value + " from UIntN value " + $value
                            + " would be negative");
        }
        return new UIntN($value.subtract(n.$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    UIntN mul(UIntN n);
     * </pre>
     */
    public UIntN mul(Ctx ctx, UIntN n) {
        return new UIntN($value.multiply(n.$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    UIntN div(UIntN n);
     * </pre>
     */
    public UIntN div(Ctx ctx, UIntN n) {
        return new UIntN($value.divide(n.$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    UIntN mod(UIntN n);
     * </pre>
     */
    public UIntN mod(Ctx ctx, UIntN n) {
        return new UIntN($value.mod(n.$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    UIntN divrem(UIntN n);
     * </pre>
     */
    public UIntN divrem(Ctx ctx, UIntN n) {
        BigInteger[] results = $value.divideAndRemainder(n.$value);
        ctx.o0 = new UIntN(results[1]);
        return new UIntN(results[0]);
    }

    /**
     * The native implementation of:
     * <pre>
     *    UIntN remainder(UIntN n);
     * </pre>
     */
    public UIntN remainder(Ctx ctx, UIntN n) {
        return new UIntN($value.remainder(n.$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    UIntN abs();
     * </pre>
     */
    public UIntN abs(Ctx ctx) {
        return this;
    }

    /**
     * The native implementation of:
     * <pre>
     *    UIntN pow(UIntN n);
     * </pre>
     */
    public UIntN pow(Ctx ctx, UIntN n) {
        if (n.$value.bitLength() > 32) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "");
        }
        try {
            return new UIntN($value.pow(n.$value.intValue()));
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
        return this;
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntNumber leftmostBit.get()
     * </pre>
     */
    public UIntN leftmostBit$get(Ctx ctx) {
        if ($value.bitCount() == 0) {
            return this;
        }
        int len = $value.bitLength() - 1;
        for (int i = len; i >= 0; i--) {
            if ($value.testBit(1)) {
                return new UIntN(BigInteger.ZERO.setBit(i));
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
    public UIntN rightmostBit$get(Ctx ctx) {
        int lowest = $value.getLowestSetBit();
        if (lowest < 0) {
            return this;
        }
        return new UIntN(BigInteger.ZERO.setBit(lowest));
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
     *     IntNumber bitCount.get()
     * </pre>
     */
    public UIntN magnitude$get$p(Ctx ctx) {
        return this;
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntNumber and(UIntN n);
     * </pre>
     */
    public IntNumber and(Ctx ctx, IntNumber that) {
        return new UIntN($value.and(((UIntN) that).$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntNumber or(UIntN n);
     * </pre>
     */
    public IntNumber or(Ctx ctx, IntNumber that) {
        return new UIntN($value.or(((UIntN) that).$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntNumber xor(UIntN n);
     * </pre>
     */
    public IntNumber xor(Ctx ctx, IntNumber that) {
        return new UIntN($value.xor(((UIntN) that).$value));
    }

    /**
     * The native implementation of:
     * <pre>
     *    IntNumber not();
     * </pre>
     */
    public IntNumber not(Ctx ctx) {
        if ($value.signum() == 0) {
            return ONE;
        }
        byte[] bytes = $value.toByteArray();
        int    bits  = $value.bitLength() % 8;
        int    start = 0;
        if (bits != 0) {
            int mask = (1 << bits) - 1;
            bytes[0] = (byte) (~bytes[0] & mask);
            start = 1;
        }

        for (int i = start; i < bytes.length; i++) {
            bytes[i] = (byte) ~bytes[i];
        }
        return new UIntN(new BigInteger(1, bytes));
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
        return new UIntN($value.shiftLeft((int) n));
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
        return new UIntN($value.shiftRight((int) n));
    }

    /**
     * The primitive implementation of:
     * <pre>
     *    UIntN shiftAllRight(Int n);
     * </pre>
     */
    public IntNumber shiftAllRight$p(Ctx ctx, long n) {
        if (n == 0 || $value.signum() == 0) {
            return this;
        }
        return new UIntN($value.shiftRight((int) n));
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

        return new UIntN(result);
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

        return new UIntN(result);
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
        return new UIntN(result);
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
        return new UIntN(result);
    }

    // ----- Sequential interface ------------------------------------------------------------------

    /**
     * The native implementation of:
     * <pre>
     *     (Boolean, IntN) next()
     * </pre>
     */
    public boolean next$p(Ctx ctx) {
        ctx.o0 = new UIntN($value.add(BigInteger.ONE));
        return true;
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntN nextValue()
     * </pre>
     */
    public UIntN nextValue(Ctx ctx) {
        return new UIntN($value.add(BigInteger.ONE));
    }

    /**
     * The native implementation of:
     * <pre>
     *     (Boolean, IntN) prev()
     * </pre>
     */
    public boolean prev$p(Ctx ctx) {
        if ($value.equals(BigInteger.ZERO)) {
            return false;
        }
        ctx.o0 = new UIntN($value.subtract(BigInteger.ONE));
        return true;
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntN prevValue()
     * </pre>
     */
    public UIntN prevValue(Ctx ctx) {
        if ($value.equals(BigInteger.ZERO)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Cannot decrement UIntN value zero");
        }
        return new UIntN($value.subtract(BigInteger.ONE));
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     Int stepsTo(IntNumber that)
     * </pre>
     */
    public long stepsTo$p(Ctx ctx, IntNumber that) {
        return ((UIntN) that).$value.subtract(this.$value).longValue();
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     IntNumber skip(Int steps)
     * </pre>
     */
    public IntNumber skip$p(Ctx ctx, long steps) {
        return steps == 0 ? this : new UIntN($value.add(BigInteger.valueOf(steps)));
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
     * @return this UIntN value as a Java {@code int}
     */
    public int toInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() >= Byte.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UIntN value " + $value + " is not a valid Int8 value");
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
     * @return this UIntN value as a Java {@code int}
     */
    public int toInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() >= Short.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UIntN value " + $value + " is not a valid Int16 value");
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
     * @return this UIntN value as a Java {@code int}
     */
    public int toInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() >= Integer.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UIntN value " + $value + " is not a valid Int32 value");
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
     * @return this UIntN value as a Java long
     */
    public long toInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() >= Long.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UIntN value " + $value + " is not a valid Int64 value");
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
     * @return this UIntN value as an Int128
     */
    public long toInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() >= 128) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UIntN value " + $value + " is not a valid Int128 value");
        }
        long[] longs = $longValues();
        ctx.i0 = longs.length > 1 ? longs[1] : 0;
        return longs.length > 0 ? longs[0] : 0;
    }

    /**
     * The primitive implementation of UInt8 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UIntN value as a Java {@code int}
     */
    public int toUInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() > Byte.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UIntN value " + $value + " is not a valid UInt8 value");
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
     * @return this UIntN value as a Java {@code int}
     */
    public int toUInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() > Short.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UIntN value " + $value + " is not a valid UInt16 value");
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
     * @return this UIntN value as a Java {@code int}
     */
    public int toUInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() > Integer.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UIntN value " + $value + " is not a valid UInt32 value");
        }
        return $value.intValue();
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
            throw oob.$init(ctx, "UIntN value " + $value + " is not a valid Nibble value");
        }
        return $value.byteValue() & 0x0F;
    }

    /**
     * Implementation of UInt toUInt(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UIntN value as a Java long
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
     * @return this UIntN value as a Java long
     */
    public long toUInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() > Long.SIZE) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UIntN value " + $value + " is not a valid UInt64 value");
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
     * @return this UIntN value as a UInt128
     */
    public long toUInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value.bitLength() > 128) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UIntN value " + $value + " is not a valid UInt128 value");
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
        return IntN.$box($value);
    }

    /**
     * The native implementation of:
     * <pre>
     *     UIntN toUIntN()
     * </pre>
     */
    public UIntN toUIntN(Ctx ctx) {
        return this;
    }

    // ----- Orderable interface -------------------------------------------------------------------

    public static Ordered compare(Ctx ctx, nType type, UIntN value1, UIntN value2) {
        int r = value1.$value.compareTo(value2.$value);
        return r < 0  ? Ordered.Lesser.$INSTANCE :
               r == 0 ? Ordered.Equal.$INSTANCE :
                        Ordered.Greater.$INSTANCE;
    }

    public static boolean equals$p(Ctx ctx, nType type, UIntN value1, UIntN value2) {
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

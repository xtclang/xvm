/**
 * `Int` is an automatically-sized integer, and supports the [Int128] range of values.
 *
 * When integer bitwise operations are not required, use the `Int` type by default for all integer
 * properties, variables, parameters, and return values whose runtime range is _unknown_, and thus
 * whose ideal storage size is also unknown. Conversely, if bitwise operations (as defined by the
 * [Bitwise] mixin) are required, or if the exact range of a value is known, then use the
 * appropriate sized integer type ([Int8], [Int16], [Int32], [Int64], or [Int128]) or the variably-
 * sized integer type ([IntN]) instead.
 *
 * "Automatically-sized" does not mean "variably sized" at runtime; a variably sized value means
 * that the class for that value decides on an instance-by-instance basis what to use for the
 * storage of the value that the instance represents. "Automatically-sized" means that the _runtime_
 * is responsible for handling all values in the largest possible range, but is permitted to select
 * a far smaller space for the storage of the integer value than the largest possible range would
 * indicate, so long as the runtime can adjust that storage to handle larger values when necessary.
 * An expected implementation for properties, for example, would utilize runtime statistics to
 * determine the actual ranges of values encountered in classes with large numbers of
 * instantiations, and would then reshape the storage layout of those classes, reducing the memory
 * footprint of each instance of those classes; this operation would likely be performed during a
 * service-level garbage collection, or when a service is idle. The reverse is not true, though:
 * When a value is encountered that is larger than the storage size that was previously assumed to
 * be sufficient, then the runtime must immediately provide for the storage of that value in some
 * manner, so that information is not lost. As with all statistics-driven optimizations that require
 * real-time de-optimization to handle unexpected and otherwise-unsupported conditions, there will
 * be significant and unavoidable one-time costs, every time such a scenario is encountered.
 */
const Int
        extends IntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int.
     */
    static IntLiteral MinValue = Int128.MinValue;

    /**
     * The maximum value for an Int.
     */
    static IntLiteral MaxValue = Int128.MaxValue;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength()
        {
        return False;
        }

    @Override
    static Int zero()
        {
        return 0;
        }

    @Override
    static Int one()
        {
        return 1;
        }

    @Override
    static conditional Range<Int> range()
        {
        return True, MinValue..MaxValue;
        }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 64-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits)
        {
        assert bits.size <= 128;
        super(bits);
        }

    /**
     * Construct a 64-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes)
        {
        assert bytes.size <= 16;
        super(bytes);
        }

    /**
     * Construct a 64-bit signed integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text)
        {
        construct Int(new IntLiteral(text).toInt().bits);
        }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        return switch (this <=> 0)
            {
            case Lesser : Negative;
            case Equal  : Zero;
            case Greater: Positive;
            };
        }

    @Override
    UInt magnitude.get()
        {
        return abs().toUInt();
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Int neg()
        {
        TODO
        }

    @Override
    @Op("+")
    Int add(Int! n)
        {
        TODO
        }

    @Override
    @Op("-")
    Int sub(Int! n)
        {
        TODO
        }

    @Override
    @Op("*")
    Int mul(Int! n)
        {
        TODO
        }

    @Override
    @Op("/")
    Int div(Int! n)
        {
        TODO
        }

    @Override
    @Op("%")
    Int mod(Int! n)
        {
        TODO
        }

    @Override
    Int abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    Int pow(Int! n)
        {
        Int result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Bitwise operations (but only a subset) ------------------------------------------------

    /**
     * If any bits are set in this integer, then return an integer with only the most significant
     * (left-most) of those bits set, otherwise return zero.
     */
    @RO Int leftmostBit.get()
        {
        // this will exhibit potentially unexpected behavior for negative values, since -1 can be
        // stored in a small number of bits but 0x800000000000000000000000000000000 cannot be
        return this == 0
                ? this
                : toInt128().leftmostBit.toInt();
        }

    /**
     * If any bits are set in this integer, then return an integer with only the least significant
     * (right-most) of those bits set, otherwise return zero.
     */
    @RO Int rightmostBit.get()
        {
        return this == 0
                ? this
                : toInt128().rightmostBit.toInt();
        }

    /**
     * The number of bits that are zero following the least significant (right-most) `1` bit.
     * This scans from right-to-left (least significant to most significant).
     *
     * For an integer with `bitCount==1`, this provides the log2 value of the integer.
     */
    Int trailingZeroCount.get()
        {
        return toInt128().trailingZeroCount;
        }

    /**
     * The number of bits that are set (non-zero) in the integer. This is also referred to as a
     * _population count_, or `POPCNT`.
     */
    Int bitCount.get()
        {
        // warning: for negative values, this will almost always return a value larger than the
        // number of bits used to the Int value itself, as the result of sign extension
        return toInt128().bitCount;
        }

    /**
     * Bitwise AND.
     */
    @Op("&") Int and(Int that)
        {
        return toInt128().and(that.toInt128()).toInt();
        }

    /**
     * Bitwise OR.
     */
    @Op("|") Int or(Int that)
        {
        return toInt128().or(that.toInt128()).toInt();
        }

    /**
     * Bitwise XOR.
     */
    @Op("^") Int xor(Int that)
        {
        return toInt128().xor(that.toInt128()).toInt();
        }

    /**
     * Bitwise NOT.
     */
    @Op("~") Int not()
        {
        return toInt128().not().toInt();
        }

    /**
     * Shift bits left. This is both a logical left shift and arithmetic left shift, for
     * both signed and unsigned integer values.
     */
    @Op("<<") Int shiftLeft(Int! count)
        {
        return toInt128().shiftLeft(count).toInt();
        }

    /**
     * Shift bits right. For signed integer values, this is an arithmetic right shift. For
     * unsigned integer values, this is both a logical right shift and arithmetic right
     * shift.
     */
    @Op(">>") Int shiftRight(Int! count)
        {
        return toInt128().shiftRight(count).toInt();
        }

    /**
     * Keep the specified number of least-significant (right-most) bit values unchanged, zeroing any
     * remaining bits. Note that for negative values, if any bits are zeroed, this will change the
     * sign of the resulting value.
     */
    Int retainLSBits(Int! count)
        {
        return toInt128().retainLSBits(count).toInt();
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Int next()
        {
        if (this < MaxValue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional Int prev()
        {
        if (this > MinValue)
            {
            return True, this - 1;
            }

        return False;
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Int toInt(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return this;
        }

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= Int8.MinValue && this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
        }

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= Int16.MinValue && this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
        }

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= Int32.MinValue && this <= Int32.MaxValue;
        return new Int32(bits[bitLength-32 ..< bitLength]);
        }

    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= Int64.MinValue && this <= Int64.MaxValue;
        return new Int64(bits[bitLength-64 ..< bitLength]);
        }

    @Auto
    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new Int128(new Bit[128](i -> bits[i < 128-bitLength ? 0 : i]));
        }

    @Auto
    @Override
    IntN toIntN(Rounding direction = TowardZero)
        {
        return new IntN(bits);
        }

    @Override
    UInt toUInt(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return magnitude;
        }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= UInt8.MinValue && this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
        }

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= UInt16.MinValue && this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
        }

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= UInt32.MinValue && this <= UInt32.MaxValue;
        return new UInt32(bits[bitLength-32 ..< bitLength]);
        }

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= UInt64.MinValue && this <= UInt64.MaxValue;
        return new UInt64(bits[bitLength-64 ..< bitLength]);
        }

    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= 0;
        return new UInt128(new Bit[128](i -> (i < 128-bitLength ? 0 : bits[i])));
        }

    @Override
    UIntN toUIntN(Rounding direction = TowardZero)
        {
        assert:bounds this >= 0;
        return new UIntN(bits);
        }

    @Auto
    @Override
    BFloat16 toBFloat16();

    @Auto
    @Override
    Float16 toFloat16();

    @Auto
    @Override
    Float32 toFloat32();

    @Auto
    @Override
    Float64 toFloat64();

    @Auto
    @Override
    Float128 toFloat128();

    @Auto
    @Override
    FloatN toFloatN()
        {
        return toIntLiteral().toFloatN();
        }

    @Auto
    @Override
    Dec32 toDec32();

    @Auto
    @Override
    Dec64 toDec64();

    @Auto
    @Override
    Dec128 toDec128();

    @Auto
    @Override
    DecN toDecN()
        {
        return toIntLiteral().toDecN();
        }


    // ----- Hashable functions --------------------------------------------------------------------

    @Override
    static <CompileType extends Int> Int64 hashCode(CompileType value)
        {
        return value.toInt64();
        }

    @Override
    static <CompileType extends Int> Boolean equals(CompileType value1, CompileType value2)
        {
        return value1.bits == value2.bits;
        }
    }
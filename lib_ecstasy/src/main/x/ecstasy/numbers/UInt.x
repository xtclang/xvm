/**
 * `UInt` is an automatically-sized unsigned integer, and supports the non-negative portion of the
 * [Int128] range of values. `UInt` does **not** support the entire [UInt128] range of values, as
 * one would naturally expect; this is a purposeful choice that allows `UInt` to provide an
 * automatic conversion to `Int`, thus eliminating one significant potential downside of selecting
 * `UInt` as a variable/property/parameter/return type, when so many APIs consume the `Int` type.
 *
 * When integer bitwise operations are not required, use the `UInt` type by default for all unsigned
 * integer properties, variables, parameters, and return values whose runtime range is _unknown_,
 * and thus whose ideal storage size is also unknown. Conversely, if bitwise operations (as defined
 * by the [Bitwise] mixin) are required, or if the exact range of a value is known, then use the
 * appropriate sized unsigned integer type ([UInt8] aka `Byte`, [UInt16], [UInt32], [UInt64],
 * or [UInt128]) or the variably-sized unsigned integer type ([UIntN]) instead.
 *
 * "Automatically-sized" does not mean "variably sized" at runtime; a variably sized value means
 * that the class for that value decides on an instance-by-instance basis what to use for the
 * storage of the value that the instance represents. "Automatically-sized" means that the _runtime_
 * is responsible for handling all values in the largest possible range, but is permitted to select
 * a far smaller space for the storage of the unsigned integer value than the largest possible range
 * would indicate, so long as the runtime can adjust that storage to handle larger values when
 * necessary. An expected implementation for properties, for example, would utilize runtime
 * statistics to determine the actual ranges of values encountered in classes with large numbers of
 * instantiations, and would then reshape the storage layout of those classes, reducing the memory
 * footprint of each instance of those classes; this operation would likely be performed during a
 * service-level garbage collection, or when a service is idle. The reverse is not true, though:
 * When a value is encountered that is larger than the storage size that was previously assumed to
 * be sufficient, then the runtime must immediately provide for the storage of that value in some
 * manner, so that information is not lost. As with all statistics-driven optimizations that require
 * real-time de-optimization to handle unexpected and otherwise-unsupported conditions, there will
 * be significant and unavoidable one-time costs, every time such a scenario is encountered.
 */
const UInt
        extends UIntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an UInt.
     */
    static IntLiteral MinValue = 0;

    /**
     * The maximum value for an UInt (which is the same as the maximum value for Int).
     */
    static IntLiteral MaxValue = Int.MaxValue;

    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength()
        {
        return False;
        }

    @Override
    static UInt zero()
        {
        return 0;
        }

    @Override
    static UInt one()
        {
        return 1;
        }

    @Override
    static conditional Range<UInt> range()
        {
        return True, MinValue..MaxValue;
        }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a UInt number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits)
        {
        assert:bounds bits.size < 128 || bits.size == 128 && bits[0] == bits[1];
        super(bits);
        }

    /**
     * Construct a UInt number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes)
        {
        assert:bounds bytes.size < 16 || bytes.size == 16 && bytes[0] & 0x80 >>> 6 == bytes[0] >>> 7;
        super(bytes);
        }

    /**
     * Construct a UInt number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text)
        {
        construct UInt(new IntLiteral(text).toUInt().bits);
        }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        return this == 0 ? Zero : Positive;
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    UInt add(UInt! n)
        {
        return this + n;
        }

    @Override
    @Op("-")
    UInt sub(UInt! n)
        {
        return this - n;
        }

    @Override
    @Op("*")
    UInt mul(UInt! n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    UInt div(UInt! n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    UInt mod(UInt! n)
        {
        return this % n;
        }

    @Override
    UInt pow(UInt! n)
        {
        UInt result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UInt next()
        {
        if (this < MaxValue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional UInt prev()
        {
        if (this > MinValue)
            {
            return True, this - 1;
            }

        return False;
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Auto
    @Override
    Int toInt(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new Int(bits);
        }

    @Auto
    @Override
    UInt toUInt(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return this;
        }

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
        }

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
        }

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int32.MaxValue;
        return new Int32(bits[bitLength-32 ..< bitLength]);
        }

    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int64.MaxValue;
        return new Int64(bits);
        }

    @Auto
    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new Int128(new Bit[128](i -> i < 128-bitLength ? 0 : bits[i]));
        }

    @Auto
    @Override
    IntN toIntN(Rounding direction = TowardZero)
        {
        return bits[0] == 0 ? new IntN(bits) : toUIntN().toIntN();
        }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
        }

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
        }

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= UInt32.MaxValue;
        return new UInt32(bits[bitLength-32 ..< bitLength]);
        }

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= UInt64.MaxValue;
        return new UInt64(bits[bitLength-64 ..< bitLength]);
        }

    @Auto
    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new UInt128(new Bit[128](i -> i < 128-bitLength ? 0 : bits[i]));
        }

    @Auto
    @Override
    UIntN toUIntN(Rounding direction = TowardZero)
        {
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


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return this <= UInt64.MaxValue
                ? toUInt64() .estimateStringLength()
                : toUInt128().estimateStringLength();
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        return this <= UInt64.MaxValue
                ? toUInt64() .appendTo(buf)
                : toUInt128().appendTo(buf);
        }
    }
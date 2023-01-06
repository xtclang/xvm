/**
 * `Dec` is an automatically-sized decimal, and supports the [Dec128] range of values.
 *
 * Use the `Dec` type by default for decimal values, whenever the optimal size for decimal values
 * are unknown, and as long as `Dec128` is sufficiently large to hold the expected range of values.
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
const Dec
        extends DecimalFPNumber
        default(0.0)
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a decimal floating point number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits)
        {
        switch (bits.size)
            {
            case 28, 32, 60, 64, 128:
                construct DecimalFPNumber(bits);
                break;

            default:
                throw new OutOfBounds($"Unsupported Dec bit size: {bits.size}");
            }
        }

    /**
     * Construct a decimal floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes)
        {
        switch (bytes.size)
            {
            case 4, 8, 16:
                construct DecimalFPNumber(bytes);
                break;

            default:
                throw new OutOfBounds($"Unsupported Dec bytes size: {bytes.size}");
            }
        }

    /**
     * Construct a decimal floating point number from its `String` representation.
     *
     * @param text  a floating point number, in text format
     */
    @Override
    construct(String text)
        {
        construct Dec(new FPLiteral(text).toDec().bits);
        }


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength()
        {
        return False;
        }

    @Override
    static Dec zero()
        {
        return 0.0;
        }

    @Override
    static Dec one()
        {
        return 1.0;
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op Dec add(Dec n)
        {
        TODO
        }

    @Override
    @Op Dec sub(Dec n)
        {
        TODO
        }

    @Override
    @Op Dec mul(Dec n)
        {
        TODO
        }

    @Override
    @Op Dec div(Dec n)
        {
        TODO
        }

    @Override
    @Op Dec mod(Dec n)
        {
        TODO
        }

    @Override
    Dec abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op Dec neg()
        {
        TODO
        }

    @Override
    Dec pow(Dec n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    @RO Int emax.get()
        {
        return 6144;
        }

    @Override
    Int emin.get()
        {
        return 1 - emax;
        }

    @Override
    Int bias.get()
        {
        return 6176;
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean negative, Int significand, Int exponent) split()
        {
        TODO
        }

    @Override
    Dec round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    Dec floor()
        {
        TODO
        }

    @Override
    Dec ceil()
        {
        TODO
        }

    @Override
    Dec exp()
        {
        TODO
        }

    @Override
    Dec scaleByPow(Int n)
        {
        TODO
        }

    @Override
    Dec log()
        {
        TODO
        }

    @Override
    Dec log2()
        {
        TODO
        }

    @Override
    Dec log10()
        {
        TODO
        }

    @Override
    Dec sqrt()
        {
        TODO
        }

    @Override
    Dec cbrt()
        {
        TODO
        }

    @Override
    Dec sin()
        {
        TODO
        }

    @Override
    Dec cos()
        {
        TODO
        }

    @Override
    Dec tan()
        {
        TODO
        }

    @Override
    Dec asin()
        {
        TODO
        }

    @Override
    Dec acos()
        {
        TODO
        }

    @Override
    Dec atan()
        {
        TODO
        }

    @Override
    Dec atan2(Dec y)
        {
        TODO
        }

    @Override
    Dec sinh()
        {
        TODO
        }

    @Override
    Dec cosh()
        {
        TODO
        }

    @Override
    Dec tanh()
        {
        TODO
        }

    @Override
    Dec asinh()
        {
        TODO
        }

    @Override
    Dec acosh()
        {
        TODO
        }

    @Override
    Dec atanh()
        {
        TODO
        }

    @Override
    Dec deg2rad()
        {
        TODO
        }

    @Override
    Dec rad2deg()
        {
        TODO
        }

    @Override
    Dec nextUp()
        {
        TODO
        }

    @Override
    Dec nextDown()
        {
        TODO
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    IntN toIntN(Rounding direction = TowardZero)
        {
        return round(direction).toIntN();
        }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UIntN toUIntN(Rounding direction = TowardZero)
        {
        return round(direction).toUIntN();
        }

    @Override
    Float8e4 toFloat8e4();

    @Override
    Float8e5 toFloat8e5();

    @Override
    BFloat16 toBFloat16();

    @Override
    Float16 toFloat16();

    @Override
    Float32 toFloat32();

    @Override
    Float64 toFloat64();

    @Auto
    @Override
    Float128 toFloat128();

    @Auto
    @Override
    FloatN toFloatN()
        {
        return toFPLiteral().toFloatN();
        }

    @Override
    Dec toDec()
        {
        return this;
        }

    @Override
    Dec32 toDec32()
        {
        return switch (bits.size)
            {
            case 28:     TODO
            case 32:     new Dec32(bits);
            case 60, 64: toDec64().toDec32();
            case 128:    toDec128().toDec32();
            default:     assert;
            };
        }

    @Override
    Dec64 toDec64()
        {
        return switch (bits.size)
            {
            case 28, 32: toDec32().toDec64();
            case 60:     TODO
            case 64:     new Dec64(bits);
            case 128:    toDec128().toDec64();
            default:     assert;
            };
        }

    @Auto
    @Override
    Dec128 toDec128()
        {
        return switch (bits.size)
            {
            case 28, 32: toDec32().toDec128();
            case 60, 64: toDec64().toDec128();
            case 128:    new Dec128(bits);
            default:     assert;
            };
        }

    @Auto
    @Override
    DecN toDecN()
        {
        return switch (bits.size)
            {
            case 28:          toDec32().toDecN();
            case 60:          toDec64().toDecN();
            case 32, 64, 128: new DecN(bits);
            default:          assert;
            };
        }


    // ----- Hashable functions --------------------------------------------------------------------

    @Override
    static <CompileType extends Dec> Int64 hashCode(CompileType value)
        {
        return value.toDec128().hashCode();
        }

    @Override
    static <CompileType extends Dec> Boolean equals(CompileType value1, CompileType value2)
        {
        if (value1.bits == value2.bits)
            {
            return True;
            }

        return switch (maxOf(value1.bits.size, value2.bits.size))
            {
            case 28, 32: value1.toDec32()  == value2.toDec32();
            case 60, 64: value1.toDec64()  == value2.toDec64();
            case 128:    value1.toDec128() == value2.toDec128();
            default: assert;
            };
        }
    }
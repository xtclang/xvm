/**
 * An IntLiteral is an IntNumber that is able to convert to any text string containing a legal representation of an
 * IntNumber into any of the built-in IntNumber implementations.
 * <p>
 * There are a number of formats for an IntLiteral:
 * <ul>
 * <li>a binary integer, e.g. 0b1010</li>
 * <li>an octal integer, e.g. 0o777</li>
 * <li>a decimal integer, e.g. 1234</li>
 * <li>a hexadecimal integer, e.g. 0xFE64</li>
 * </ul>
 * Furthermore, any of the above may be signed using the "+" or "-" sign.
 * <p>
 * While most text strings have an unambiguous translation to an integer, there are cases that are ambiguous without
 * knowing the exact integer type that the value will be converted to. For example, 0x80 could be an Int8 of -128, but
 * as any other integer type (e.g. Int16, UInt8, UInt16), 0x80 would be the value 128.
 */
const IntLiteral(String text)
    {
    /**
     * If the literal begins with an explicit "+" or "-" sign, this property indicates that sign.
     */
    Signum explicitSign = Zero;

    /**
     * This is the radix of the integer literal, one of 2, 8, 10, or 16.
     */
    Int radix = 10;

    VarInt value;

    construct IntLiteral(String text)
        {
        assert:always text.length > 0;

        // optional leading sign
        Int of = 0;
        switch (chars[of])
            {
            case '-':
                explicitSign = Negative;
                ++of;
                break;

            case '+':
                explicitSign = Positive;
                ++of;
                break;
            }

        // optional leading format
        Boolean underscoreOk = false;
        if (text.length - of >= 2 && chars[of] == '0')
            {
            switch (chars[of+1])
                {
                case 'X':
                case 'x':
                    radix = 16;
                    break;

                case 'B':
                case 'b':
                    radix = 2;
                    break;

                case 'o':
                    radix = 8;
                    break;
                }

            if (radix != 10)
                {
                of += 2;
                underscoreOk = true;
                }
            }

        // digits
        VarInt  value  = 0;
        Int     digits = 0;
        while (of < text.length)
            {
            Char ch = chars[of++];
            Int  nch;
            if (ch >= '0' && ch <= '9')
                {
                nch = ch.to<Int>() - '0'.to<Int>();
                }
            else if (ch >= 'A' && ch <= 'F')
                {
                nch = ch.to<Int>() - 'A'.to<Int>() + 10;
                }
            else if (ch >= 'a' && ch <= 'f')
                {
                nch = ch.to<Int>() - 'a'.to<Int>() + 10;
                }
            else
                {
                assert:always ch == '_' && underscoreOk;
                continue;
                }

            assert:always nch < radix;
            value = value * radix + nch.to<VarInt>();
            ++digits;
            underscoreOk = true;
            }

        this.value = value;
        }

    /**
     * The minimum number of bits to store the IntLiteral's value as a signed integer in a twos-complement format,
     * where the number of bits is a power-of-two of at least 8.
     */
    @ro Int minIntBits.get()
        {
        }

    /**
     * The minimum number of bits to store the IntLiteral's value as an unsigned integer,
     * where the number of bits is a power-of-two of at least 8.
     */
    @ro Int minUIntBits.get()
        {
        }

    /**
     * The minimum number of bits to store the IntLiteral's value in the IEEE754 binary floating point format,
     * where the number of bits is a power-of-two of at least 16.
     */
    @ro Int minFloatBits.get()
        {
        }

    /**
     * The minimum number of bits to store the IntLiteral's value in the IEEE754 decimal floating point format,
     * where the number of bits is a power-of-two of at least 8.
     */
    @ro Int minDecBits.get()
        {
        }

    /**
     * Convert the number to a variable-length signed integer.
     */
    @auto VarInt to<VarInt>();

    /**
     * Convert the number to a signed 8-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    @auto Int8 to<Int8>()
        {
        return to<VarInt>().to<Int8>();
        }

    /**
     * Convert the number to a signed 16-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    @auto Int16 to<Int16>();
        {
        return to<VarInt>().to<Int16>();
        }

    /**
     * Convert the number to a signed 32-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    @auto Int32 to<Int32>();
        {
        return to<VarInt>().to<Int32>();
        }

    /**
     * Convert the number to a signed 64-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    @auto Int64 to<Int64>();
        {
        return to<VarInt>().to<Int64>();
        }

    /**
     * Convert the number to a signed 128-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    @auto Int128 to<Int128>();
        {
        return to<VarInt>().to<Int128>();
        }

    /**
     * Convert the number to a variable-length unsigned integer.
     */
    @auto VarUInt to<VarUInt>();

    /**
     * Convert the number to a unsigned 8-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    @auto UInt8 to<UInt8>()
        {
        return to<VarUInt>().to<UInt8>();
        }

    /**
     * Convert the number to a unsigned 16-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    @auto UInt16 to<UInt16>();
        {
        return to<VarUInt>().to<UInt16>();
        }

    /**
     * Convert the number to a unsigned 32-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    @auto UInt32 to<UInt32>();
        {
        return to<VarUInt>().to<UInt32>();
        }

    /**
     * Convert the number to a unsigned 64-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    @auto UInt64 to<UInt64>();
        {
        return to<VarUInt>().to<UInt64>();
        }

    /**
     * Convert the number to a unsigned 128-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    @auto UInt128 to<UInt128>();
        {
        return to<VarInt>().to<UInt128>();
        }

    /**
     * Convert the number to a variable-length binary radix floating point number.
     */
    @auto VarFloat to<VarFloat>();

    /**
     * Convert the number to a 16-bit radix-2 (binary) floating point number.
     */
    @auto Float16 to<Float16>()
        {
        return to<VarFloat>().to<Float16>();
        }

    /**
     * Convert the number to a 32-bit radix-2 (binary) floating point number.
     */
    @auto Float32 to<Float32>()
        {
        return to<VarFloat>().to<Float32>();
        }

    /**
     * Convert the number to a 64-bit radix-2 (binary) floating point number.
     */
    @auto Float64 to<Float64>()
        {
        return to<VarFloat>().to<Float64>();
        }

    /**
     * Convert the number to a 128-bit radix-2 (binary) floating point number.
     */
    @auto Float128 to<Float128>()
        {
        return to<VarFloat>().to<Float128>();
        }

    /**
     * Convert the number to a variable-length decimal radix floating point number.
     */
    @auto VarDec to<VarDec>();

    /**
     * Convert the number to a 32-bit radix-10 (decimal) floating point number.
     */
    @auto Dec32 to<Dec32>()
        {
        return to<VarDec>().to<Dec32>();
        }

    /**
     * Convert the number to a 64-bit radix-10 (decimal) floating point number.
     */
    @auto Dec64 to<Dec64>()
        {
        return to<VarDec>().to<Dec64>();
        }

    /**
     * Convert the number to a 128-bit radix-10 (decimal) floating point number.
     */
    @auto Dec128 to<Dec128>()
        {
        return to<VarDec>().to<Dec128>();
        }
    }

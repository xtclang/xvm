/**
 * An IntNumber is a Number that represents an integer value.
 */
@Abstract const IntNumber
        extends Number
        implements Sequential
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits)
        {
        super(bits);
        }

    /**
     * Construct an integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes)
        {
        super(bytes);
        }


    // ----- IntNumber properties ------------------------------------------------------------------

    @Override
    @RO UIntNumber magnitude;


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    Int stepsTo(IntNumber that)
        {
        return (that - this).toInt64();
        }

    @Override
    IntNumber skip(Int steps)
        {
        return this + steps;
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert the integer number to a character.
     */
    Char toChar()
        {
        return new Char(toUInt32());
        }

    /**
     * Convert the least significant 4 bits of the integer value to a hexadecimal digit (hexit).
     *
     * @return the hexit for the least significant 4 bits of the integer value
     */
    Char toHexit()
        {
        return toNibble(True).toChar();
        }

    /**
     * Convert the IntNumber to a Nibble.
     *
     * @return the hexit for the least significant 4 bits of the integer value
     */
    Nibble toNibble(Boolean truncate = False)
        {
        Byte byte = toByte(truncate);
        assert truncate || byte <= 0xF;
        return Nibble.of(byte & 0xF);
        }

    /**
     * Obtain the number as an array of boolean values, each corresponding to one bit.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return the number as an array of booleans.
     */
    Boolean[] toBooleanArray()
        {
        return toBitArray().toBooleanArray();
        }

    /**
     * Obtain the integer number as an integer that checks for overflow and underflow conditions.
     */
    (IntNumber - Unchecked) toChecked();

    /**
     * Obtain the integer number as an integer that does not check for overflow or underflow.
     */
    @Unchecked IntNumber toUnchecked();

    @Override
    IntLiteral toIntLiteral(Rounding direction = TowardZero)
        {
        return new IntLiteral(toString());
        }

    @Override
    FPLiteral toFPLiteral()
        {
        return new FPLiteral(toString());
        }


    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return magnitude.estimateStringLength() + (sign == Negative ? 1 : 0);
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        if (sign == Negative)
            {
            buf.add('-');
            }
        return magnitude.appendTo(buf);
        }

    /**
     * Calculate the string size for the specified IntNumber and type specific size array.
     */
    protected static <IntType extends IntNumber> Int calculateStringSize(IntType n, IntType[] sizeArray)
        {
        for (Int index = 0; True; index++)
            {
            if (n <= sizeArray[index])
                {
                return index + 1;
                }
            }
        assert;
        }
    }
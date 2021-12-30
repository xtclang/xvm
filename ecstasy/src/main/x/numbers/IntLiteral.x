/**
 * An IntLiteral is a constant type that is able to convert any text string containing a
 * legal representation of an IntNumber into any of the built-in IntNumber implementations.
 *
 * There are a number of formats for an IntLiteral:
 * <ul>
 * <li>a binary integer, e.g. 0b1010</li>
 * <li>an octal integer, e.g. 0o777</li>
 * <li>a decimal integer, e.g. 1234</li>
 * <li>a hexadecimal integer, e.g. 0xFE64</li>
 * </ul>
 * Furthermore, any of the above may be signed using the "+" or "-" sign.
 *
 * The IntLiteral is complicated by its use both for signed and unsigned integer literals;
 * there are some edge cases in which the same literal can represent a different value,
 * depending on what actual type its value is represented by. While most text strings have
 * an unambiguous translation to an integer, there are cases that are ambiguous without
 * knowing the exact integer type that the value will be converted to. For example, 0x80
 * could be an Int8 of -128, but as any other integer type (e.g. Int16, UInt8, UInt16),
 * 0x80 would be the value 128. TODO ... and???
 */
const IntLiteral(String text)
        implements Sequential
    {
    /**
     * Construct an IntLiteral from a String.
     *
     * @param text  the literal value
     */
    construct(String text)
        {
        assert:arg text.size > 0;

        // optional leading sign
        Int of = 0;
        switch (text[of])
            {
            case '-':
                explicitSign = Signum.Negative;
                ++of;
                break;

            case '+':
                explicitSign = Signum.Positive;
                ++of;
                break;
            }

        // optional leading format
        Boolean underscoreOk = False;
        if (text.size - of >= 2 && text[of] == '0')
            {
            switch (text[of+1])
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
                underscoreOk = True;
                }
            }

        // digits
        UIntN magnitude = 0;
        Int   digits    = 0;
        NextChar: while (of < text.size)
            {
            Char   ch = text[of];
            UInt32 nch;
            switch (ch)
                {
                case '0'..'9':
                    nch = ch - '0';
                    break;

                case 'A'..'F':
                    nch = 10 + (ch - 'A');
                    break;

                case 'a'..'f':
                    nch = 10 + (ch - 'a');
                    break;

                case '_':
                    if (underscoreOk)
                        {
                        continue NextChar;
                        }
                    continue;

                default:
                    throw new IllegalArgument($|Illegal character {ch.toSourceString()} at \
                                               |offset {of} in integer literal {text.quoted()}
                                             );
                }

            if (nch >= radix)
                {
                throw new IllegalArgument($|Illegal digit {ch.toSourceString()} for radix \
                                           |{radix} in integer literal {text.quoted()}
                                         );
                }

            magnitude    = magnitude * radix + nch;
            underscoreOk = True;
            ++digits;
            ++of;
            }

        if (digits == 0)
            {
            throw new IllegalArgument($"Illegal integer literal {text.quoted()}");
            }

        this.magnitude = magnitude;
        this.text      = text;
        }

    /**
     * If the literal begins with an explicit "+" or "-" sign, this property indicates
     * that sign.
     */
    Signum explicitSign = Zero;

    /**
     * This is the radix of the integer literal, one of 2, 8, 10, or 16.
     */
    UInt8 radix = 10;

    /**
     * This is the value of the literal in IntN form.
     */
    UIntN magnitude;

    /**
     * The literal text.
     */
    private String text;

    /**
     * The minimum number of bits to store the IntLiteral's value as a signed integer in
     * a twos-complement format, where the number of bits is a power-of-two of at least 8.
     */
    Int minIntBits.get()
        {
        Int count;

        if (magnitude == 0)
            {
            // it doesn't take much to store a zero
            count = 1;
            }
        else if (explicitSign == Signum.Zero && radix != 10)
            {
            // combination of no explicit sign and a non-decimal radix implies that the
            // number of bits necessary is the number of bits to hold the magnitude
            // itself; examples:
            //   0x7F -> 8 bits
            //   0x80 -> 8 bits
            //   0xFF -> 8 bits
            count = magnitude.leftmostBit.trailingZeroCount + 1;
            }
        else if (explicitSign == Signum.Negative)
            {
            // examples:
            //   -0x7F -> 8 bits
            //   -0x80 -> 8 bits
            //   -0x81 -> 16 bits
            //   -0xFF -> 16 bits
            count = (magnitude - 1).leftmostBit.trailingZeroCount + 2;
            }
        else
            {
            // examples:
            //   127  +0x7F  -> 8 bits
            //   128  +0x80  -> 16 bits
            count = magnitude.leftmostBit.trailingZeroCount + 2;
            }

        // round up to nearest power of 2 (at least 8)
        return (count * 2 - 1).leftmostBit.maxOf(8);
        }

    /**
     * The minimum number of bits to store the IntLiteral's value as an unsigned integer,
     * where the number of bits is a power-of-two of at least 8.
     */
    @RO Int minUIntBits.get()
        {
// TODO review
        assert explicitSign != Signum.Negative;

        if (magnitude == 0)
            {
            // smallest int: 8 bits (1 byte)
            return 8;
            }

        return (magnitude.leftmostBit.trailingZeroCount * 2 + 1).leftmostBit.maxOf(8).toInt64();
        }

    /**
     * The minimum number of bits to store the IntLiteral's value in the IEEE754 binary floating
     * point format, where the number of bits is a power-of-two of at least 16.
     */
    @RO Int minFloatBits.get()
        {
        TODO
        }

    /**
     * The minimum number of bits to store the IntLiteral's value in the IEEE754 decimal floating
     * point format, where the number of bits is a power-of-two of at least 8.
     */
    @RO Int minDecBits.get()
        {
        TODO
        }


    // ----- IntNumber API -------------------------------------------------------------------------

    /**
     * Bitwise AND.
     */
    @Op("&")
    IntLiteral and(IntLiteral that)
        {
        return this & that;
        }

    /**
     * Bitwise OR.
     */
    @Op("|")
    IntLiteral or(IntLiteral that)
        {
        return this | that;
        }

    /**
     * Bitwise XOR.
     */
    @Op("^")
    IntLiteral xor(IntLiteral that)
        {
        return this ^ that;
        }

    /**
     * Bitwise NOT.
     */
    @Op("~")
    IntLiteral not()
        {
        return ~this;
        }

    /**
     * Shift bits left. Works like an arithmetic left shift.
     */
    @Op("<<")
    IntLiteral shiftLeft(Int count)
        {
        return this << count;
        }

    /**
     * Shift bits right. Works like an arithmetic right shift.
     */
    @Op(">>")
    IntLiteral shiftRight(Int count)
        {
        return this >> count;
        }

    /**
     * Works identically to the `shiftRight`.
     */
    @Op(">>>")
    IntLiteral shiftAllRight(Int count)
        {
        return this >>> count;
        }

    /**
     * Obtain a range beginning with this number and proceeding to the specified number.
     */
    @Op("..") Range<Int> to(Int n)
        {
        return new Range<Int>(this.toInt64(), n);
        }

    /**
     * Obtain a range beginning with this number and proceeding to the specified number.
     */
    @Op("..<") Range<Int> toExcluding(Int n)
        {
        return new Range<Int>(this.toInt64(), n, lastExclusive=True);
        }


    // ----- Number API ----------------------------------------------------------------------------

    /**
     * Addition: Add another number to this number, and return the result.
     */
    @Op("+")
    IntLiteral add(IntLiteral n)
        {
        return new IntLiteral((this.toIntN() + n.toIntN()).toString());
        }

    /**
     * Subtraction: Subtract another number from this number, and return the result.
     */
    @Op("-")
    IntLiteral sub(IntLiteral n)
        {
        return new IntLiteral((this.toIntN() - n.toIntN()).toString());
        }

    /**
     * Multiplication: Multiply this number by another number, and return the result.
     */
    @Op("*")
    IntLiteral mul(IntLiteral n)
        {
        return new IntLiteral((this.toIntN() * n.toIntN()).toString());
        }

    /**
     * Division: Divide this number by another number, and return the result.
     */
    @Op("/")
    IntLiteral div(IntLiteral n)
        {
        return new IntLiteral((this.toIntN() / n.toIntN()).toString());
        }

    /**
     * Modulo: Return the modulo that would result from dividing this number by another number.
     */
    @Op("%")
    IntLiteral mod(IntLiteral n)
        {
        return new IntLiteral((this.toIntN() % n.toIntN()).toString());
        }


    // ----- Sequential ----------------------------------------------------------------------------

    @Override
    conditional IntLiteral prev()
        {
        return True, this - 1;
        }

    @Override
    conditional IntLiteral next()
        {
        return True, this + 1;
        }

    @Override
    Int stepsTo(IntLiteral that)
        {
        return that - this;
        }

    @Override
    IntLiteral skip(Int steps)
        {
        return this + steps.toIntLiteral();
        }


    // ----- conversions ---------------------------------------------------------------------------

    // TODO doc all
    // TODO unchecked support

    @Auto Bit toBit()
        {
        if (magnitude == 0)
            {
            return 0;
            }

        assert magnitude == 1 && explicitSign != Signum.Negative;
        return 1;
        }

    /**
     * Convert the number to an unsigned 8-bit integer.
     *
     * A second name for the [toUInt8] method, to assist with readability. By using a property
     * to alias the method, instead of creating a second delegating method, this prevents the
     * potential for accidentally overriding the wrong method.
     */
    static Method<IntLiteral, <>, <Byte>> toByte = toUInt8;

    /**
     * Convert the number to a variable-length signed integer.
     *
     * @return the variable-length signed integer value
     */
    @Auto IntN toIntN()
        {
        TODO
        }

    /**
     * Convert the number to an unchecked, variable-length signed integer.
     *
     * @return the unchecked, variable-length signed integer value
     */
    @Auto @Unchecked IntN toUncheckedIntN()
        {
        return toIntN().toUnchecked();
        }

    /**
     * Convert the number to a signed 8-bit integer.
     *
     * @return the signed 8-bit integer value
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 8-bit integer range
     */
    @Auto Int8 toInt8()
        {
        return toIntN().toInt8();
        }

    /**
     * Convert the number to an unchecked, signed 8-bit integer.
     * Any additional magnitude is discarded.
     *
     * @return the signed 8-bit integer value
     */
    @Auto @Unchecked Int8 toUncheckedInt8()
        {
        return toIntN().toUnchecked().toInt8().toUnchecked();
        }

    /**
     * Convert the number to a signed 16-bit integer.
     *
     * @return the signed 16-bit integer value
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 16-bit integer range
     */
    @Auto Int16 toInt16()
        {
        return toIntN().toInt16();
        }

    /**
     * Convert the number to an unchecked, signed 16-bit integer.
     * Any additional magnitude is discarded.
     *
     * @return the signed 16-bit integer value
     */
    @Auto @Unchecked Int16 toUncheckedInt16()
        {
        return toIntN().toUnchecked().toInt16().toUnchecked();
        }

    /**
     * Convert the number to a signed 32-bit integer.
     *
     * @return the signed 32-bit integer value
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 32-bit integer range
     */
    @Auto Int32 toInt32()
        {
        return toIntN().toInt32();
        }

    /**
     * Convert the number to an unchecked, signed 32-bit integer.
     * Any additional magnitude is discarded.
     *
     * @return the signed 32-bit integer value
     */
    @Auto @Unchecked Int32 toUncheckedInt32()
        {
        return toIntN().toUnchecked().toInt32().toUnchecked();
        }

    /**
     * Convert the number to a signed 64-bit integer.
     *
     * @return the signed 64-bit integer value
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 64-bit integer range
     */
    @Auto Int64 toInt64()
        {
        return toIntN().toInt64();
        }

    /**
     * Convert the number to an unchecked, signed 64-bit integer.
     * Any additional magnitude is discarded.
     *
     * @return the signed 64-bit integer value
     */
    @Auto @Unchecked Int64 toUncheckedInt64()
        {
        return toIntN().toUnchecked().toInt64().toUnchecked();
        }

    /**
     * Convert the number to a signed 128-bit integer.
     *
     * @return the signed 128-bit integer value
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 128-bit integer range
     */
    @Auto Int128 toInt128()
        {
        return toIntN().toInt128();
        }

    /**
     * Convert the number to an unchecked, signed 128-bit integer.
     * Any additional magnitude is discarded.
     *
     * @return the signed 128-bit integer value
     */
    @Auto @Unchecked Int128 toUncheckedInt128()
        {
        return toIntN().toUnchecked().toInt128().toUnchecked();
        }

    /**
     * Convert the number to a variable-length unsigned integer.
     *
     * @return the variable-length unsigned integer value
     */
    @Auto UIntN toUIntN()
        {
        TODO
        }

    /**
     * Convert the number to an unchecked, variable-length unsigned integer.
     *
     * @return the unchecked, variable-length unsigned integer value
     */
    @Auto @Unchecked UIntN toUncheckedUIntN()
        {
        return toUIntN().toUnchecked();
        }

    /**
     * Convert the number to an unsigned 8-bit integer.
     *
     * @return the unsigned 8-bit integer value
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 8-bit integer range
     */
    @Auto UInt8 toUInt8()
        {
        return toUIntN().toUInt8();
        }

    /**
     * Convert the number to an unchecked, unsigned 8-bit integer.
     * Any additional magnitude is discarded.
     *
     * @return the unsigned 8-bit integer value
     */
    @Auto @Unchecked UInt8 toUncheckedUInt8()
        {
        return toUIntN().toUnchecked().toUInt8().toUnchecked();
        }

    /**
     * Convert the number to an unsigned 16-bit integer.
     *
     * @return the unsigned 16-bit integer value
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 16-bit integer range
     */
    @Auto UInt16 toUInt16()
        {
        return toUIntN().toUInt16();
        }

    /**
     * Convert the number to an unchecked, unsigned 16-bit integer.
     * Any additional magnitude is discarded.
     *
     * @return the unsigned 16-bit integer value
     */
    @Auto @Unchecked UInt16 toUncheckedUInt16()
        {
        return toUIntN().toUnchecked().toUInt16().toUnchecked();
        }

    /**
     * Convert the number to an unsigned 32-bit integer.
     *
     * @return the unsigned 32-bit integer value
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 32-bit integer range
     */
    @Auto UInt32 toUInt32()
        {
        return toUIntN().toUInt32();
        }

    /**
     * Convert the number to an unchecked, unsigned 32-bit integer.
     * Any additional magnitude is discarded.
     *
     * @return the unsigned 32-bit integer value
     */
    @Auto @Unchecked UInt32 toUncheckedUInt32()
        {
        return toUIntN().toUnchecked().toUInt32().toUnchecked();
        }

    /**
     * Convert the number to an unsigned 64-bit integer.
     *
     * @return the unsigned 64-bit integer value
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 64-bit integer range
     */
    @Auto UInt64 toUInt64()
        {
        return toUIntN().toUInt64();
        }

    /**
     * Convert the number to an unchecked, unsigned 64-bit integer.
     * Any additional magnitude is discarded.
     *
     * @return the unsigned 64-bit integer value
     */
    @Auto @Unchecked UInt64 toUncheckedUInt64()
        {
        return toUIntN().toUnchecked().toUInt64().toUnchecked();
        }

    /**
     * Convert the number to an unsigned 128-bit integer.
     *
     * @return the unsigned 128-bit integer value
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 128-bit integer range
     */
    @Auto UInt128 toUInt128()
        {
        return toIntN().toUInt128();
        }

    /**
     * Convert the number to an unchecked, unsigned 128-bit integer.
     * Any additional magnitude is discarded.
     *
     * @return the unsigned 128-bit integer value
     */
    @Auto @Unchecked UInt128 toUncheckedUInt128()
        {
        return toUIntN().toUnchecked().toUInt128().toUnchecked();
        }

    /**
     * Convert the number to a variable-length binary radix floating point number.
     */
    @Auto FloatN toFloatN()
        {
        TODO
        }

    /**
     * Convert the number to a 16-bit radix-2 (binary) floating point number.
     */
    @Auto Float16 toFloat16()
        {
        return toFloatN().toFloat16();
        }

    /**
     * Convert the number to a 16-bit radix-2 (binary) "brain" floating point number.
     */
    @Auto BFloat16 toBFloat16()
        {
        return toFloatN().toBFloat16();
        }

    /**
     * Convert the number to a 32-bit radix-2 (binary) floating point number.
     */
    @Auto Float32 toFloat32()
        {
        return toFloatN().toFloat32();
        }

    /**
     * Convert the number to a 64-bit radix-2 (binary) floating point number.
     */
    @Auto Float64 toFloat64()
        {
        return toFloatN().toFloat64();
        }

    /**
     * Convert the number to a 128-bit radix-2 (binary) floating point number.
     */
    @Auto Float128 toFloat128()
        {
        return toFloatN().toFloat128();
        }

    /**
     * Convert the number to a variable-length decimal radix floating point number.
     */
    @Auto DecN toDecN()
        {
        TODO
        }

    /**
     * Convert the number to a 32-bit radix-10 (decimal) floating point number.
     */
    @Auto Dec32 toDec32()
        {
        return toDecN().toDec32();
        }

    /**
     * Convert the number to a 64-bit radix-10 (decimal) floating point number.
     */
    @Auto Dec64 toDec64()
        {
        return toDecN().toDec64();
        }

    /**
     * Convert the number to a 128-bit radix-10 (decimal) floating point number.
     */
    @Auto Dec128 toDec128()
        {
        return toDecN().toDec128();
        }

    /**
     * Convert the number to a 4-bit integer.
     * Any additional magnitude is discarded.
     */
    @Auto Nibble toNibble()
        {
        return new Nibble(toInt64());
        }

    /**
     * Convert the number to a 128-bit radix-10 (decimal) floating point number.
     */
    Char toChar()
        {
        return new Char(toUInt32());
        }

    @Override
    String toString()
        {
        return text;
        }

    // TODO @Auto?
    FPLiteral toFPLiteral()
        {
        return new FPLiteral(text);
        }


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return text.size;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        return text.appendTo(buf);
        }
    }

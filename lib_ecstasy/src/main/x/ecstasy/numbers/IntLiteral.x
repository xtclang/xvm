import Number.Rounding;

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
        implements IntConvertible
        implements FPConvertible
        implements Destringable
        default(0) {
    /**
     * Construct an IntLiteral from a String.
     *
     * @param text  the literal value
     */
    @Override
    construct(String text) {
        assert:arg text.size > 0 as $"Illegal integer literal: The literal is empty";

        // optional leading sign
        Int offset = 0;
        Int length = text.size;
        switch (text[offset]) {
        case '-':
            explicitSign = Signum.Negative;
            ++offset;
            break;

        case '+':
            explicitSign = Signum.Positive;
            ++offset;
            break;
        }

        // optional leading format
        if (length - offset >= 2 && text[offset] == '0') {
            switch (text[offset+1]) {
            case 'B', 'b':
                radix   = 2;
                offset += 2;
                break;

            case      'o':      // capital 'o' is not allowed; it gets confused with zero
                radix   = 8;
                offset += 2;
                break;

            case 'X', 'x':
                radix   = 16;
                offset += 2;
                break;

            default:
                radix   = 10;
                break;
            }
        }

        // digits
        UIntN magnitude = 0;
        Int   digits    = 0;
        NextChar: while (offset < length) {
            Char ch = text[offset];
            if (UInt8 n := ch.asciiHexit()) {
                if (n >= radix) {
                    break NextChar;
                }

                magnitude = magnitude * radix + n;
                ++digits;
            } else if (ch == '_') {
                assert:arg digits > 0 as $|Illegal initial underscore character at the beginning\
                                          | of the integer literal {text.quoted()}
                                         ;
            } else {
                break NextChar;
            }

            ++offset;
        }

        // allow KI/KB, MI/MB, GI/GB TI/TB, PI/PB, EI/EB, ZI/ZB, YI/YB
        PossibleSuffix: if (radix == 10 && offset < length) {
            Int factorIndex;
            switch (text[offset]) {
            case 'K', 'k':
                factorIndex = 0;
                break;

            case 'M', 'm':
                factorIndex = 1;
                break;

            case 'G', 'g':
                factorIndex = 2;
                break;

            case 'T', 't':
                factorIndex = 3;
                break;

            case 'P', 'p':
                factorIndex = 4;
                break;

            case 'E', 'e':
                factorIndex = 5;
                break;

            case 'Z', 'z':
                factorIndex = 6;
                break;

            case 'Y', 'y':
                factorIndex = 7;
                break;

            default:
                break PossibleSuffix;
            }
            ++offset;

            Factor[] factors = DecimalFactor.values;    // implicitly decimal, e.g. "k"
            if (offset < length) {
                switch (text[offset]) {
                case 'B', 'b':                      // explicitly decimal, e.g. "kb"
                    ++offset;
                    break;

                case 'I', 'i':                      // explicitly binary, e.g. "ki"
                    ++offset;
                    factors = BinaryFactor.values;

                    if (offset < length) {          // optional trailing "b", e.g. "kib"
                        Char optionalB = text[offset];
                        if (optionalB == 'B' || optionalB == 'b') {
                            ++offset;
                        }
                    }
                    break;
                }
            }

            magnitude *= factors[factorIndex].factor;
        }

        assert:arg offset == length as $|Illegal character {text[offset].toSourceString()} at offset\
                                        | {offset} in radix {radix}} integer literal {text.quoted()}
                                       ;

        assert:arg digits > 0 as $|Illegal radix {radix} integer literal; literal contains\
                                  | no valid digits: {text.quoted()}
                                 ;

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
     * A value that holds a factor.
     */
    interface Factor {
        @RO UIntN factor;
    }

    /**
     * The decimal (1000x) factors.
     */
    enum DecimalFactor(UIntN factor)
            implements Factor {
        KB(1_000),
        MB(1_000_000),
        GB(1_000_000_000),
        TB(1_000_000_000_000),
        PB(1_000_000_000_000_000),
        EB(1_000_000_000_000_000_000),
        ZB(1_000_000_000_000_000_000_000),
        YB(1_000_000_000_000_000_000_000_000),
    }

    /**
     * The binary (1024x) factors.
     */
    enum BinaryFactor(UIntN factor)
            implements Factor {
        KI(1024),
        MI(1024 * 1024),
        GI(1024 * 1024 * 1024),
        TI(1024 * 1024 * 1024 * 1024),
        PI(1024 * 1024 * 1024 * 1024 * 1024),
        EI(1024 * 1024 * 1024 * 1024 * 1024 * 1024),
        ZI(1024 * 1024 * 1024 * 1024 * 1024 * 1024 * 1024),
        YI(1024 * 1024 * 1024 * 1024 * 1024 * 1024 * 1024 * 1024),
    }

    /**
     * The minimum number of bits to store the IntLiteral's value as a signed integer in
     * a twos-complement format, where the number of bits is a power-of-two of at least 8.
     */
    Int minIntBits.get() {
        Int count;

        if (magnitude == 0) {
            // it doesn't take much to store a zero
            count = 1;
        } else if (explicitSign == Signum.Zero && radix != 10) {
            // combination of no explicit sign and a non-decimal radix implies that the
            // number of bits necessary is the number of bits to hold the magnitude
            // itself; examples:
            //   0x7F -> 8 bits
            //   0x80 -> 8 bits
            //   0xFF -> 8 bits
            count = magnitude.leftmostBit.trailingZeroCount + 1;
        } else if (explicitSign == Signum.Negative) {
            // examples:
            //   -0x7F -> 8 bits
            //   -0x80 -> 8 bits
            //   -0x81 -> 16 bits
            //   -0xFF -> 16 bits
            count = (magnitude - 1).leftmostBit.trailingZeroCount + 2;
        } else {
            // examples:
            //   127  +0x7F  -> 8 bits
            //   128  +0x80  -> 16 bits
            count = magnitude.leftmostBit.trailingZeroCount + 2;
        }

        // round up to nearest power of 2 (at least 8)
        return (count * 2 - 1).leftmostBit.notLessThan(8);
    }

    /**
     * The minimum number of bits to store the IntLiteral's value as an unsigned integer,
     * where the number of bits is a power-of-two of at least 8.
     */
    @RO Int minUIntBits.get() {
// TODO review
        assert explicitSign != Signum.Negative;

        if (magnitude == 0) {
            // smallest int: 8 bits (1 byte)
            return 8;
        }

        return (magnitude.leftmostBit.trailingZeroCount * 2 + 1).leftmostBit.notLessThan(8);
    }

    /**
     * The minimum number of bits to store the IntLiteral's value in the IEEE754 binary floating
     * point format, where the number of bits is a power-of-two of at least 16.
     */
    @RO Int minFloatBits.get() {
        TODO
    }

    /**
     * The minimum number of bits to store the IntLiteral's value in the IEEE754 decimal floating
     * point format, where the number of bits is a power-of-two of at least 8.
     */
    @RO Int minDecBits.get() {
        TODO
    }


    // ----- IntNumber API -------------------------------------------------------------------------

    /**
     * Bitwise AND.
     */
    @Op("&")
    IntLiteral and(IntLiteral that) {
        return this & that;
    }

    /**
     * Bitwise OR.
     */
    @Op("|")
    IntLiteral or(IntLiteral that) {
        return this | that;
    }

    /**
     * Bitwise XOR.
     */
    @Op("^")
    IntLiteral xor(IntLiteral that) {
        return this ^ that;
    }

    /**
     * Bitwise NOT.
     */
    @Op("~")
    IntLiteral not() {
        return ~this;
    }

    /**
     * Shift bits left. Works like an arithmetic left shift.
     */
    @Op("<<")
    IntLiteral shiftLeft(Int count) {
        return this << count;
    }

    /**
     * Shift bits right. Works like an arithmetic right shift.
     */
    @Op(">>")
    IntLiteral shiftRight(Int count) {
        return this >> count;
    }

    /**
     * Works identically to the `shiftRight`.
     */
    @Op(">>>")
    IntLiteral shiftAllRight(Int count) {
        return this >>> count;
    }


    // ----- Number API ----------------------------------------------------------------------------

    /**
     * Calculate the negative of this number.
     *
     * @return the negative of this number, generally equal to `0-this`
     */
    @Op("-#") IntLiteral neg() {
        return -this;
    }

    /**
     * Addition: Add another number to this number, and return the result.
     */
    @Op("+")
    IntLiteral add(IntLiteral n) {
        return new IntLiteral((this.toIntN() + n.toIntN()).toString());
    }

    /**
     * Subtraction: Subtract another number from this number, and return the result.
     */
    @Op("-")
    IntLiteral sub(IntLiteral n) {
        return new IntLiteral((this.toIntN() - n.toIntN()).toString());
    }

    /**
     * Multiplication: Multiply this number by another number, and return the result.
     */
    @Op("*")
    IntLiteral mul(IntLiteral n) {
        return new IntLiteral((this.toIntN() * n.toIntN()).toString());
    }

    /**
     * Division: Divide this number by another number, and return the result.
     */
    @Op("/")
    IntLiteral div(IntLiteral n) {
        return new IntLiteral((this.toIntN() / n.toIntN()).toString());
    }

    /**
     * Modulo: Return the modulo that would result from dividing this number by another number.
     */
    @Op("%")
    IntLiteral mod(IntLiteral n) {
        return new IntLiteral((this.toIntN() % n.toIntN()).toString());
    }


    // ----- Sequential ----------------------------------------------------------------------------

    @Override
    conditional IntLiteral prev() {
        return True, this - 1;
    }

    @Override
    conditional IntLiteral next() {
        return True, this + 1;
    }

    @Override
    Int stepsTo(IntLiteral that) {
        return that - this;
    }

    @Override
    IntLiteral skip(Int steps) {
        return this + steps.toIntLiteral();
    }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert the value to a single Bit.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     *
     * @return the corresponding Bit value
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 32-bit integer range
     *                      and `truncate` is not `True`
     */
    @Auto
    Bit toBit(Boolean truncate = False) {
        Byte byte = toByte(truncate);
        assert:bounds truncate || byte <= 1;
        return byte & 1 == 1 ? 1 : 0;
    }

    /**
     * Convert the number to a 4-bit integer.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     *
     * @return the corresponding Nibble value
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 4-bit integer range
     *                      and `truncate` is not `True`
     */
    @Auto
    Nibble toNibble(Boolean truncate = False) {
        return Nibble.of(toInt64(truncate));
    }

    /**
     * Convert the value to a character value.
     *
     * @param truncate   pass `True` to silently truncate the integer value if necessary
     *
     * @return the corresponding Char value
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 32-bit integer range
     *                      and `truncate` is not `True`
     */
    Char toChar(Boolean truncate = False) {
        return new Char(toUInt32(truncate));
    }

    @Override
    IntLiteral toIntLiteral(Rounding direction = TowardZero) {
        return this;
    }

    @Auto
    @Override
    Int toInt(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    IntN toIntN(Rounding direction = TowardZero) {
        TODO
    }

    @Auto
    @Override
    UInt toUInt(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    UIntN toUIntN(Rounding direction = TowardZero);

    @Auto
    @Override
    Dec toDec() {
        return toFPLiteral().toDec();
    }

    @Auto
    @Override
    Dec32 toDec32() {
        return toFPLiteral().toDec32();
    }

    @Auto
    @Override
    Dec64 toDec64() {
        return toFPLiteral().toDec64();
    }

    @Auto
    @Override
    Dec128 toDec128() {
        return toFPLiteral().toDec128();
    }

    @Auto
    @Override
    DecN toDecN() {
        return toFPLiteral().toDecN();
    }

    @Auto
    @Override
    Float8e4 toFloat8e4() {
        return toFPLiteral().toFloat8e4();
    }

    @Auto
    @Override
    Float8e5 toFloat8e5() {
        return toFPLiteral().toFloat8e5();
    }

    @Auto
    @Override
    BFloat16 toBFloat16() {
        return toFPLiteral().toBFloat16();
    }

    @Auto
    @Override
    Float16 toFloat16() {
        return toFPLiteral().toFloat16();
    }

    @Auto
    @Override
    Float32 toFloat32() {
        return toFPLiteral().toFloat32();
    }

    @Auto
    @Override
    Float64 toFloat64() {
        return toFPLiteral().toFloat64();
    }

    @Auto
    @Override
    Float128 toFloat128() {
        return toFPLiteral().toFloat128();
    }

    @Auto
    @Override
    FloatN toFloatN() {
        return toFPLiteral().toFloatN();
    }

    @Auto
    @Override
    FPLiteral toFPLiteral() {
        return new FPLiteral(text);
    }

    @Override
    String toString() {
        return text;
    }


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return text.size;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        return text.appendTo(buf);
    }
}
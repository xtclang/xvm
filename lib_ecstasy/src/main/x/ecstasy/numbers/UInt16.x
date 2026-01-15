const UInt16
        extends UIntNumber
        default(0) {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an UInt16.
     */
    static IntLiteral MinValue = 0;

    /**
     * The maximum value for an UInt16.
     */
    static IntLiteral MaxValue = 0xFFFF;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 16;
    }

    @Override
    static Int16 zero() {
        return 0;
    }

    @Override
    static Int16 one() {
        return 1;
    }

    @Override
    static conditional Range<UInt16> range() {
        return True, MinValue..MaxValue;
    }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 16-bit unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        assert bits.size == 16;
        super(bits);
    }

    /**
     * Construct a 16-bit unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert bytes.size == 2;
        super(bytes);
    }

    /**
     * Construct a 16-bit unsigned integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        assert UInt16 n := parse(text);
        this.bits = n.bits;
    }


    // ----- parsing -------------------------------------------------------------------------------

    /**
     * Parse a UInt16 value from the passed String.
     *
     * @param text   the string value
     * @param radix  (optional) the radix of the passed String value, in the range `2..36`
     *
     * @return True iff the passed String value represents a legal UInt16 value
     * @return (conditional) the parsed UInt16 value
     */
    static conditional UInt16 parse(String text, Int? radix=Null) {
        if (text.empty || !(2 <= radix? <= 36)) {
            return False;
        }

        // optional leading sign
        Int length = text.size;
        Int offset = text[0] == '+' ? 1 : 0;

        // optional radix indicator
        if (radix == Null) {
            // determine the radix from the string value (the default radix is decimal)
            radix = 10;
            if (length >= offset + 2 && text[offset] == '0') {
                switch (text[offset+1]) {
                case 'b', 'B':
                    radix   = 2;
                    offset += 2;
                    break;
                case 'o':
                    radix   = 8;
                    offset += 2;
                    break;
                case 'x', 'X':
                    radix   = 16;
                    offset += 2;
                    break;
                }
            }
        } else if (length >= offset + 2 && text[offset] == '0' && switch (radix) {
                case  2: text[offset+1].lowercase == 'b';
                case  8: text[offset+1].lowercase == 'o';
                case 16: text[offset+1].lowercase == 'x';
                default: False;
                }) {
            offset += 2;
        }

        // digits
        Int     magnitude = 0;
        Boolean digits    = False;
        if (radix == 10) {
            NextChar: while (offset < length) {
                Char ch = text[offset];
                if (UInt8 digit := ch.asciiDigit()) {
                    magnitude = magnitude * radix + digit;
                    digits = True;
                    if (magnitude > UInt16.MaxValue) {
                        return False;
                    }
                } else if (ch == '_') {
                    if (!digits) {
                        return False;
                    }
                } else {
                    break;
                }
                ++offset;
            }

            // allow KI/KB, MI/MB, GI/GB TI/TB, PI/PB, EI/EB, ZI/ZB, YI/YB
            PossibleSuffix: if (offset < length) {
                Int factorIndex;
                switch (text[offset]) {
                case 'K', 'k':
                    factorIndex = 0;
                    break;

                case 'M', 'm':
                case 'G', 'g':
                case 'T', 't':
                case 'P', 'p':
                case 'E', 'e':
                case 'Z', 'z':
                case 'Y', 'y':
                    if (magnitude != 0) {
                        // these would all be out of range -- with any factor -- for UInt16
                        return False;
                    }
                    factorIndex = -1;
                    break;

                default:
                    return False;
                }
                ++offset;

                IntLiteral.Factor[] factors = IntLiteral.DecimalFactor.values; // implicitly decimal, e.g. "k"
                if (offset < length) {
                    switch (text[offset]) {
                    case 'B', 'b':                      // explicitly decimal, e.g. "kb"
                        ++offset;
                        break;

                    case 'I', 'i':                      // explicitly binary, e.g. "ki"
                        ++offset;
                        factors = IntLiteral.BinaryFactor.values;

                        if (offset < length) {          // optional trailing "b", e.g. "kib"
                            Char optionalB = text[offset];
                            if (optionalB == 'B' || optionalB == 'b') {
                                ++offset;
                            }
                        }
                        break;
                    }
                }

                if (factorIndex >= 0) {
                    magnitude *= factors[factorIndex].factor.toInt();
                }
            }
        } else { // the number is *not* radix 10
            NextChar: while (offset < length) {
                Char ch = text[offset++];
                Int digit;
                switch (ch) {
                case '0'..'9':
                    digit = ch - '0';
                    break;
                case 'A'..'Z':
                    digit = ch - 'A' + 10;
                    break;
                case 'a'..'z':
                    digit = ch - 'a' + 10;
                    break;
                case '_':
                    if (digits) {
                        continue NextChar;
                    }
                    return False;
                default:
                    return False;
                }

                if (digit >= radix) {
                    return False;
                }
                magnitude = magnitude * radix + digit;
                if (magnitude > UInt16.MaxValue) {
                    return False;
                }
                digits = True;
            }
        }

        return !digits || offset < length || magnitude > MaxValue
                ? False
                : True, magnitude.toUInt16();
    }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get() {
        return this == 0 ? Zero : Positive;
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    UInt16 add(UInt16! n) {
        return this + n;
    }

    @Override
    @Op("-")
    UInt16 sub(UInt16! n) {
        return this - n;
    }

    @Override
    @Op("*")
    UInt16 mul(UInt16! n) {
        return this * n;
    }

    @Override
    @Op("/")
    UInt16 div(UInt16! n) {
        return this / n;
    }

    @Override
    @Op("%")
    UInt16 mod(UInt16! n) {
        return this % n;
    }

    @Override
    UInt16 pow(UInt16! n) {
        UInt16 result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UInt16 next() {
        if (this < MaxValue) {
            return True, this + 1;
        }

        return False;
    }

    @Override
    conditional UInt16 prev() {
        if (this > MinValue) {
            return True, this - 1;
        }

        return False;
    }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert this UInt16 to an Int8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 8 bits bits of this UInt16. The high-order bit becomes the sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int8
     *
     * @return  an Int8 that has the same bit pattern as the low order 8 bits of this UInt16
     *
     * @throws OutOfBounds if checkBounds is True and this UInt16 is higher then Int8.MaxValue
     */
    @Override
    Int8 toInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
    }

    /**
     * Convert this UInt16 to an Int16.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the bit pattern
     * of this UInt16. The high-order bit becomes the sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int16
     *
     * @return  an Int16 that has the same bit pattern as this UInt16
     */
    @Override
    Int16 toInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= Int16.MaxValue;
        return new Int16(bits);
    }

    /**
     * Convert this UInt16 to an Int32.
     *
     * Conversion is performed by zero-extending this UInt16 up to an Int32.
     * A UInt16 will always fit within an Int32, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int32
     *
     * @return  an Int32 that is equivalent to this UInt16
     */
    @Auto
    @Override
    Int32 toInt32(Boolean checkBounds = False) = new Int32(new Bit[32](i -> (i < 32-bitLength ? 0 : bits[i])));

    /**
     * Convert this UInt16 to an Int64.
     *
     * Conversion is performed by zero-extending this UInt16 up to an Int64.
     * A UInt16 will always fit within an Int64, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int64
     *
     * @return  an Int64 that is equivalent to this UInt16
     */
    @Auto
    @Override
    Int64 toInt64(Boolean checkBounds = False) = new Int64(new Bit[64](i -> (i < 64-bitLength ? 0 : bits[i])));

    /**
     * Convert this UInt16 to an Int128.
     *
     * Conversion is performed by zero-extending this UInt16 up to an Int128.
     * A UInt16 will always fit within an Int128, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int128
     *
     * @return  an Int128 that is equivalent to this UInt16
     */
    @Auto
    @Override
    Int128 toInt128(Boolean checkBounds = False) = new Int128(new Bit[128](i -> (i < 128-bitLength ? 0 : bits[i])));

    /**
     * Convert this UInt16 to an UInt8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 8 bits bits of this UInt16.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt8
     *
     * @return  an Int8 that has the same bit pattern as the low order 8 bits of this UInt16
     *
     * @throws OutOfBounds if checkBounds is True and this UInt16 is higher then Int8.MaxValue
     */
    @Override
    UInt8 toUInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
    }

    /**
     * Convert this UInt16 to an UInt16, which is effectively a no-op and returns this UInt16.
     *
     * @param checkBounds  this parameter will be ignored
     *
     * @return  this UInt16
     */
    @Override
    UInt16 toUInt16(Boolean checkBounds = False) = this;

    /**
     * Convert this UInt16 to an UInt32.
     *
     * Conversion is performed by sign extending this UInt16 up to an UInt32.
     * A UInt16 will always fit within an Int32, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt32
     *
     * @return  an UInt32 that is equivalent to this UInt16
     */
    @Auto
    @Override
    UInt32 toUInt32(Boolean checkBounds = False) = new UInt32(new Bit[32](i -> (i < 32-bitLength ? 0 : bits[i])));

    /**
     * Convert this UInt16 to an UInt64.
     *
     * Conversion is performed by sign extending this UInt16 up to an UInt64.
     * A UInt16 will always fit within an Int64, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt64
     *
     * @return  an UInt64 that is equivalent to this UInt16
     */
    @Auto
    @Override
    UInt64 toUInt64(Boolean checkBounds = False) = new UInt64(new Bit[64](i -> (i < 64-bitLength ? 0 : bits[i])));

    /**
     * Convert this UInt16 to an UInt128.
     *
     * Conversion is performed by sign extending this UInt16 up to an UInt128.
     * A UInt16 will always fit within an Int128, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt128
     *
     * @return  an UInt128 that is equivalent to this UInt16
     */
    @Auto
    @Override
    UInt128 toUInt128(Boolean checkBounds = False) = new UInt128(new Bit[128](i -> (i < 128-bitLength ? 0 : bits[i])));


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return calculateStringSize(this, sizeArray);
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        if (sign == Zero) {
            buf.add('0');
        } else {
            (UInt16 left, UInt16 digit) = this /% 10;
            if (left.sign != Zero) {
                left.appendTo(buf);
            }
            buf.add(Digits[digit]);
        }
        return buf;
    }

    // MaxValue = 65_535 (5 digits)
    private static UInt16[] sizeArray =
         [
         9, 99, 999, 9_999, 65_535
         ];
}
const Int64
        extends IntNumber
        default(0) {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int64.
     */
    static IntLiteral MinValue = -0x8000_0000_0000_0000;

    /**
     * The maximum value for an Int64.
     */
    static IntLiteral MaxValue =  0x7FFF_FFFF_FFFF_FFFF;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 64;
    }

    @Override
    static Int64 zero() {
        return 0;
    }

    @Override
    static Int64 one() {
        return 1;
    }

    @Override
    static conditional Range<Int64> range() {
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
    construct(Bit[] bits) {
        assert bits.size == 64;
        super(bits);
    }

    /**
     * Construct a 64-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert bytes.size == 8;
        super(bytes);
    }

    /**
     * Construct a 64-bit signed integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        assert Int64 n := parse(text);
        this.bits = n.bits;
    }


    // ----- parsing -------------------------------------------------------------------------------

    /**
     * Parse a Int64 value from the passed String.
     *
     * @param text   the string value
     * @param radix  (optional) the radix of the passed String value, in the range `2..36`
     *
     * @return True iff the passed String value represents a legal Int64 value
     * @return (conditional) the parsed Int64 value
     */
    static conditional Int64 parse(String text, Int? radix=Null) {
        if (text.empty || !(2 <= radix? <= 36)) {
            return False;
        }

        // optional leading sign
        Int     length = text.size;
        Int     offset = 0;
        Boolean neg    = False;
        switch (text[0]) {
            case '-':
                neg    = True;
                offset = 1;
                break;
            case '+':
                offset = 1;
                break;
        }

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
        Int     magnitude = 0;              // built as a negative value
        Boolean digits    = False;
        if (radix == 10) {
            NextChar: while (offset < length) {
                Char ch = text[offset];
                if (UInt8 digit := ch.asciiDigit()) {
                    Int n = magnitude * radix - digit;
                    if (n > magnitude) {    // overflow check
                        return False;
                    }
                    magnitude = n;
                    digits    = True;
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
                case 'Y', 'y':
                    if (magnitude != 0) {
                        // these would all be out of range -- with any factor -- for Int64
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
                    // do the factor scaling using an Int128 so we don't run out of bits (it makes
                    // detecting overflow much easier)
                    Int128 scaled =  magnitude * factors[factorIndex].factor.toInt128();
                    if (scaled < Int64.MinValue) {
                        return False;
                    }
                    magnitude = scaled.toInt();
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
                Int n = magnitude * radix - digit;
                if (n > magnitude) {    // check for overflow of the negative magnitude
                    return False;
                }
                magnitude = n;
                digits    = True;
            }
        }

        if (!digits || offset < length) {
            return False;
        }

        // remember: the magnitude is built as a negative value
        return neg
                ? (True, magnitude)
                : (magnitude != MinValue, -magnitude);
    }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get() {
        return switch (this <=> 0) {
            case Lesser : Negative;
            case Equal  : Zero;
            case Greater: Positive;
        };
    }

    @Override
    UInt64 magnitude.get() {
        return toInt128().abs().toUInt64();
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Int64 neg() {
        return ~this + 1;
    }

    @Override
    @Op("+")
    Int64 add(Int64! n) {
        return this + n;
    }

    @Override
    @Op("-")
    Int64 sub(Int64! n) {
        return this + ~n + 1;
    }

    @Override
    @Op("*")
    Int64 mul(Int64! n) {
        return this * n;
    }

    @Override
    @Op("/")
    Int64 div(Int64! n) {
        return this / n;
    }

    @Override
    @Op("%")
    Int64 mod(Int64! n) {
        return this % n;
    }

    @Override
    Int64 abs() {
        return this < 0 ? -this : this;
    }

    @Override
    Int64 pow(Int64! n) {
        Int64 result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Int64 next() {
        if (this < MaxValue) {
            return True, this + 1;
        }

        return False;
    }

    @Override
    conditional Int64 prev() {
        if (this > MinValue) {
            return True, this - 1;
        }

        return False;
    }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Int8 toInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int8.MinValue && this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
    }

    @Override
    Int16 toInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int16.MinValue && this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
    }

    @Override
    Int32 toInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int32.MinValue && this <= Int32.MaxValue;
        return new Int32(bits[bitLength-32 ..< bitLength]);
    }

    @Override
    Int64 toInt64(Boolean checkBounds = False) = this;

    @Auto
    @Override
    Int128 toInt128(Boolean checkBounds = False) = new Int128(new Bit[128](i -> bits[i < 128-bitLength ? 0 : i]));

    @Override
    UInt8 toUInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt8.MinValue && this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
    }

    @Override
    UInt16 toUInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt16.MinValue && this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
    }

    @Override
    UInt32 toUInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt32.MinValue && this <= UInt32.MaxValue;
        return new UInt32(bits[bitLength-32 ..< bitLength]);
    }

    @Override
    UInt64 toUInt64(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt64(bits);
    }

    @Override
    UInt128 toUInt128(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt128(new Bit[128](i -> (i < 128-bitLength ? 0 : bits[i])));
    }


    // ----- Hashable functions --------------------------------------------------------------------

    @Override
    static <CompileType extends Int64> Int64 hashCode(CompileType value) {
        return value;
    }

    @Override
    static <CompileType extends Int64> Boolean equals(CompileType value1, CompileType value2) {
        return value1.bits == value2.bits;
    }
}
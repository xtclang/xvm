import collections.ConstOrdinalList;

import io.IllegalUTF;

const Char(UInt32 codepoint)
        implements Sequential
        default('\u0000')
    {
    // ----- constructors --------------------------------------------------------------------------

    assert()
        {
        assert codepoint <= 0x10FFFF as $"Character code-point ({codepoint}) out of Unicode range";

        assert !(0xD7FF < codepoint < 0xE000) as $|Character code-point ({codepoint}) is a surrogate;\
                                                  | surrogates are not valid Unicode characters
                                                 ;
        }

    /**
     * Construct a character from the codepoint indicated by the passed UTF8 value.
     *
     * @param utf8  the character, in UTF8 format
     */
    construct(Byte[] utf8)
        {
        Int length = utf8.size;
        UInt32 codepoint;
        switch (length)
            {
            case 1:
                // ASCII value
                codepoint = utf8[0].toUInt32();
                if (codepoint > 0x7F)
                    {
                    throw new IllegalUTF($"Illegal ASCII code in 1-byte UTF8 format: {codepoint}");
                    }
                break;

            case 2..6:
                // #1s first byte  trailing  # trailing  bits  code-points
                // --- ----------  --------  ----------  ----  -----------------------
                //  0  0xxxxxxx    n/a           0         7   U+0000    - U+007F     (ASCII)
                //  2  110xxxxx    10xxxxxx      1        11   U+0080    - U+07FF
                //  3  1110xxxx    10xxxxxx      2        16   U+0800    - U+FFFF
                //  4  11110xxx    10xxxxxx      3        21   U+10000   - U+1FFFFF
                //  5  111110xx    10xxxxxx      4        26   U+200000  - U+3FFFFFF
                //  6  1111110x    10xxxxxx      5        31   U+4000000 - U+7FFFFFFF
                codepoint = utf8[0].toUInt32();
                Int bits = (~codepoint).leftmostBit.trailingZeroCount;
                if (length != 7 - bits)
                    {
                    throw new IllegalUTF($"Expected UTF8 length of {7 - bits} bytes; actual length is {length} bytes");
                    }
                codepoint &= 0b11111 >>> 5 - bits;

                for (Int i : [1..length))
                    {
                    Byte b = utf8[i];
                    if (b & 0b11000000 != 0b10000000)
                        {
                        throw new IllegalUTF("trailing unicode byte does not match 10xxxxxx");
                        }
                    codepoint = codepoint << 6 | (b & 0b00111111).toUInt32();
                    }
                break;

            default:
                throw new IllegalUTF($"Illegal UTF8 encoding length: {length}");
            }

        construct Char(codepoint);
        }

    /**
     * Construct a character from the codepoint indicated by the passed `Byte` value. This is
     * primarily useful for codepoints in the ASCII range.
     *
     * @param codepoint  the codepoint for the character
     */
    construct(Byte codepoint)
        {
        construct Char(codepoint.toUInt32());
        }

    /**
     * Construct a character from the codepoint indicated by the passed `Int` value.
     *
     * @param n  the codepoint for the character
     */
    construct(Int n)
        {
        construct Char(codepoint.toUInt32());
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The unicode code-point for this character.
     */
    UInt32 codepoint;


    // ----- Sequential ----------------------------------------------------------------------------

    @Override
    conditional Char prev()
        {
        if (codepoint > 0)
            {
            return True, new Char(codepoint - 1);
            }
        return False;
        }

    @Override
    conditional Char next()
        {
        if (codepoint < UInt32.maxvalue)
            {
            return True, new Char(codepoint + 1);
            }
        return False;
        }

    @Override
    Int stepsTo(Char that)
        {
        return that - this;
        }

    @Override
    Char skip(Int steps)
        {
        return this + steps.toUInt32();
        }


    // ----- operators ---------------------------------------------------------------------------

    @Op("+")
    Char add(UInt32 n)
        {
        return new Char(codepoint + n);
        }

    @Op("+")
    String add(Char ch)
        {
        return new StringBuffer(2).add(this).add(ch).toString();
        }

    @Op("+")
    String add(String s)
        {
        return new StringBuffer(1 + s.size).add(this).addAll(s).toString();
        }

    @Op("-")
    Char sub(UInt32 n)
        {
        return new Char(codepoint - n);
        }

    @Op("-")
    UInt32 sub(Char ch)
        {
        return this.codepoint - ch.codepoint;
        }

    @Op("*")
    String dup(Int n)
        {
        if (n == 0)
            {
            return "";
            }

        assert n > 0;
        StringBuffer buf = new StringBuffer(n);
        for (Int i = 0; i < n; ++i)
            {
            buf.add(this);
            }
        return buf.toString();
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * A direct conversion from the Char to a Byte is supported because of ASCII. An out-of-range
     * value (anything not an ASCII character) will result in an exception; this is subtly different
     * from [toUInt8], which supports any value up to 0xFF.
     */
    Byte toByte()
        {
        assert codepoint <= 0x7F;
        return codepoint.toByte();
        }

    /**
     * A conversion to Byte[] results in a byte array with between 1-6 bytes containing
     * a UTF-8 formatted codepoint.
     *
     * Note: The current version 9 of Unicode limits code points to 0x10FFFF, which
     * means that all UTF-8 encoding will use between 1-4 bytes.
     */
    immutable Byte[] utf8()
        {
        Int    length = calcUtf8Length();
        Byte[] bytes  = new Byte[length];
        Int    actual = formatUtf8(bytes, 0);
        assert actual == length;
        return bytes.makeImmutable();
        }

    // conversion to integer types are **not** @Auto, because a Char is not considered to be a
    // number, and a conversion fails if the codepoint is out of range of the desired type

    /**
     * @return the character's codepoint as an 8-bit signed integer
     * @throws an exception if the codepoint is not in the range 0..127
     */
    Int8 toInt8()
        {
        return codepoint.toInt8();
        }

    /**
     * @return the character's codepoint as a 16-bit signed integer
     * @throws an exception if the codepoint is not in the range 0..32767
     */
    Int16 toInt16()
       {
       return codepoint.toInt16();
       }

    /**
     * @return the character's codepoint as a 32-bit signed integer
     */
    Int32 toInt32()
       {
       return codepoint.toInt32();
       }

    /**
     * @return the character's codepoint as a 64-bit signed integer
     */
    Int64 toInt64()
        {
        return codepoint.toInt64();
        }

    /**
     * @return the character's codepoint as a 128-bit signed integer
     */
    Int128 toInt128()
        {
        return codepoint.toInt128();
        }

    /**
     * @return the character's codepoint as a variable-length signed integer
     */
    IntN toIntN()
        {
        return codepoint.toIntN();
        }

    /**
     * @return the character's codepoint as an 8-bit unsigned integer
     * @throws an exception if the codepoint is not in the range 0..255
     */
    UInt8 toUInt8()
        {
        return codepoint.toUInt8();
        }

    /**
     * @return the character's codepoint as a 16-bit unsigned integer
     * @throws an exception if the codepoint is not in the range 0..65535
     */
    UInt16 toUInt16()
        {
        return codepoint.toUInt16();
        }

    /**
     * @return the character's codepoint as a 32-bit unsigned integer
     */
    UInt32 toUInt32()
        {
        return codepoint;
        }

    /**
     * @return the character's codepoint as a 64-bit unsigned integer
     */
    UInt64 toUInt64()
        {
        return codepoint.toUInt64();
        }

    /**
     * @return the character's codepoint as a 128-bit unsigned integer
     */
    UInt128 toUInt128()
        {
        return codepoint.toUInt128();
        }

    /**
     * @return the character's codepoint as a variable-length unsigned integer
     */
    UIntN toUIntN()
        {
        return codepoint.toUIntN();
        }

    /**
     * @return the character as it would appear in source code as a character literal
     */
    String toSourceString()
        {
        Int len = 1;
        len := isEscaped();

        StringBuffer buf = new StringBuffer(len+2);
        buf.add('\'');
        appendEscaped(buf);
        buf.add('\'');

        return buf.toString();
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Determine if the specified character is considered to be white-space.
     *
     * @return True iff this character is considered to be an Ecstasy whitespace character
     */
    Boolean isWhitespace()
        {
        // optimize for the ASCII range
        if (codepoint <= 0x7F)
            {
            return 0x09 <= codepoint <= 0x20
                    //                              2               1      0
                    //                              0FEDCBA9876543210FEDCBA9
                    && 1.as(Int) << codepoint-9 & 0b111110100000000000011111 != 0;
            }

        return switch (codepoint)
            {
         // case 0x0009:        //   U+0009      9  HT      Horizontal Tab
         // case 0x000A:        //   U+000A     10  LF      Line Feed
         // case 0x000B:        //   U+000B     11  VT      Vertical Tab
         // case 0x000C:        //   U+000C     12  FF      Form Feed
         // case 0x000D:        //   U+000D     13  CR      Carriage Return
         // case 0x001A:        //   U+001A     26  SUB     End-of-File, or “control-Z”
         // case 0x001C:        //   U+001C     28  FS      File Separator
         // case 0x001D:        //   U+001D     29  GS      Group Separator
         // case 0x001E:        //   U+001E     30  RS      Record Separator
         // case 0x001F:        //   U+001F     31  US      Unit Separator
         // case 0x0020:        //   U+0020     32  SP      Space
            case 0x0085:        //   U+0085    133  NEL     Next Line
            case 0x00A0:        //   U+00A0    160  &nbsp;  Non-breaking space
            case 0x1680:        //   U+1680   5760          Ogham Space Mark
            case 0x2000:        //   U+2000   8192          En Quad
            case 0x2001:        //   U+2001   8193          Em Quad
            case 0x2002:        //   U+2002   8194          En Space
            case 0x2003:        //   U+2003   8195          Em Space
            case 0x2004:        //   U+2004   8196          Three-Per-Em Space
            case 0x2005:        //   U+2005   8197          Four-Per-Em Space
            case 0x2006:        //   U+2006   8198          Six-Per-Em Space
            case 0x2007:        //   U+2007   8199          Figure Space
            case 0x2008:        //   U+2008   8200          Punctuation Space
            case 0x2009:        //   U+2009   8201          Thin Space
            case 0x200A:        //   U+200A   8202          Hair Space
            case 0x2028:        //   U+2028   8232   LS     Line Separator
            case 0x2029:        //   U+2029   8233   PS     Paragraph Separator
            case 0x202F:        //   U+202F   8239          Narrow No-Break Space
            case 0x205F:        //   U+205F   8287          Medium Mathematical Space
            case 0x3000: True;  //   U+3000  12288          Ideographic Space

            default    : False;
            };
        }

    /**
     * Determine if the character acts as a line terminator.
     *
     * @return True iff this character acts as an Ecstasy line terminator
     */
    Boolean isLineTerminator()
        {
        // optimize for the ASCII range
        if (codepoint <= 0x7F)
            {
            // this handles the following cases:
            //   U+000A  10  LF   Line Feed
            //   U+000B  11  VT   Vertical Tab
            //   U+000C  12  FF   Form Feed
            //   U+000D  13  CR   Carriage Return
            return 0x0A <= codepoint <= 0x0D;
            }

        // this handles the following cases:
        //   U+0085    133   NEL    Next Line
        //   U+2028   8232   LS     Line Separator
        //   U+2029   8233   PS     Paragraph Separator
        return codepoint == 0x0085 || codepoint == 0x2028 || codepoint == 0x2029;
        }

    /**
     * @return the minimum number of bytes necessary to encode the character in UTF8 format
     */
    Int calcUtf8Length()
        {
        if (codepoint <= 0x7f)
            {
            return 1;
            }

        UInt32 codepoint = this.codepoint >> 11;
        Int    length    = 2;
        while (codepoint != 0)
            {
            codepoint >>= 5;
            ++length;
            }

        return length;
        }

    /**
     * Encode this character into the passed byte array using the UTF8 format.
     *
     * @param bytes  the byte array to write the UTF8 bytes into
     * @param of     the offset into the byte array to write the first byte
     *
     * @return the number of bytes used to encode the character in UTF8 format
     */
    Int formatUtf8(Byte[] bytes, Int of)
        {
        UInt32 cp = codepoint;
        if (cp <= 0x7F)
            {
            // ASCII - single byte 0xxxxxxx format
            bytes[of] = cp.toByte();
            return 1;
            }

        // otherwise the format is based on the number of significant bits:
        // bits  code-points             first byte  trailing  # trailing
        // ----  ----------------------- ----------  --------  ----------
        //  11   U+0080    - U+07FF      110xxxxx    10xxxxxx      1
        //  16   U+0800    - U+FFFF      1110xxxx    10xxxxxx      2
        //  21   U+10000   - U+1FFFFF    11110xxx    10xxxxxx      3
        //  26   U+200000  - U+3FFFFFF   111110xx    10xxxxxx      4
        //  31   U+4000000 - U+7FFFFFFF  1111110x    10xxxxxx      5
        Int trailing;
        switch (cp.leftmostBit)
            {
            case 0b00000000000000000000000010000000:
            case 0b00000000000000000000000100000000:
            case 0b00000000000000000000001000000000:
            case 0b00000000000000000000010000000000:
                bytes[of++] = 0b11000000 | (cp >>> 6).toByte();
                trailing = 1;
                break;

            case 0b00000000000000000000100000000000:
            case 0b00000000000000000001000000000000:
            case 0b00000000000000000010000000000000:
            case 0b00000000000000000100000000000000:
            case 0b00000000000000001000000000000000:
                bytes[of++] = 0b11100000 | (cp >>> 12).toByte();
                trailing = 2;
                break;

            case 0b00000000000000010000000000000000:
            case 0b00000000000000100000000000000000:
            case 0b00000000000001000000000000000000:
            case 0b00000000000010000000000000000000:
            case 0b00000000000100000000000000000000:
                bytes[of++] = 0b11110000 | (cp >>> 18).toByte();
                trailing = 3;
                break;

            case 0b00000000001000000000000000000000:
            case 0b00000000010000000000000000000000:
            case 0b00000000100000000000000000000000:
            case 0b00000001000000000000000000000000:
            case 0b00000010000000000000000000000000:
                bytes[of++] = 0b11111000 | (cp >>> 24).toByte();
                trailing = 4;
                break;

            case 0b00000100000000000000000000000000:
            case 0b00001000000000000000000000000000:
            case 0b00010000000000000000000000000000:
            case 0b00100000000000000000000000000000:
            case 0b01000000000000000000000000000000:
                bytes[of++] = 0b11111100 | (cp >>> 30).toByte();
                trailing = 5;
                break;

            default:
                // TODO: cp.toHexString() would be a better output
                throw new IllegalUTF($"illegal codepoint: {cp}");
            }
        Int length = trailing + 1;

        // write out trailing bytes; each has the same "10xxxxxx" format with 6
        // bits of data
        while (trailing > 0)
            {
            bytes[of++] = 0b10_000000 | (cp >>> --trailing * 6 & 0b00_111111).toByte();
            }

        return length;
        }

    /**
     * Determine if the character needs to be escaped in order to be displayed.
     *
     * @return True iff the character should be escaped in order to be displayed
     * @return (conditional) the number of characters in the escape sequence
     */
    conditional Int isEscaped()
        {
        return switch (codepoint)
            {
            case 0x00           :               // null terminator
            case 0x08           :               // backspace
            case 0x09           :               // horizontal tab
            case 0x0A           :               // line feed
            case 0x0B           :               // vertical tab
            case 0x0C           :               // form feed
            case 0x0D           :               // carriage return
            case 0x1A           :               // EOF
            case 0x1B           :               // escape
            case 0x22           :               // double quotes
            case 0x27           :               // single quotes
            case 0x5C           :               // the escaping slash itself requires an explicit escape
            case 0x7F           : (True, 2);    // DEL

            case 0x00..0x1F     :               // C0 control characters
            case 0x80..0x9F     :               // C1 control characters
            case 0x2028..0x2029 : (True, 5);    // line and paragraph separator

            default             : False;
            };
        }

    /**
     * Append the specified character to the StringBuilder, escaping if
     * necessary.
     *
     * @param buf  the `Appender` to append to
     * @param ch   the character to escape
     *
     * @return the StringBuilder
     */
    Appender<Char!> appendEscaped(Appender<Char!> buf)
        {
        return switch (codepoint)
            {
            case 0x00:
                // null terminator
                buf.add('\\')
                   .add('0');

            case 0x08:
                // backspace
                buf.add('\\')
                   .add('b');

            case 0x09:
                // horizontal tab
                buf.add('\\')
                   .add('t');

            case 0x0A:
                // line feed
                buf.add('\\')
                   .add('n');

            case 0x0B:
                // vertical tab
                buf.add('\\')
                   .add('v');

            case 0x0C:
                // form feed
                buf.add('\\')
                   .add('f');

            case 0x0D:
                // carriage return
                buf.add('\\')
                   .add('r');

            case 0x1A:
                // EOF
                buf.add('\\')
                   .add('z');

            case 0x1B:
                // escape
                buf.add('\\')
                   .add('e');

            case 0x22:
                // double quotes
                buf.add('\\')
                   .add('\"');

            case 0x27:
                // single quotes
                buf.add('\\')
                   .add('\'');

            case 0x5C:
                // the escaping slash itself requires an explicit escape
                buf.add('\\')
                   .add('\\');

            case 0x7F:
               // DEL
                buf.add('\\')
                   .add('d');

            case 0x00..0x1F     :       // C0 control characters
            case 0x80..0x9F     :       // C1 control characters
            case 0x2028..0x2029 :       // line and paragraph separator
                buf.add('\\')
                   .add('u')
                   .add((codepoint & 0xF000 >>> 24).toHexit())
                   .add((codepoint & 0x0F00 >>> 16).toHexit())
                   .add((codepoint & 0x00F0 >>>  8).toHexit())
                   .add((codepoint & 0x000F >>>  0).toHexit());

            default:
                buf.add(this);
            };
        }

    /**
     * @return the character as it would appear in source code, in single quotes and escaped as
     *         necessary
     */
    String quoted()
        {
        if (Int len := isEscaped())
            {
            return appendEscaped(new StringBuffer(len + 2).add('\'')).add('\'').toString();
            }
        else
            {
            return new StringBuffer(3).add('\'').add(this).add('\'').toString();
            }
        }


    // ----- ASCII support -------------------------------------------------------------------------

    /**
     * Determine if the character is in the ASCII range.
     */
    Boolean ascii.get()
        {
        return codepoint <= 0x7F;
        }

    /**
     * Determine if the character is an ASCII digit, one of the values '0'..'9'.
     *
     * @return True iff the character is an ASCII digit
     * @return (conditional) a value in the range `[0..9]`
     */
    conditional UInt8 asciiDigit()
        {
        return switch (this)
            {
            case '0'..'9': (True, (this - '0').toUInt8());
            default      : False;
            };
        }

    /**
     * Determine if the character is an ASCII hexit, one of the values `['0'..'9']`, `['A'..'F']`,
     * or `['a'..'f']`.
     *
     * @return True iff the character is an ASCII hexadecimal digit (a "hexit")
     * @return (conditional) a value in the range `[0..15]`
     */
    conditional UInt8 asciiHexit()
        {
        return switch (this)
            {
            case '0'..'9': (True,       (this - '0').toUInt8());
            case 'A'..'F': (True, 0xA + (this - 'A').toUInt8());
            case 'a'..'f': (True, 0xa + (this - 'a').toUInt8());
            default      : False;
            };
        }

    /**
     * Determine if the character is an ASCII letter, one of the values 'A'..'Z' or 'a'..'z'.
     *
     * @return True iff the character is an ASCII letter
     * @return (conditional) this letter
     */
    conditional Char asciiLetter()
        {
        return switch (this)
            {
            case 'A'..'Z': (True, this);
            case 'a'..'z': (True, this);
            default      : False;
            };
        }

    /**
     * Determine if the character is an ASCII uppercase letter, one of the values 'A'..'Z'.
     *
     * @return True iff the character is an ASCII uppercase letter
     * @return (conditional) this uppercase letter
     */
    conditional Char asciiUppercase()
        {
        return 'A' <= this <= 'Z'
                ? (True, this)
                : False;
        }

    /**
     * Determine if the character is an ASCII lowercase letter, one of the values 'a'..'z'.
     *
     * @return True iff the character is an ASCII lowercase letter
     * @return (conditional) this lowercase letter
     */
    conditional Char asciiLowercase()
        {
        return 'a' <= this <= 'z'
                ? (True, this)
                : False;
        }


    // ----- numeric conversion support ------------------------------------------------------------

    /**
     * Determine if the character represents a nibble value.
     *
     * @return True iff the character represents a nibble value
     * @return (optional) the corresponding Nibble
     */
    conditional Nibble isNibble()
        {
        return switch (this)
            {
            case '0'..'9':
            case 'A'..'F':
            case 'a'..'f': (True, Nibble.of(this));

            default: False;
            };
        }


    // ----- surrogate pair support ----------------------------------------------------------------

    /**
     * Test this character to determine if it is the first part of a surrogate pair.
     *
     * From [the Unicode FAQ](https://unicode.org/faq/utf_bom.html#utf8-4):
     *
     * > Surrogates are code points from two special ranges of Unicode values, reserved for use as
     * > the leading, and trailing values of paired code units in UTF-16. Leading, also called high,
     * > surrogates are from D80016 to DBFF16, and trailing, or low, surrogates are from DC0016 to
     * > DFFF16. They are called surrogates, since they do not represent characters directly, but
     * > only as a pair.
     *
     * @return True if this this Char has a surrogate codepoint, and is a leading (first) value of
     *         a surrogate pair
     *
     * @throws IllegalUTF if this Char has a surrogate codepoint, but is not a valid **leading**
     *                    value for a surrogate pair
     */
    Boolean requiresTrailingSurrogate()
        {
        if (codepoint < 0xD800 || codepoint >= 0xE000)
            {
            return False;
            }

        // for surrogates, the high ten bits (in the range 0x000–0x3FF) are encoded in the range
        // 0xD800–0xDBFF, and the low ten bits (in the range 0x000–0x3FF) are encoded in the range
        // 0xDC00–0xDFFF
        if (codepoint >= 0xDC00)
            {
            throw new IllegalUTF($"leading-surrogate required; trailing-surrogate found: {codepoint}");
            }

        return True;
        }

    /**
     * Combine this leading surrogate with a trailing surrogate to produce a character.
     *
     * From [the Unicode FAQ](https://unicode.org/faq/utf_bom.html#utf8-4):
     *
     * > There is a much simpler computation that does not try to follow the bit distribution table.
     *
     *     // constants
     *     const UTF32 LEAD_OFFSET = 0xD800 - (0x10000 >> 10);
     *     const UTF32 SURROGATE_OFFSET = 0x10000 - (0xD800 << 10) - 0xDC00;
     *
     *     // computations
     *     UTF16 lead = LEAD_OFFSET + (codepoint >> 10);
     *     UTF16 trail = 0xDC00 + (codepoint & 0x3FF);
     *
     *     UTF32 codepoint = (lead << 10) + trail + SURROGATE_OFFSET;
     *
     * And:
     *
     * > Finally, the reverse, where hi and lo are the high and low surrogate, and C the resulting
     * > character
     *
     *     UTF32 X = (hi & ((1 << 6) -1)) << 10 | lo & ((1 << 10) -1);
     *     UTF32 W = (hi >> 6) & ((1 << 5) - 1);
     *     UTF32 U = W + 1;
     *
     *     UTF32 C = U << 16 | X;
     *
     * @param trailing  the trailing portion of the surrogate pair
     *
     * @return the resulting Char that the surrogate pair represents
     *
     * @throws IllegalUTF if `this` Char is not a leading surrogate, or the `trailing` Char is not a
     *                    trailing surrogate
     */
    Char addTrailingSurrogate(Char trailing)
        {
        UInt32 hi = this.codepoint;
        if (hi < 0xD800 || hi >= 0xDC00)
            {
            throw new IllegalUTF($"illegal leading-surrogate: {hi}");
            }

        UInt32 lo = trailing.codepoint;
        if (lo < 0xDC00 || codepoint >= 0xE000)
            {
            throw new IllegalUTF($"illegal trailing-surrogate: {lo}");
            }

        static @Unchecked UInt32 SURROGATE_OFFSET = 0x10000 - (0xD800 << 10) - 0xDC00;
        return new Char((hi << 10) + lo + SURROGATE_OFFSET);
        }


    // ----- Unicode support -----------------------------------------------------------------------

    /**
     * True iff the codepoint is a defined Unicode character.
     */
    Boolean unicode.get()
        {
        return category != Unassigned;
        }

    /**
     * The Unicode General Category of the character.
     *
     * This information is field 2 in the `UnicodeData.txt` data file from the Unicode Consortium.
     * From [https://www.unicode.org/reports/tr44/#General_Category_Values]:
     *
     * > This is a useful breakdown into various character types which can be used as a default
     * > categorization in implementations. For the property values, see
     * > [General Category Values](https://www.unicode.org/reports/tr44/#General_Category_Values).
     *
     * This information is stored in the binary file "CharCats.dat" in this package. For a codepoint
     * `n`, the n-th byte of the file is the ordinal of the `Category` enum value for the character.
     */
    Category category.get()
        {
        static List<Int> categoriesByCodepoint = new ConstOrdinalList(#./CharCats.dat);

        return codepoint < categoriesByCodepoint.size
                ? Category.values[categoriesByCodepoint[codepoint]]
                : Unassigned;
        }

    /**
     * Unicode "General Categories".
     */
    enum Category(String code, String description)
        {
        UppercaseLetter     ("Lu", "An uppercase letter"),
        LowercaseLetter     ("Ll", "A lowercase letter"),
        TitlecaseLetter     ("Lt", "A digraphic character, with first part uppercase"),
        ModifierLetter      ("Lm", "A modifier letter"),
        OtherLetter         ("Lo", "Other letters, including syllables and ideographs"),
        NonspacingMark      ("Mn", "A nonspacing combining mark (zero advance width)"),
        SpacingMark         ("Mc", "A spacing combining mark (positive advance width)"),
        EnclosingMark       ("Me", "An enclosing combining mark"),
        DecimalNumber       ("Nd", "A decimal digit"),
        LetterNumber        ("Nl", "A letterlike numeric character"),
        OtherNumber         ("No", "A numeric character of other type"),
        ConnectorPunctuation("Pc", "A connecting punctuation mark, like a tie"),
        DashPunctuation     ("Pd", "A dash or hyphen punctuation mark"),
        OpenPunctuation     ("Ps", "An opening punctuation mark (of a pair)"),
        ClosePunctuation    ("Pe", "A closing punctuation mark (of a pair)"),
        InitialPunctuation  ("Pi", "An initial quotation mark"),
        FinalPunctuation    ("Pf", "A final quotation mark"),
        OtherPunctuation    ("Po", "A punctuation mark of other type"),
        MathSymbol          ("Sm", "A symbol of mathematical use"),
        CurrencySymbol      ("Sc", "A currency sign"),
        ModifierSymbol      ("Sk", "A non-letterlike modifier symbol"),
        OtherSymbol         ("So", "A symbol of other type"),
        SpaceSeparator      ("Zs", "A space character (of various non-zero widths)"),
        LineSeparator       ("Zl", "U+2028 LINE SEPARATOR only"),
        ParagraphSeparator  ("Zp", "U+2029 PARAGRAPH SEPARATOR only"),
        Control             ("Cc", "A C0 or C1 control code"),
        Format              ("Cf", "A format control character"),
        Surrogate           ("Cs", "A surrogate code point"),
        PrivateUse          ("Co", "A private-use character"),
        Unassigned          ("Cn", "A reserved unassigned code point or a noncharacter");

        Boolean casedLetter;
        Boolean letter;
        Boolean mark;
        Boolean number;
        Boolean punctuation;
        Boolean symbol;
        Boolean separator;
        Boolean other;

        construct (String code, String description)
            {
            this.code        = code;
            this.description = description;

            letter      = code[0] == 'L';
            mark        = code[0] == 'M';
            number      = code[0] == 'N';
            punctuation = code[0] == 'P';
            symbol      = code[0] == 'S';
            separator   = code[0] == 'Z';
            other       = code[0] == 'C';

            casedLetter = letter && (code == "Lu" || code == "Ll" || code == "Lt");
            }
        }

    /**
     * The value in the range `[0..9]` that represents the decimal value of this character.
     *
     * > If the character has the property value Numeric_Type=Decimal, then the Numeric_Value of
     * > that digit is represented with an integer value (limited to the range 0..9) in fields 6, 7,
     * > and 8. Characters with the property value Numeric_Type=Decimal are restricted to digits
     * > which can be used in a decimal radix positional numeral system and which are encoded in the
     * > standard in a contiguous ascending range 0..9. See the discussion of decimal digits in
     * > Chapter 4, Character Properties in
     * > [Unicode](https://www.unicode.org/reports/tr41/tr41-26.html#Unicode).
     */
    conditional Int decimalValue()
        {
        static List<Int> decsByCodepoint = new ConstOrdinalList(#./CharDecs.dat);

        if (codepoint < decsByCodepoint.size)
            {
            Int val = decsByCodepoint[codepoint];
            if (val < 10)
                {
                return True, val;
                }
            }

        return False;
        }

    /**
     * The numeric value of this character, represented as a `String`, and potentially represented
     * using a fractional notation of an integer value followed by `/` followed by a second integer
     * value.
     *
     * > If the character has the property value Numeric_Type=Numeric, then the Numeric_Value of
     * > that character is represented with a positive or negative integer or rational number in
     * > this field, and fields 6 and 7 are null. This includes fractions such as, for example,
     * > "1/5" for U+2155 VULGAR FRACTION ONE FIFTH.
     */
    String? numericValue.get()
        {
        static List<Int> numsByCodepoint = new ConstOrdinalList(#./CharNums.dat);
        if (codepoint >= numsByCodepoint.size)
            {
            return Null;
            }

        static String[] numStrings =
            [
            "-1/2",
            "0",
            "1",
            "1/10",
            "1/12",
            "1/16",
            "1/160",
            "1/2",
            "1/20",
            "1/3",
            "1/32",
            "1/320",
            "1/4",
            "1/40",
            "1/5",
            "1/6",
            "1/64",
            "1/7",
            "1/8",
            "1/80",
            "1/9",
            "10",
            "10/12",
            "100",
            "1000",
            "10000",
            "100000",
            "1000000",
            "10000000",
            "100000000",
            "10000000000",
            "1000000000000",
            "11",
            "11/12",
            "11/2",
            "12",
            "13",
            "13/2",
            "14",
            "15",
            "15/2",
            "16",
            "17",
            "17/2",
            "18",
            "19",
            "2",
            "2/12",
            "2/3",
            "2/5",
            "20",
            "200",
            "2000",
            "20000",
            "200000",
            "20000000",
            "21",
            "216000",
            "22",
            "23",
            "24",
            "25",
            "26",
            "27",
            "28",
            "29",
            "3",
            "3/12",
            "3/16",
            "3/2",
            "3/20",
            "3/4",
            "3/5",
            "3/64",
            "3/8",
            "3/80",
            "30",
            "300",
            "3000",
            "30000",
            "300000",
            "31",
            "32",
            "33",
            "34",
            "35",
            "36",
            "37",
            "38",
            "39",
            "4",
            "4/12",
            "4/5",
            "40",
            "400",
            "4000",
            "40000",
            "400000",
            "41",
            "42",
            "43",
            "432000",
            "44",
            "45",
            "46",
            "47",
            "48",
            "49",
            "5",
            "5/12",
            "5/2",
            "5/6",
            "5/8",
            "50",
            "500",
            "5000",
            "50000",
            "500000",
            "6",
            "6/12",
            "60",
            "600",
            "6000",
            "60000",
            "600000",
            "7",
            "7/12",
            "7/2",
            "7/8",
            "70",
            "700",
            "7000",
            "70000",
            "700000",
            "8",
            "8/12",
            "80",
            "800",
            "8000",
            "80000",
            "800000",
            "9",
            "9/12",
            "9/2",
            "90",
            "900",
            "9000",
            "90000",
            "900000",
            ];

        Int index = numsByCodepoint[codepoint];
        return index >= numStrings.size
                ? Null
                : numStrings[index];
        }

    /**
     * For characters that have a Unicode lowercase form, this property provides that form;
     * otherwise, this property returns `this`.
     */
    Char lowercase.get()
        {
        static List<Int> lowersByCodepoint = new ConstOrdinalList(#./CharLowers.dat);

        if (codepoint >= lowersByCodepoint.size)
            {
            return this;
            }

        Int lower = lowersByCodepoint[codepoint];
        return lower == 0
                ? this
                : new Char(lower);
        }

    /**
     * For characters that have a Unicode uppercase form, this property provides that form;
     * otherwise, this property returns `this`.
     */
    Char uppercase.get()
        {
        static List<Int> uppersByCodepoint = new ConstOrdinalList(#./CharUppers.dat);

        if (codepoint >= uppersByCodepoint.size)
            {
            return this;
            }

        Int upper = uppersByCodepoint[codepoint];
        return upper == 0
                ? this
                : new Char(upper);
        }

    /**
     * For characters that have a Unicode titlecase form, this property provides that form;
     * otherwise, this property returns `this`.
     */
    Char titlecase.get()
        {
        static List<Int> titlesByCodepoint = new ConstOrdinalList(#./CharTitles.dat);

        if (codepoint >= titlesByCodepoint.size)
            {
            return this;
            }

        Int title = titlesByCodepoint[codepoint];
        return title == 0
                ? this
                : new Char(title);
        }

    /**
     * The Unicode Block name for the range of codepoints containing this character, or `Null` if
     * the codepoint for this character does not belong to a Unicode block.
     */
    String? blockName.get()
        {
        static List<Int> blocksByCodepoint = new ConstOrdinalList(#./CharBlocks.dat);
        if (codepoint >= blocksByCodepoint.size)
            {
            return Null;
            }

        static String[] blockNames =
            [
            "ASCII",
            "Adlam",
            "Aegean_Numbers",
            "Ahom",
            "Alchemical",
            "Alphabetic_PF",
            "Anatolian_Hieroglyphs",
            "Ancient_Greek_Music",
            "Ancient_Greek_Numbers",
            "Ancient_Symbols",
            "Arabic",
            "Arabic_Ext_A",
            "Arabic_Math",
            "Arabic_PF_A",
            "Arabic_PF_B",
            "Arabic_Sup",
            "Armenian",
            "Arrows",
            "Avestan",
            "Balinese",
            "Bamum",
            "Bamum_Sup",
            "Bassa_Vah",
            "Batak",
            "Bengali",
            "Bhaiksuki",
            "Block_Elements",
            "Bopomofo",
            "Bopomofo_Ext",
            "Box_Drawing",
            "Brahmi",
            "Braille",
            "Buginese",
            "Buhid",
            "Byzantine_Music",
            "CJK",
            "CJK_Compat",
            "CJK_Compat_Forms",
            "CJK_Compat_Ideographs",
            "CJK_Compat_Ideographs_Sup",
            "CJK_Ext_A",
            "CJK_Ext_B",
            "CJK_Ext_C",
            "CJK_Ext_D",
            "CJK_Ext_E",
            "CJK_Ext_F",
            "CJK_Ext_G",
            "CJK_Radicals_Sup",
            "CJK_Strokes",
            "CJK_Symbols",
            "Carian",
            "Caucasian_Albanian",
            "Chakma",
            "Cham",
            "Cherokee",
            "Cherokee_Sup",
            "Chess_Symbols",
            "Chorasmian",
            "Compat_Jamo",
            "Control_Pictures",
            "Coptic",
            "Coptic_Epact_Numbers",
            "Counting_Rod",
            "Cuneiform",
            "Cuneiform_Numbers",
            "Currency_Symbols",
            "Cypriot_Syllabary",
            "Cyrillic",
            "Cyrillic_Ext_A",
            "Cyrillic_Ext_B",
            "Cyrillic_Ext_C",
            "Cyrillic_Sup",
            "Deseret",
            "Devanagari",
            "Devanagari_Ext",
            "Diacriticals",
            "Diacriticals_Ext",
            "Diacriticals_For_Symbols",
            "Diacriticals_Sup",
            "Dingbats",
            "Dives_Akuru",
            "Dogra",
            "Domino",
            "Duployan",
            "Early_Dynastic_Cuneiform",
            "Egyptian_Hieroglyph_Format_Controls",
            "Egyptian_Hieroglyphs",
            "Elbasan",
            "Elymaic",
            "Emoticons",
            "Enclosed_Alphanum",
            "Enclosed_Alphanum_Sup",
            "Enclosed_CJK",
            "Enclosed_Ideographic_Sup",
            "Ethiopic",
            "Ethiopic_Ext",
            "Ethiopic_Ext_A",
            "Ethiopic_Sup",
            "Geometric_Shapes",
            "Geometric_Shapes_Ext",
            "Georgian",
            "Georgian_Ext",
            "Georgian_Sup",
            "Glagolitic",
            "Glagolitic_Sup",
            "Gothic",
            "Grantha",
            "Greek",
            "Greek_Ext",
            "Gujarati",
            "Gunjala_Gondi",
            "Gurmukhi",
            "Half_And_Full_Forms",
            "Half_Marks",
            "Hangul",
            "Hanifi_Rohingya",
            "Hanunoo",
            "Hatran",
            "Hebrew",
            "High_PU_Surrogates",
            "High_Surrogates",
            "Hiragana",
            "IDC",
            "IPA_Ext",
            "Ideographic_Symbols",
            "Imperial_Aramaic",
            "Indic_Number_Forms",
            "Indic_Siyaq_Numbers",
            "Inscriptional_Pahlavi",
            "Inscriptional_Parthian",
            "Jamo",
            "Jamo_Ext_A",
            "Jamo_Ext_B",
            "Javanese",
            "Kaithi",
            "Kana_Ext_A",
            "Kana_Sup",
            "Kanbun",
            "Kangxi",
            "Kannada",
            "Katakana",
            "Katakana_Ext",
            "Kayah_Li",
            "Kharoshthi",
            "Khitan_Small_Script",
            "Khmer",
            "Khmer_Symbols",
            "Khojki",
            "Khudawadi",
            "Lao",
            "Latin_1_Sup",
            "Latin_Ext_A",
            "Latin_Ext_Additional",
            "Latin_Ext_B",
            "Latin_Ext_C",
            "Latin_Ext_D",
            "Latin_Ext_E",
            "Lepcha",
            "Letterlike_Symbols",
            "Limbu",
            "Linear_A",
            "Linear_B_Ideograms",
            "Linear_B_Syllabary",
            "Lisu",
            "Lisu_Sup",
            "Low_Surrogates",
            "Lycian",
            "Lydian",
            "Mahajani",
            "Mahjong",
            "Makasar",
            "Malayalam",
            "Mandaic",
            "Manichaean",
            "Marchen",
            "Masaram_Gondi",
            "Math_Alphanum",
            "Math_Operators",
            "Mayan_Numerals",
            "Medefaidrin",
            "Meetei_Mayek",
            "Meetei_Mayek_Ext",
            "Mende_Kikakui",
            "Meroitic_Cursive",
            "Meroitic_Hieroglyphs",
            "Miao",
            "Misc_Arrows",
            "Misc_Math_Symbols_A",
            "Misc_Math_Symbols_B",
            "Misc_Pictographs",
            "Misc_Symbols",
            "Misc_Technical",
            "Modi",
            "Modifier_Letters",
            "Modifier_Tone_Letters",
            "Mongolian",
            "Mongolian_Sup",
            "Mro",
            "Multani",
            "Music",
            "Myanmar",
            "Myanmar_Ext_A",
            "Myanmar_Ext_B",
            "NB",
            "NKo",
            "Nabataean",
            "Nandinagari",
            "New_Tai_Lue",
            "Newa",
            "Number_Forms",
            "Nushu",
            "Nyiakeng_Puachue_Hmong",
            "OCR",
            "Ogham",
            "Ol_Chiki",
            "Old_Hungarian",
            "Old_Italic",
            "Old_North_Arabian",
            "Old_Permic",
            "Old_Persian",
            "Old_Sogdian",
            "Old_South_Arabian",
            "Old_Turkic",
            "Oriya",
            "Ornamental_Dingbats",
            "Osage",
            "Osmanya",
            "Ottoman_Siyaq_Numbers",
            "PUA",
            "Pahawh_Hmong",
            "Palmyrene",
            "Pau_Cin_Hau",
            "Phags_Pa",
            "Phaistos",
            "Phoenician",
            "Phonetic_Ext",
            "Phonetic_Ext_Sup",
            "Playing_Cards",
            "Psalter_Pahlavi",
            "Punctuation",
            "Rejang",
            "Rumi",
            "Runic",
            "Samaritan",
            "Saurashtra",
            "Sharada",
            "Shavian",
            "Shorthand_Format_Controls",
            "Siddham",
            "Sinhala",
            "Sinhala_Archaic_Numbers",
            "Small_Forms",
            "Small_Kana_Ext",
            "Sogdian",
            "Sora_Sompeng",
            "Soyombo",
            "Specials",
            "Sundanese",
            "Sundanese_Sup",
            "Sup_Arrows_A",
            "Sup_Arrows_B",
            "Sup_Arrows_C",
            "Sup_Math_Operators",
            "Sup_PUA_A",
            "Sup_PUA_B",
            "Sup_Punctuation",
            "Sup_Symbols_And_Pictographs",
            "Super_And_Sub",
            "Sutton_SignWriting",
            "Syloti_Nagri",
            "Symbols_And_Pictographs_Ext_A",
            "Symbols_For_Legacy_Computing",
            "Syriac",
            "Syriac_Sup",
            "Tagalog",
            "Tagbanwa",
            "Tags",
            "Tai_Le",
            "Tai_Tham",
            "Tai_Viet",
            "Tai_Xuan_Jing",
            "Takri",
            "Tamil",
            "Tamil_Sup",
            "Tangut",
            "Tangut_Components",
            "Tangut_Sup",
            "Telugu",
            "Thaana",
            "Thai",
            "Tibetan",
            "Tifinagh",
            "Tirhuta",
            "Transport_And_Map",
            "UCAS",
            "UCAS_Ext",
            "Ugaritic",
            "VS",
            "VS_Sup",
            "Vai",
            "Vedic_Ext",
            "Vertical_Forms",
            "Wancho",
            "Warang_Citi",
            "Yezidi",
            "Yi_Radicals",
            "Yi_Syllables",
            "Yijing",
            "Zanabazar_Square",
            ];

        Int index = blocksByCodepoint[codepoint];
        return index >= blockNames.size
                ? Null
                : blockNames[index];
        }

    /**
     * The Unicode canonical combining class for the character's codepoint.
     *
     * > The classes used for the Canonical Ordering Algorithm in the Unicode Standard. This
     * > property could be considered either an enumerated property or a numeric property: the
     * > principal use of the property is in terms of the numeric values. For the property value
     * > names associated with different numeric values, see DerivedCombiningClass.txt and Canonical
     * > Combining Class Values.
     */
    Int? unicodeCanonicalCombiningClass.get()
        {
        TODO CharCombineClass.dat
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 1;
        }

    @Override
    Appender<Char!> appendTo(Appender<Char!> buf)
        {
        return buf.add(this);
        }
    }
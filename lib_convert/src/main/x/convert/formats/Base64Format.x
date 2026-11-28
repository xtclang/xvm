/**
 * A `Format` that encodes bytes as Base64 text and decodes Base64 text into bytes.
 *
 * Set `urlSafe` to use the Base64URL alphabet, with `-` and `_` for values 62 and 63.
 * The `pad` and `lineLength` options control padding and line wrapping for either alphabet.
 *
 * @see [RFC 4648 §4 for Base64](https://www.rfc-editor.org/rfc/rfc4648.html#section-4)
 * @see [RFC 4648 §5 for Base64URL](https://www.rfc-editor.org/rfc/rfc4648.html#section-5)
 * @see [RFC 7515 §2 for JWS](https://www.rfc-editor.org/rfc/rfc7515.html#section-2)
 */
const Base64Format(Boolean pad = False, Int? lineLength = Null, Boolean urlSafe = False)
        implements Format<Byte[]> {

    @Override
    String name.get() {
        String alphabet = urlSafe ? "Base64URL" : "Base64";
        return $"{alphabet}{pad ? "-padded" : ""}{lineLength == Null ? "" : $"({lineLength})"}";
    }

    /**
     * Default Base64 Format instance: Unpadded, no line length limit.
     */
    static Base64Format Instance = new Base64Format();

    @Override
    Value read(Iterator<Char> stream) {
        Int    charLen    = stream.knownSize() ?: 0;
        Byte[] byteBuf    = new Byte[](charLen * 6 / 8);
        Byte   prevBits   = 0;
        Int    prevCount  = 0;
        while (Char ch := stream.next()) {
            if (Byte newBits := isBase64(ch, assertTrash=True, urlSafe=urlSafe)) {
                if (prevCount == 0) {
                    prevBits  = newBits;
                    prevCount = 6;
                } else {
                    byteBuf.add((prevBits << 8-prevCount) | (newBits >> prevCount-2));
                    prevBits   = newBits;
                    prevCount -= 2;
                }
            }
        }

        return byteBuf.freeze(inPlace=True);
    }

    @Override
    Value decode(String text) {
        Int    charLen    = text.size;
        Byte[] byteBuf    = new Byte[](charLen * 6 / 8);
        Byte   prevBits   = 0;
        Int    prevCount  = 0;
        for (Int offset = 0; offset < charLen; ++offset) {
            if (Byte newBits := isBase64(text[offset], assertTrash=True, urlSafe=urlSafe)) {
                if (prevCount == 0) {
                    prevBits  = newBits;
                    prevCount = 6;
                } else {
                    byteBuf.add((prevBits << 8-prevCount) | (newBits >> prevCount-2));
                    prevBits   = newBits;
                    prevCount -= 2;
                }
            }
        }

        return byteBuf.freeze(inPlace=True);
    }

    @Override
    void write(Value value, Appender<Char> stream, Boolean? pad = Null, Int? lineLength = Null) {
        pad        ?:= this.pad;
        lineLength ?:= this.lineLength ?: MaxValue;

        Int    lineOffset = 0;
        Int    totalChars = 0;
        Byte   prevByte   = 0;
        Int    prevCount  = 0;          // number of leftover bits from the previous byte
        Int    byteOffset = 0;
        Int    byteLength = value.size;
        while (True) {
            // glue together the next six bits, which will create one character of output
            Byte sixBits;
            if (byteOffset >= byteLength) {
                if (prevCount == 0) {
                    break;
                }
                sixBits   = prevByte << 6 - prevCount;
                prevCount = 0;
            } else if (prevCount == 6) {
                sixBits   = prevByte << 6 - prevCount;
                prevCount = 0;
            } else {
                Byte nextByte = value[byteOffset++];
                sixBits    = (prevByte << 6 - prevCount) | (nextByte >> 2 + prevCount);
                prevByte   = nextByte;
                prevCount += 2;
            }

            if (lineOffset >= lineLength) {
                stream.add('\r').add('\n');
                totalChars += lineOffset;
                lineOffset  = 0;
            }

            stream.add(base64(sixBits & 0b111111, urlSafe));
            ++lineOffset;
        }

        if (pad) {
            totalChars += lineOffset;
            for (Int i = 0, Int padCount = 4 - (totalChars & 0b11) & 0b11; i < padCount; ++i) {
                if (lineOffset >= lineLength) {
                    stream.add('\r').add('\n');
                    lineOffset  = 0;
                }

                stream.add('=');
                ++lineOffset;
            }
        }
    }

    @Override
    String encode(Value value, Boolean? pad = Null, Int? lineLength = Null) {
        pad        ?:= this.pad;
        lineLength ?:= this.lineLength;

        // calculate buffer size
        Int byteLen = value.size;
        Int charLen = (byteLen * 8 + 5) / 6;
        if (pad) {
            charLen += 4 - (charLen & 0b11) & 0b11;
        }
        if (lineLength != Null) {
            charLen += ((charLen + lineLength - 1) / lineLength - 1).notLessThan(0) * 2;
        }

        StringBuffer charBuf = new StringBuffer(charLen);
        write(value, charBuf, pad, lineLength);
        return charBuf.toString();
    }

    /**
     * Translate a single Base64 character to the least significant 6 bits of a `Byte` value.
     *
     * @param ch       the Base64 character; no pad or newlines allowed
     * @param urlSafe  (optional) use the Base64URL alphabet
     *
     * @return the value in the range `0 ..< 64`
     */
    static Byte valOf(Char ch, Boolean urlSafe = False) {
        if (Byte value := isBase64(ch, assertTrash=True, urlSafe=urlSafe)) {
            return value;
        }

        assert ch != '=' as $"Unexpected padding character in Base64: {ch.quoted()}";
        assert as $"Unexpected newline character in Base64: {ch.quoted()}";
    }

    /**
     * Translate a single Base64 character to the least significant 6 bits of a `Byte` value.
     *
     * @param ch           the character to test if it is Base64
     * @param assertTrash  (optional) pass True to assert on illegal Base64 characters
     * @param urlSafe      (optional) use the Base64URL alphabet
     *
     * @return the value in the range `0 ..< 64`
     */
    static conditional Byte isBase64(Char ch, Boolean assertTrash = False, Boolean urlSafe = False) {
        switch (ch) {
        case 'A'..'Z': return True, (ch - 'A').toUInt8();
        case 'a'..'z': return True, (ch - 'a').toUInt8() + 26;
        case '0'..'9': return True, (ch - '0').toUInt8() + 52;
        case '+', '-':
            if (ch == (urlSafe ? '-' : '+')) {
                return True, 62;
            }
            break;
        case '/', '_':
            if (ch == (urlSafe ? '_' : '/')) {
                return True, 63;
            }
            break;

        case '=':           // "pad" sometimes allowed (or required) at end
        case '\r', '\n':    // newlines sometimes allowed (or required)
            return False;
        }

        return assertTrash ? assert as $"Invalid Base64 character: {ch.quoted()}" : False;
    }

    /**
     * Convert the passed byte value to a Base64 character.
     *
     * @param byte     the byte value, which must be in the range `0..63`
     * @param urlSafe  (optional) use the Base64URL alphabet
     *
     * @return the Base64 character
     */
    static Char base64(Byte byte, Boolean urlSafe = False) {
        return switch (byte) {
            case  0 ..< 26: 'A'+byte;
            case 26 ..< 52: 'a'+(byte-26);
            case 52 ..< 62: '0'+(byte-52);
            case 62: urlSafe ? '-' : '+';
            case 63: urlSafe ? '_' : '/';
            default: assert:bounds as $"byte={byte}";
        };
    }
}

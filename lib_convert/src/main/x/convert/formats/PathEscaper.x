/**
 * Handles the String-to-String conversion of the path portion of a URI.
 */
const PathEscaper(Boolean singleSegment)
        implements Format<String> {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * True if the content is tied to a single path segment, i.e. cannot contain a slash.
     */
    Boolean singleSegment;


    // ----- Format interface ----------------------------------------------------------------------

    @Override
    String name = "URI-path";

    @Override
    Value read(Iterator<Char> stream) {
        Int presize = 0;
        presize := stream.knownSize();
        StringBuffer buf = new StringBuffer(presize);

        while (Char ch := stream.next()) {
            if (ch == '%') {
                ch = readEscapedChar(stream);

                // escaped '/' or '.' needs to stay escaped, because the URI specification defines
                // special meanings for those characters
                if (ch == '/' || ch == '.') {
                    writeEscapedChar(buf, ch);
                    continue;
                }

                UInt32 codepoint = ch.codepoint;
                if (codepoint > 127) {
                    // decode a Unicode UTF-8 sequence; the number of leading one bits tells us how
                    // long the UTF-8 sequence must be
                    Int size = (~codepoint).leftmostBit.leadingZeroCount;
                    assert 2 <= size <= 6 as "Invalid UTF-8 sequence";

                    Byte[] utf8 = new Byte[](size);
                    utf8[0] = ch.toUInt8();
                    for (Int i : 1 ..< size) {
                        assert ch := stream.next(), ch == '%' as "Invalid UTF-8 sequence";
                        ch = readEscapedChar(stream);
                        utf8[i] = ch.toUInt8();
                    }
                    ch = new Char(utf8);
                }
            }

            buf.add(ch);
        }

        return buf.toString();
    }

    @Override
    Value decode(String text) {
        // quick scan the input string since it usually won't need any changes at all
        return text.chars.all(isUnescapedPathChar)
                ? text
                : super(text);
    }

    @Override
    void write(Value value, Appender<Char> stream) {
        for (Int offset = 0, Int length = value.size; offset < length; ++offset) {
            Char ch = value[offset];
            if (isUnescapedPathChar(ch) || (ch == '/' && !singleSegment)) {
                stream.add(ch);
            } else {
                // there is one exception to the escaping rule, which is when the value contains
                // a character escape that unescapes to '/' or '.' (because we do not unescape
                // those)
                if (ch == '%' && offset + 2 < length && value[offset+1] == '2') {
                    Char ch2 = value[offset+2];
                    if (ch2 == 'E' || ch2 == 'F') {
                        stream.add('%')
                              .add('2')
                              .add(ch2);
                        offset += 2;
                        continue;
                    }
                }

                writeEscapedChar(stream, ch);
            }
        }
    }

    @Override
    String encode(Value value) {
        // quick scan the input string since it usually won't need any changes at all
        return value.chars.all(isUnescapedPathChar)
                ? value
                : super(value);
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Read the next two characters from the string, which must both be ASCII hexit characters, and
     * convert them to a single character in the codepoint range `0..255`.
     *
     * @param stream  the stream of characters to read from
     *
     * @return a character in the codepoint range `0..255`
     */
    static Char readEscapedChar(Iterator<Char> stream) {
        assert Char ch1 := stream.next(),
               Char ch2 := stream.next(),
               UInt8 n1 := ch1.asciiHexit(),
               UInt8 n2 := ch1.asciiHexit() as "Invalid '%' escape sequence in path";
        return new Char(n1 * 16 + n2);
    }

    /**
     * Write an ASCII character to the string as an escape sequence `%nn`, or a Unicode character
     * outside of the ASCII range as a sequence of escape sequences each representing one byte of
     * the UTF-8 encoding.
     *
     * @param stream  the stream of characters write to
     * @param ch      the character to write
     *
     * @return the stream
     */
    static Appender<Char> writeEscapedChar(Appender<Char> stream, Char ch) {
        UInt32 n = ch.codepoint;
        if (n <= 127) {
            stream.add('%')
                  .add((n >>> 4).toHexit())
                  .add(n.toHexit());
        } else {
            for (Byte b : ch.utf8()) {
                stream.add('%')
                      .add((b >>> 4).toHexit())
                      .add(b.toHexit());
            }
        }

        return stream;
    }

    /**
     * Determine if the specified character can be represented as-is in the path string.
     *
     * @param ch  the character to test
     *
     * @return True to use the character as-is; False to escape the character
     */
    static Boolean isUnescapedPathChar(Char ch) {
        return switch (ch) {
            case '/':
            case 'A'..'Z', 'a'..'z', '0'..'9', '-', '.', '_', '~':
            case '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=':
            case ':', '@':
                True;

            default:
                False;
        };
    }
}

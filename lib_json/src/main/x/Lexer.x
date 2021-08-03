import ecstasy.Markable;

import ecstasy.io.EndOfFile;
import ecstasy.io.IOException;
import ecstasy.io.TextPosition;

/**
 * A lexer for a JSON document.
 *
 * The JSON format is described at https://www.json.org as follows:
 *
 *     json
 *         element
 *
 *     value
 *         object
 *         array
 *         string
 *         number
 *         "true"
 *         "false"
 *         "null"
 *
 *     object
 *         '{' ws '}'
 *         '{' members '}'
 *
 *     members
 *         member
 *         member ',' members
 *
 *     member
 *         ws string ws ':' element
 *
 *     array
 *         '[' ws ']'
 *         '[' elements ']'
 *
 *     elements
 *         element
 *         element ',' elements
 *
 *     element
 *         ws value ws
 *
 *     string
 *         '"' characters '"'
 *
 *     characters
 *         ""
 *         character characters
 *
 *     character
 *         '0020' . '10ffff' - '"' - '\'
 *         '\' escape
 *
 *     escape
 *         '"'
 *         '\'
 *         '/'
 *         'b'
 *         'f'
 *         'n'
 *         'r'
 *         't'
 *         'u' hex hex hex hex
 *
 *     hex
 *         digit
 *         'A' . 'F'
 *         'a' . 'f'
 *
 *     number
 *         integer fraction exponent
 *
 *     integer
 *         digit
 *         onenine digits
 *         '-' digit
 *         '-' onenine digits
 *
 *     digits
 *         digit
 *         digit digits
 *
 *     digit
 *         '0'
 *         onenine
 *
 *     onenine
 *         '1' . '9'
 *
 *     fraction
 *         ""
 *         '.' digits
 *
 *     exponent
 *         ""
 *         'E' sign digits
 *         'e' sign digits
 *
 *     sign
 *         ""
 *         '+'
 *         '-'
 *
 *     ws
 *         ""
 *         '0020' ws
 *         '000D' ws
 *         '000A' ws
 *         '0009' ws
 */
class Lexer
        implements Iterator<Token>
        implements Markable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a JSON lexical analyzer that processes a document from a Reader.
     *
     * @param reader  the source of the JSON document text
     */
    construct(Reader reader)
        {
        this.reader = reader;
        }
    finally
        {
        eatWhitespace();
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying Reader.
     */
    protected/private Reader reader;


    // ----- types ---------------------------------------------------------------------------------

    /**
     * JSON is composed of these lexical elements.
     */
    enum Id
        {
        NoVal, BoolVal, IntVal, FPVal, StrVal,
        ArrayEnter, ArrayExit,
        ObjectEnter, ObjectExit,
        Colon, Comma
        }

    /**
     * A JSON token has a lexical identity (what the element type is), a location in the text stream
     * (with the ending position being the position _after_ the token), and an optional value.
     */
    static const Token(Id id, TextPosition start, TextPosition end, Primitive value = Null)
        {
        @Override
        Int estimateStringLength()
            {
            return 5
                 + start.lineNumber.estimateStringLength()
                 + start.lineOffset.estimateStringLength()
                 + switch (id)
                    {
                    case NoVal..FPVal: value.estimateStringLength();
                    case StrVal:       value.estimateStringLength() + 2;
                    default:           3;
                    };
            }

        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            buf.add('(');
            start.lineNumber.appendTo(buf);
            buf.add(':');
            start.lineOffset.appendTo(buf);
            "): ".appendTo(buf);
            switch (id)
                {
                case NoVal..FPVal:
                    value.appendTo(buf);
                    break;
                case StrVal:
                    buf.add('\"');
                    value.appendTo(buf);
                    buf.add('\"');
                    break;
                default:
                    buf.add('\'')
                       .add(switch (id)
                        {
                        case ArrayEnter:  '[';
                        case ArrayExit:   ']';
                        case ObjectEnter: '{';
                        case ObjectExit:  '}';
                        case Colon:       ':';
                        case Comma:       ',';
                        default:          assert;
                        })
                       .add('\'');
                    break;
                }

            return buf;
            }
        }


    // ----- Iterator ------------------------------------------------------------------------------

    @Override
    conditional Token next()
        {
        if (reader.eof)
            {
            return False;
            }

        return True, eatToken();
        }


    // ----- Markable ------------------------------------------------------------------------------

    @Override
    Object mark()
        {
        return reader.position;
        }

    @Override
    void restore(Object mark, Boolean unmark = False)
        {
        assert mark.is(TextPosition);
        reader.position = mark;

        if (unmark)
            {
            this.unmark(mark);
            }
        }

    @Override
    void unmark(Object mark)
        {
        }


    // ----- operations ----------------------------------------------------------------------------

    /**
     * Skip over any white space.
     *
     * Whitespace in a JSON document is defined as:
     *
     *     ws
     *         ""
     *         '0020' ws
     *         '000D' ws
     *         '000A' ws
     *         '0009' ws
     */
    protected void eatWhitespace()
        {
        while (Char ch := reader.next())
            {
            switch (ch)
                {
                case ' ':
                case '\r':
                case '\n':
                case '\t':
                case '\0': // TODO GG - started getting trailing \0 in json output files (or when reading them in? Not sure ...)
                    break;

                default:
                    reader.rewind();
                    return;
                }
            }
        }

    /**
     * Eat the next token from the text. The whitespace must have already been removed.
     *
     * @return the next token
     */
    protected Token eatToken()
        {
        Id           id;
        Primitive    val   = Null;
        TextPosition start = reader.position;
        switch (Char ch = reader.nextChar())
            {
            case '{':
                id = ObjectEnter;
                break;

            case '}':
                id = ObjectExit;
                break;

            case '[':
                id = ArrayEnter;
                break;

            case ']':
                id = ArrayExit;
                break;

            case ',':
                id = Comma;
                break;

            case ':':
                id = Colon;
                break;

            case 'n':
                require("ull");
                id  = NoVal;
                val = Null;
                break;

            case 't':
                require("rue");
                id  = BoolVal;
                val = True;
                break;

            case 'f':
                require("alse");
                id  = BoolVal;
                val = False;
                break;

            case '-', '0'..'9':
                val = eatNumber(ch, start);
                id  = val.is(FPLiteral) ? FPVal : IntVal;
                break;

            case '\"':
                id  = StrVal;
                val = eatString();
                break;

            default:
                throw new IOException($"unexpected character: {ch.quoted()}");
            }

        TextPosition end = reader.position;
        eatWhitespace();
        return new Token(id, start, end, val);
        }

    /**
     * Eat a number; the first character has already been eaten.
     *
     * @param ch  the first character, which is either '-' or a digit in the range 0-9
     *
     * @return the number as an IntLiteral or an FPLiteral
     */
    protected Primitive eatNumber(Char ch, TextPosition start)
        {
        Int     count = 0;
        Boolean fp    = False;
        EOF: do
            {
            // Init
            //     '-'         PreWhole
            //     other       PreWhole
            if (ch == '-')
                {
                ch = reader.nextChar();
                ++count;
                }

            // PreWhole
            //     '0'         PostWhole
            //     '1'..'9'    MidWhole
            if (ch == '0')
                {
                if (reader.eof)
                    {
                    break EOF;
                    }
                ch = reader.nextChar();
                ++count;
                }
            else if (ch >= '1' && ch <= '9')
                {
                // MidWhole
                //     '0'..'9'    MidWhole
                //     other       PostWhole
                do
                    {
                    if (reader.eof)
                        {
                        break EOF;
                        }
                    ch = reader.nextChar();
                    ++count;
                    }
                while (ch >= '0' && ch <= '9');
                }
            else
                {
                throw new IOException($"expected digit; found '{ch}'");
                }

            // PostWhole
            //     '.'         PreFrac
            //     other       PostFrac
            if (ch == '.')
                {
                fp = True;
                ch = reader.nextChar();
                ++count;

                // PreFrac
                //     '0'..'9'    MidFrac
                if (ch >= '0' && ch <= '9')
                    {
                    // MidFrac
                    //     '0'..'9'    MidFrac
                    //     other       PostFrac
                    do
                        {
                        if (reader.eof)
                            {
                            break EOF;
                            }
                        ch = reader.nextChar();
                        ++count;
                        }
                    while (ch >= '0' && ch <= '9');
                    }
                else
                    {
                    throw new IOException($"expected digit; found '{ch}'");
                    }
                }

            // PostFrac
            //     'e' 'E'     PreExpSign
            //     other       finished
            if (ch == 'e' || ch == 'E')
                {
                fp = True;
                ch = reader.nextChar();
                ++count;

                // PreExpSign
                //     '+' '-'     PreExp
                //     other       PreExp
                if (ch == '+' || ch == '-')
                    {
                    ch = reader.nextChar();
                    ++count;
                    }

                // PreExp
                //     '0'..'9'    MidExp
                //     other       finished
                if (ch >= '0' && ch <= '9')
                    {
                    // MidExp
                    //     '0'..'9'    MidExp
                    //     other       finished
                    do
                        {
                        if (reader.eof)
                            {
                            break EOF;
                            }
                        ch = reader.nextChar();
                        ++count;
                        }
                    while (ch >= '0' && ch <= '9');
                    }
                else
                    {
                    throw new IOException($"expected digit; found '{ch}'");
                    }
                }
            }
        while (False);

        reader.position = start;
        String lit = reader.nextString(count);
        return fp ? new FPLiteral(lit) : new IntLiteral(lit);
        }

    /**
     * Eat a quoted String; the opening quote has already been eaten.
     *
     * @return the contents of the quoted String
     */
    protected String eatString()
        {
        TextPosition  start = reader.position;
        Int           count = 0;
        StringBuffer? buf   = Null;
        while (Char ch := reader.next())
            {
            switch (ch)
                {
                case '\"':
                    if (buf == Null)
                        {
                        TextPosition cur = reader.position;
                        reader.position  = start;
                        String result    = reader.nextString(count);
                        reader.position  = cur;
                        return result;
                        }
                    else
                        {
                        return buf.toString();
                        }

                case '\\':
                    if (buf == Null)
                        {
                        buf = new StringBuffer();
                        if (count > 0)
                            {
                            TextPosition cur = reader.position;
                            reader.position  = start;
                            buf.addAll(reader.nextChars(count));
                            reader.position  = cur;
                            }
                        }

                    switch (ch = reader.nextChar())
                        {
                        case '\"':
                        case '\\':
                        case '/':
                            buf.add(ch);
                            break;

                        case 'b':
                            buf.add('\b');
                            break;

                        case 'f':
                            buf.add('\f');
                            break;

                        case 'n':
                            buf.add('\n');
                            break;

                        case 'r':
                            buf.add('\r');
                            break;

                        case 't':
                            buf.add('\t');
                            break;

                        case 'u':
                            // 'u' hex hex hex hex
                            UInt32 codepoint = Nibble.of(reader.nextChar()).toUInt32() << 8
                                             | Nibble.of(reader.nextChar()).toUInt32() << 8
                                             | Nibble.of(reader.nextChar()).toUInt32() << 8
                                             | Nibble.of(reader.nextChar()).toUInt32();
                            break;
                        }
                    break;

                default:
                    if (ch.codepoint < 0x20)
                        {
                        }

                    ++count;
                    if (buf != Null)
                        {
                        buf.add(ch);
                        }
                    break;
                }
            }

        throw new EndOfFile();
        }

    /**
     * Require that the next sequence of characters in the JSON document match the characters in
     * the specified string.
     *
     * @param s  the string of characters that must exactly match the next characters in the JSON
     *           document text
     *
     * @throws IOException if the next character does not match the specified character
     */
    protected void require(String s)
        {
        for (Char ch : s)
            {
            require(ch);
            }
        }

    /**
     * Require the next character to match a specific character.
     *
     * @param ch  the character that must be the next character in the JSON document text
     *
     * @throws IOException if the next character does not match the specified character
     */
    protected void require(Char ch)
        {
        if (reader.eof || reader.nextChar() != ch)
            {
            throw new IOException($"expected '{ch}'");
            }
        }
    }
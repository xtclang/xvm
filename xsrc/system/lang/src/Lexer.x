import io.TextPosition;


/**
 * A lexical analyzer (tokenizer) for the Ecstasy language.
 */
class Lexer
        implements Iterator<Token>
        implements Markable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an Ecstasy lexical analyzer ("tokenizer") that processes source code from a Reader.
     *
     * @param source   the Ecstasy source code
     * @param errlist  the ErrorList to log errors to
     */
    construct(Source source, ErrorList errlist)
        {
        this.source  = source;
        this.errlist = errlist;
        this.reader  = source.createReader();
        }
    finally
        {
        eatWhitespace();
        }

    /**
     * Internal use.
     *
     * @param parent  a Lexer that this Lexer can delegate to if necessary
     */
    protected construct(Lexer parent)
        {
        source  = parent.source;
        errlist = parent.errlist;
        reader  = source.createReader();
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The [Source] being lexed.
     */
    public/private Source source;

    /**
     * The underlying [Reader]. Note that the UTF-escape transformation occurs between this Reader
     * and the lexer.
     */
    protected/private Reader reader;

    /**
     * The number of characters past the EOF that the lexer has pretended to read.
     */
    protected/private Int pastEOF;

    /**
     * Keeps track of whether whitespace was encountered.
     */
    protected/private Boolean whitespace;

    /**
     * The ErrorList to log errors to.
     */
    public/private ErrorList errlist;


    // ----- Iterator methods ----------------------------------------------------------------------

    @Override
    conditional Token next()
        {
        if (eof)
            {
            return False;
            }

        Boolean      spaceBefore = whitespace;
        TextPosition posBefore   = reader.position;
        (Id id, Object value)    = eatToken(posBefore);
        TextPosition posAfter    = reader.position;
        Boolean      spaceAfter  = eatWhitespace();
        return True, new Token(id, posBefore, posAfter, value, spaceBefore, spaceAfter);
        }


    // ----- Markable methods ----------------------------------------------------------------------

    /**
     * A restorable position within the Lexer (Literally, Lex-Mark.)
     */
    protected class Mark(Reader reader, TextPosition position, Int pastEOF, Boolean whitespace);

    @Override
    Object mark()
        {
        return new Mark(reader, reader.position, pastEOF, whitespace);
        }

    @Override
    void restore(Object mark, Boolean unmark = False)
        {
        assert mark.is(Mark);
        assert mark.reader == reader;
        reader.position = mark.position;
        this.pastEOF    = mark.pastEOF;
        this.whitespace = mark.whitespace;
        }


    // ----- simulated Lexer -----------------------------------------------------------------------

    /**
     * Create a Lexer that pretends to lex the provided array of tokens.
     *
     * @param tokens  the tokens to emit
     *
     * @return the new Lexer
     */
    Lexer! createLexer(Token[] tokens)
        {
        return new Lexer(this)
            {
            Int index = 0;

            @Override
            conditional Token next()
                {
                if (index >= tokens.size)
                    {
                    return False;
                    }

                return True, tokens[index++];
                }

            @Override
            Object mark()
                {
                return index;
                }

            @Override
            void restore(Object mark, Boolean unmark = False)
                {
                index = mark.as(Int);
                }
            };
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Eat the characters defined as whitespace, which include line terminators and the file
     * terminator. Whitespace does not include comments.
     *
     * @return True iff whitespace was found
     */
    protected Boolean eatWhitespace()
        {
        Boolean whitespace = False;
        while (!eof)
            {
            Char ch = nextChar();
            if (ch.isWhitespace())
                {
                whitespace = True;
                }
            else
                {
                // put back the non-whitespace character
                rewind();
                break;
                }
            }

        this.whitespace = whitespace;
        return whitespace;
        }

    /**
     * Lex a single token.
     *
     * @return id     the token id
     * @return value  the token value (usually Null)
     */
    protected (Id id, Object value) eatToken(TextPosition before)
        {
        switch (Char next = nextChar())
            {
            case '{':
                return LeftCurly, Null;

            case '}':
                return RightCurly, Null;

            case '(':
                return LeftParen, Null;

            case ')':
                return RightParen, Null;

            case '[':
                return LeftSquare, Null;

            case ']':
                return RightSquare, Null;

            case ';':
                return Semicolon, Null;

            case ',':
                return Comma, Null;

            case '.':
                switch (nextChar())
                    {
                    case '.':
                        switch (nextChar())
                            {
                            case '.':
                                return Ellipsis, Null;

                            case '/':
                                return ParentDir, Null;

                            default:
                                rewind();
                                return DotDot, Null;
                            }

                    case '/':
                        return CurrentDir, Null;

                    case '0'..'9':
                        rewind();
                        return eatNumericLiteral(before, next);

                    default:
                        rewind();
                        return Dot, Null;
                    }

            case '$':
                switch (nextChar())
                    {
                    case '\"':
                        return eatTemplateLiteral(before);

                    case '|':
                        return eatMultilineTemplateLiteral(before);

                    case '/':
                        // it is a file name starting with "/"
                        rewind();
                        return StrFile, Null;

                    case '.':
                        switch (nextChar())
                            {
                            case '.':
                                switch (nextChar())
                                    {
                                    case '/':
                                        // it is a file name starting with "../"
                                        rewind(3);
                                        return StrFile, Null;

                                    default:
                                        rewind(3);
                                        return Identifier, "$";
                                    }

                            case '/':
                                // it is a file name starting with "./"
                                rewind(2);
                                return StrFile, Null;

                            default:
                                rewind(2);
                                return Identifier, "$";
                            }

                    default:
                        rewind();
                        return Identifier, "$";
                    }

            case '#':
                switch (nextChar())
                    {
                    case '.':
                    case '/':
                        // it is a file name
                        rewind();
                        return BinFile, Null;

                    case '|':
                        // multi-line binary literal
                        return eatBinaryLiteral(before, True);

                    default:
                        rewind();
                        return eatBinaryLiteral(before, False);
                    }

            case '@':
                return At, Null;

            case '?':
                switch (nextChar())
                    {
                    case '=':
                        return NotNullAsn, Null;

                    case ':':
                        switch (nextChar())
                            {
                            case '=':
                                return ElvisAsn, Null;

                            default:
                                rewind();
                                return Elvis, Null;
                            }

                    default:
                        rewind();
                        return Condition, Null;
                    }

            case ':':
                switch (nextChar())
                    {
                    case '=':
                        return CondAsn, Null;

                    default:
                        rewind();
                        return Colon, Null;
                    }

            case '+':
                switch (nextChar())
                    {
                    case '+':
                        return Increment, Null;

                    case '=':
                        return AddAsn, Null;

                    default:
                        rewind();
                        return Add, Null;
                    }

            case '-':
                switch (nextChar())
                    {
                    case '-':
                        return Decrement, Null;

                    case '>':
                        return Lambda, Null;

                    case '=':
                        return SubAsn, Null;

                    default:
                        rewind();
                        return Sub, Null;
                    }

            case '*':
                switch (nextChar())
                    {
                    case '=':
                        return MulAsn, Null;

                    default:
                        rewind();
                        return Mul, Null;
                    }

            case '/':
                switch (nextChar())
                    {
                    case '/':
                        return eatSingleLineComment(before);

                    case '*':
                        return eatEnclosedComment(before);

                    case '=':
                        return DivAsn, Null;

                    case '%':
                        return DivRem, Null;

                    default:
                        rewind();
                        return Div, Null;
                    }

            case '<':
                switch (nextChar())
                    {
                    case '<':
                        switch (nextChar())
                            {
                            case '=':
                                return ShiftLeftAsn, Null;

                            default:
                                rewind();
                                return ShiftLeft, Null;
                            }

                    case '=':
                        switch (nextChar())
                            {
                            case '>':
                                return CompareOrder, Null;

                            default:
                                rewind();
                                return CompareLTEQ, Null;
                            }

                    default:
                        rewind();
                        return CompareLT, Null;
                    }

            case '>':
                switch (nextChar())
                    {
                    case '>':
                        switch (nextChar())
                            {
                            case '>':
                                switch (nextChar())
                                    {
                                    case '=':
                                        return ShiftAllAsn, Null;

                                    default:
                                        rewind();
                                        return ShiftAll, Null;
                                    }

                            case '=':
                                return ShiftRightAsn, Null;

                            default:
                                rewind();
                                return ShiftRight, Null;
                            }

                    case '=':
                        return CompareGTEQ, Null;

                    default:
                        rewind();
                        return CompareGT, Null;
                    }

            case '&':
                switch (nextChar())
                    {
                    case '&':
                        switch (nextChar())
                            {
                            case '=':
                                return BoolAndAsn, Null;

                            default:
                                rewind();
                                return BoolAnd, Null;
                            }

                    case '=':
                        return BitAndAsn, Null;

                    default:
                        rewind();
                        return BitAnd, Null;
                    }

            case '|':
                switch (nextChar())
                    {
                    case '|':
                        switch (nextChar())
                            {
                            case '=':
                                return BoolOrAsn, Null;

                            default:
                                rewind();
                                return BoolOr, Null;
                            }

                    case '=':
                        return BitOrAsn, Null;

                    default:
                        rewind();
                        return BitOr, Null;
                    }

            case '=':
                switch (nextChar())
                    {
                    case '=':
                        return CompareEQ, Null;

                    default:
                        rewind();
                        return Assign, Null;
                    }

            case '%':
                switch (nextChar())
                    {
                    case '=':
                        return ModuloAsn, Null;

                    default:
                        rewind();
                        return Modulo, Null;
                    }

            case '!':
                switch (nextChar())
                    {
                    case '=':
                        return CompareNE, Null;

                    default:
                        rewind();
                        return BoolNot, Null;
                    }

            case '^':
                switch (nextChar())
                    {
                    case '^':
                        return BoolXor, Null;

                    case '=':
                        return BitXorAsn, Null;

                    default:
                        rewind();
                        return BitXor, Null;
                    }

            case '~':
                return BitNot, Null;

            case '0'..'9':
                return eatNumericLiteral(before, next);

            case '\'':
                return eatCharLiteral(before);

            case '\"':
                return eatStringLiteral(before);

            case '`':
                return eatMultilineLiteral(before);

            default:
                if (!isIdentifierStart(next))
                    {
                    log(Error, IllegalChar, [next.quoted()], before, reader.position);
                    }
                continue;

            case 'A'..'Z':
            case 'a'..'z':
            case '_':
                return eatIdentifierOrKeyword(before, next);
            }
        }

    /**
     * Lex a numeric literal token, starting with the second character of the literal (the first
     * being passed in).
     *
     * @param before  the position of the first character of the token
     * @param first   the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatNumericLiteral(TextPosition before, Char first)
        {
        TODO
        }

    /**
     * Lex a template literal, such as `$"x={x}"`. The opening `$"` has already been eaten.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatTemplateLiteral(TextPosition before)
        {
        TODO
        }

    /**
     * Lex a multi-line template literal, such as:
     *
     *     $|x={x}
     *      |y={y}
     *
     * The opening `$|` has already been eaten.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatMultilineTemplateLiteral(TextPosition before)
        {
        TODO
        }

    /**
     * Lex either a single- or multi-line binary literal, such as `#FFAA01234FF`. The opening `#` or
     * `#|` has already been eaten.
     *
     * @param before     the position of the first character of the token
     * @param multiline  True iff the token began with a `#|`
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatBinaryLiteral(TextPosition before, Boolean multiline)
        {
        Byte[] nibs = new Byte[];
        Loop: while (!eof)
            {
            Char ch = nextChar();
            if (Int n := ch.isHexit())
                {
                nibs.add(n.toByte());
                }
            else if (ch == '_')
                {
                if (Loop.first)
                    {
                    // it's an error to start with an underscore
                    log(Error, IllegalHex, [ch.quoted()], before, reader.position);
                    }
                // ignore the _ (it's used for spacing within the literal)
                }
            else if (!multiline)
                {
                // the first non-hexit / non-underscore character indicates an end-of-value
                rewind();
                break;
                }
            else if (ch.isWhitespace())
                {
                // ignore whitespace, unless it's newline
                if (ch.isLineTerminator() && !isMultilineContinued())
                    {
                    rewind();
                    break;
                    }
                }
            else
                {
                // error
                log(Error, IllegalHex, [ch.quoted()], before, reader.position);
                rewind();
                break;
                }
            }

        // REVIEW this code needs to be reusable and placed somewhere else (or use Nibble[])
        Int    bytesLen  = (nibs.size + 1) / 2;
        Byte[] bytes     = new Byte[bytesLen];
        Int    nibIndex  = 0;
        Int    byteIndex = 0;
        if (nibs.size & 0x1 != 0)
            {
            bytes[0]  = nibs[0];
            nibIndex  = 1;
            byteIndex = 1;
            }
        while (byteIndex < bytesLen)
            {
            bytes[byteIndex++] = nibs[nibIndex++] << 4 | nibs[nibIndex++];
            }

        return LitBinstr, bytes;
        }

    /**
     * Peek forward to see if the value is continued on the next line.
     *
     * @return True iff the value is continued on the next line, and all of the characters up to the
     *         value have been eaten and discarded
     */
    protected Boolean isMultilineContinued()
        {
        TextPosition pos = reader.position;
        while (!eof)
            {
            Char ch = nextChar();
            if (!ch.isWhitespace())
                {
                if (ch == '|')
                    {
                    return True;
                    }
                break;
                }
            }

        reader.position = pos;
        return False;
        }

    /**
     * Eat the remainder of a single line aka end-of-line comment. The opening `//` has already been
     * eaten.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatSingleLineComment(TextPosition before)
        {
        StringBuffer? buf           = Null;
        Int           trailingSpace = 0;
        while (!eof)
            {
            Char ch = nextChar();
            if (ch.isLineTerminator())
                {
                rewind();
                break;
                }

            if (ch.isWhitespace())
                {
                ++trailingSpace;
                }
            else
                {
                buf ?:= new StringBuffer();
                trailingSpace = 0;
                }

            buf?.add(ch);
            }

        String comment = "";
        if (buf != Null)
            {
            if (trailingSpace > 0)
                {
                buf = buf[0..buf.size-trailingSpace);
                }
            comment = buf.toString();
            }

        return EolComment, comment;
        }

    /**
     * Eat the remainder of a multi-line aka enclosed comment. The opening `/*` has already been
     * eaten.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatEnclosedComment(TextPosition before)
        {
        StringBuffer buf      = new StringBuffer();
        Boolean      asterisk = False;
        Boolean      doc      = False;
        Loop: while (!eof)
            {
            Char ch = nextChar();
            if (ch == '*')
                {
                asterisk = True;

                if (Loop.first)
                    {
                    doc = True;
                    continue;
                    }
                }
            else if (asterisk && ch == '/')
                {
                // remove trailing asterisk, and remove both leading and trailing whitespace
                String comment = buf.size > 0 ? buf[0..buf.size-1).toString().trim() : "";
                return (doc && comment.size > 0 ? DocComment : EncComment), comment;
                }
            else
                {
                asterisk = False;
                }

            buf.add(ch);
            }

        // missing the enclosing "*/"
        log(Error, ExpectedEndcomment, [], before, reader.position);

        // just pretend that the rest of the file was all one big comment
        String comment = buf.toString().trim();
        return (doc && comment.size > 0 ? DocComment : EncComment), comment;
        }

    /**
     * Eat the remainder of a character literal. The opening `'` has already been eaten.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatCharLiteral(TextPosition before)
        {
        Char    ch   = '?';
        Boolean term = False;
        if (!eof)
            {
            TextPosition chPos = reader.position;
            switch (ch = nextChar())
                {
                case '\'':
                    if (!eof)
                        {
                        if (nextChar() == '\'')
                            {
                            // assume the previous one should have been escaped
                            rewind();
                            log(Error, CharBadEsc, [], chPos, reader.position);
                            }
                        else
                            {
                            // assume the encountered quote that we thought was supposed to be
                            // the character value was instead supposed to be closing quote
                            rewind(2);
                            log(Error, CharNoChar, [], chPos, chPos);
                            ch   = '?';
                            term = True;
                            }
                        }
                    break;

                case '\\':
                    // process escaped char
                    switch (ch = nextChar())
                        {
                        case '\\':
                        case '\'':
                        case '\"':
                            break;

                        case '0':
                            ch = '\0';
                            break;
                        case 'b':
                            ch = '\b';
                            break;
                        case 'd':
                            ch = '\d';
                            break;
                        case 'e':
                            ch = '\e';
                            break;
                        case 'f':
                            ch = '\f';
                            break;
                        case 'n':
                            ch = '\n';
                            break;
                        case 'r':
                            ch = '\r';
                            break;
                        case 't':
                            ch = '\t';
                            break;
                        case 'v':
                            ch = '\v';
                            break;
                        case 'z':
                            ch = '\z';
                            break;

                        default:
                            if (ch.isLineTerminator())
                                {
                                // log error: newline in string
                                rewind();
                                log(Error, CharNoTerm, [], before, reader.position);
                                // assume it wasn't supposed to be an escape
                                ch   = '\\';
                                term = True;
                                }
                            else
                                {
                                // log error: bad escape
                                log(Error, CharBadEsc, [], chPos, reader.position);
                                }
                            break;
                        }
                    break;

                default:
                    if (ch.isLineTerminator())
                        {
                        // log error: newline in string
                        rewind();
                        log(Error, CharNoTerm, [], before, reader.position);
                        ch   = '?';
                        term = True;
                        }
                    break;
                }

            if (!eof && !term)
                {
                if (nextChar() == '\'')
                    {
                    term = True;
                    }
                else
                    {
                    rewind();
                    }
                }
            }

        if (!term)
            {
            // log error: unterminated character literal
            log(Error, CharNoTerm, [], before, reader.position);
            }

        return LitChar, ch;
        }

    /**
     * Eat the remainder of a string literal. The opening `"` has already been eaten.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatStringLiteral(TextPosition before)
        {
        TODO
        }

    /**
     * Eat the remainder of a multi-line string literal. The opening tick has already been eaten.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatMultilineLiteral(TextPosition before)
        {
        TODO
        }

    /**
     * Eat a token that may be an identifier or keyword. The first character has already been eaten.
     *
     * @param before  the position of the first character of the token
     * @param first   the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatIdentifierOrKeyword(TextPosition before, Char first)
        {
        TODO
        }


    // ----- reader behavior -----------------------------------------------------------------------

    /**
     * True once the stream of characters is exhausted.
     */
    protected Boolean eof.get()
        {
        return reader.eof;
        }

    private Boolean containsUnicodeEscapes = False;
    private Boolean containsCRLFs          = False;

    /**
     * Obtain the next character, if any is available.
     *
     * @return the next character, or an EOF character ('\z')
     */
    protected Char nextChar()
        {
        Char ch;
        if (ch := reader.next())
            {
            // 1) last character in the file may be SUB
            // 2) CR:LF is converted to LF
            // 3) '\\' + 'u' + 'xxxx'
            // 4) '\\' + 'U' + 'xxxxxxxx'
            switch (ch)
                {
                case '\z':
                    if (!reader.eof)
                        {
                        // back up to get the location of the SUB character
                        reader.rewind();
                        TextPosition before = reader.position;
                        assert reader.next();
                        TextPosition after = reader.position;
                        log(Error, UnexpectedEof, [], before, after);
                        }
                    break;

                case '\r':
                    if (!reader.eof)
                        {
                        assert ch := reader.next();
                        if (ch == '\n')
                            {
                            // use the CRLF as if it were just an LF
                            containsCRLFs = True;
                            }
                        else
                            {
                            // pretend that the CR was actually an LF
                            reader.rewind();
                            ch = '\n';
                            }
                        }
                    break;

                case '\\':
                    if (Char chU := reader.next())
                        {
                        Int rewind = 1; // the 'u' or 'U'
                        Int hexits = switch (chU)
                            {
                            case 'u': 4;
                            case 'U': 8;
                            default : 0;
                            };

                        maybeHex: if (hexits > 0)
                            {
                            Int codepoint = 0;
                            while (hexits > 0)
                                {
                                if (Char chX := reader.next())
                                    {
                                    ++rewind;
                                    if (Int n := chX.asciiHexit())
                                        {
                                        codepoint = codepoint << 4 | n;
                                        }
                                    else
                                        {
                                        break maybeHex;
                                        }
                                    }
                                }

                            containsUnicodeEscapes = True;
                            ch                     = codepoint.toChar();
                            rewind                 = 0;
                            }

                        // if the value wasn't a properly formed unicode escape, then undo the reads
                        // of the whatever we read after the first '\\'
                        while (rewind-- > 0)
                            {
                            reader.rewind();
                            }
                        }
                    break;
                }

            return ch;
            }
        else
            {
            ++pastEOF;
            return '\z';
            }
        }

    /**
     * Rewind the specified number of characters, default to one.
     *
     * @param count  the number of characters to rewind
     */
    protected void rewind(Int count = 1)
        {
        assert count > 0;
        if (pastEOF > 0)
            {
            Int adjust = count.minOf(pastEOF);
            count   -= adjust;
            pastEOF -= adjust;
            if (count == 0)
                {
                return;
                }
            }

        Int offset = reader.position.offset;
        assert count <= offset;

        if (containsUnicodeEscapes || containsCRLFs)
            {
            private Boolean matchEscape(Char u, Int count)
                {
                scan: if (reader.nextChar() == u)
                    {
                    while (count-- > 0)
                        {
                        if (!reader.nextChar().asciiHexit())
                            {
                            break scan;
                            }
                        }
                    return True;
                    }

                if (count > 0)
                    {
                    reader.skip(count);
                    }

                return False;
                }

            offset -= count;
            while (count-- > 0)
                {
                // if the character is an \n and containsCRLFs is true, or if the character is a
                // hexit and containsUnicodeEscapes, then extra work has to be done to make sure
                // that the character isn't part of a sequence of characters that was translated to
                // a single character when we previously read it
                reader.rewind(1);
                Char ch = reader.nextChar();
                reader.rewind(1);
                if (containsCRLFs && ch == '\n' && offset > 0)
                    {
                    reader.rewind(1);
                    Char ch2 = reader.nextChar();
                    if (ch2 == '\r')
                        {
                        --offset;
                        reader.rewind(1);
                        }
                    }
                if (containsUnicodeEscapes && ch.asciiHexit() && offset >= 5)
                    {
                    // previous 3 or 7 chars need to be hexit, preceded by 'u' or 'U', preceded
                    // by '\\'; first check for the '\\' + 'u' version
                    reader.rewind(5);
                    Char ch2 = reader.nextChar();
                    if (ch2 == '\\')
                        {
                        if (matchEscape('u', 3))
                            {
                            reader.rewind(5);
                            offset -= 5;
                            }
                        }
                    else if (ch2.asciiHexit() && offset >= 9)
                        {
                        // it could be the 'U' form
                        reader.rewind(5);
                        ch2 = reader.nextChar();
                        if (ch2 == '\\')
                            {
                            if (matchEscape('U', 7))
                                {
                                reader.rewind(9);
                                offset -= 9;
                                }
                            }
                        else
                            {
                            reader.skip(8);
                            }
                        }
                    else
                        {
                        // oops; it was not a unicode escape
                        reader.skip(4);
                        }
                    }
                }
            }
        else
            {
            reader.rewind(count);
            }
        }


    // ----- error handling ------------------------------------------------------------------------

    /**
     * Log an error.
     *
     * @param severity  the severity of the error
     * @param errmsg    the error message identity
     * @param params    the values to use to populate the parameters of the error message
     * @param before    the TextPosition of the first character (inclusive) related to the error
     * @param after     the TextPosition of the last character (exclusive) related to the error
     *
     * @return True indicates that the process that reported the error should attempt to abort at
     *         this point if it is able to
     */
    protected Boolean log(Severity severity, ErrorMsg errmsg, Object[] params, TextPosition before, TextPosition after)
        {
        return errlist.log(new Error(severity, errmsg.code, ErrorMsg.lookup, params, source, before, after));
        }

    /**
     * Error codes.
     *
     * While it may appear that the error messages are hard-coded, the text found here is simply
     * the default error text; it will eventually be localized as necessary.
     */
    enum ErrorMsg(String code, String message)
        {
        UnexpectedEof     ("LEXER-01", "Unexpected End-Of-File (SUB character)."),
        ExpectedEndcomment("LEXER-02", "Expected a comment-ending \"star slash\" but never found one."),
        IllegalChar       ("LEXER-03", "Invalid character: \"{0}\"."),
        IllegalNumber     ("LEXER-04", "Illegal number: \"{0}\"."),
        CharNoTerm        ("LEXER-05", "An illegal character literal, missing closing quote: \"{0}\"."),
        CharBadEsc        ("LEXER-06", "An illegally escaped character literal: \"{0}\"."),
        CharNoChar        ("LEXER-07", "An illegal character literal missing the character: \"{0}\"."),
        StringNoTerm      ("LEXER-08", "An illegally terminated string literal: \"{0}\"."),
        StringBadEsc      ("LEXER-09", "An illegally escaped string literal: \"{0}\"."),
        IllegalHex        ("LEXER-10", "Illegal hex value: \"{0}\"."),
        ExpectedChar      ("LEXER-11", "Expected \"{0}\"; found \"{1}\"."),
        ExpectedDigits    ("LEXER-12", "\"{0}\" digits were required; only \"{1}\" digits were found."),
        BadDate           ("LEXER-13", "Invalid ISO-8601 date \"{0}\"; date must be in the format \"YYYY-MM-DD\" with valid values for each."),
        BadTime           ("LEXER-14", "Invalid ISO-8601 time \"{0}\"; time must be in the format \"hh:mm:ss.sss\" or \"hhmmss.sss\" (with seconds and fractions of seconds optional) with valid values for each."),
        BadDatetime       ("LEXER-15", "Invalid ISO-8601 datetime \"{0}\"; datetime must be in the format date+\"T\"+time+timezone (with timezone optional), with valid values for each."),
        BadTimezone       ("LEXER-16", "Invalid ISO-8601 timezone \"{0}\"; timezone must be \"Z\" (for UTC), or in the format \"+hh:mm\" or \"+hhmm\" (using either \"+\" or \"-\", and with minutes optional) with valid values for each."),
        BadDuration       ("LEXER-17", "Invalid ISO-8601 duration \"{0}\"; duration must be in the format \"PnYnMnDTnHnMnS\" (with the year, month, day, and time value optional, and the hours, minutes, and seconds values optional within the time portion), with valid values for each."),
        UnexpectedChar    ("LEXER-18", "Unexpected character: \"{0}\".");

        /**
         * Message  token ids, but not including context-sensitive keywords.
         */
        static Map<String, ErrorMsg> byCode =
            {
            HashMap<String, ErrorMsg> map = new HashMap();
            for (ErrorMsg errmsg : ErrorMsg.values)
                {
                map[errmsg.code] = errmsg;
                }
            return map.makeImmutable();
            };

        /**
         * Lookup unformatted error message by error code.
         */
        static String lookup(String code)
            {
            assert ErrorMsg err := byCode.get(code);
            return err.message;
            }
        }


    // ----- types ---------------------------------------------------------------------------------

    /**
     * An Ecstasy token has a lexical identity (what the element type is), a location in the text stream
     * (with the ending position being the position _after_ the token), and an optional value.
     */
    static const Token(Id id, TextPosition start, TextPosition end, Object value = Null,
                       Boolean spaceBefore=False, Boolean spaceAfter=False);

    /**
     * Token identity categories.
     */
    enum Category {Normal, ContextSensitive, Special, Artificial}

    /**
     * Ecstasy source code is composed of these lexical elements.
     */
    enum Id(String? text, Category category=Normal)
        {
        Colon        (":"              ),
        Semicolon    (";"              ),
        Comma        (","              ),
        Dot          ("."              ),
        DotDot       (".."             ),
        Ellipsis     ("..."            ),
        CurrentDir   ("./"             ),
        ParentDir    ("../"            ),
        LeftParen    ("("              ),
        RightParen   (")"              ),
        LeftCurly    ("{"              ),
        RightCurly   ("}"              ),
        LeftSquare   ("["              ),
        RightSquare  ("]"              ),
        Add          ("+"              ),
        Sub          ("-"              ),
        Mul          ("*"              ),
        Div          ("/"              ),
        DivRem       ("/%"             ),
        Modulo       ("%"              ),
        ShiftLeft    ("<<"             ),
        ShiftRight   (">>"             ),
        ShiftAll     (">>>"            ),
        BitAnd       ("&"              ),
        BitOr        ("|"              ),
        BitXor       ("^"              ),
        BitNot       ("~"              ),
        BoolAnd      ("&&"             ),
        BoolOr       ("||"             ),
        BoolXor      ("^^"             ),
        BoolNot      ("!"              ),
        BinFile      ("#"              ),
        StrFile      ("$"              ),
        At           ("@"              ),
        Condition    ("?"              ),
        Elvis        ("?:"             ),
        Assign       ("="              ),
        AddAsn       ("+="             ),
        SubAsn       ("-="             ),
        MulAsn       ("*="             ),
        DivAsn       ("/="             ),
        ModuloAsn    ("%="             ),
        ShiftLeftAsn ("<<="            ),
        ShiftRightAsn(">>="            ),
        ShiftAllAsn  (">>>="           ),
        BitAndAsn    ("&="             ),
        BitOrAsn     ("|="             ),
        BitXorAsn    ("^="             ),
        BoolAndAsn   ("&&="            ),
        BoolOrAsn    ("||="            ),
        CondAsn      (":="             ),
        NotNullAsn   ("?="             ),
        ElvisAsn     ("?:="            ),
        CompareEQ    ("=="             ),
        CompareNE    ("!="             ),
        CompareLT    ("<"              ),
        CompareLTEQ  ("<="             ),
        CompareGT    (">"              ),
        CompareGTEQ  (">="             ),
        CompareOrder ("<=>"            ),
        Increment    ("++"             ),
        Decrement    ("--"             ),
        Lambda       ("->"             ),
        Any          ("_"              ),
        Allow        ("allow"          , Category.ContextSensitive),  // TODO GG why is "Category." required?
        As           ("as"             ),
        Assert       ("assert"         ),
        AssertRnd    ("assert:rnd"     ),
        AssertArg    ("assert:arg"     ),
        AssertBounds ("assert:bounds"  ),
        AssertTodo   ("assert:TODO"    ),
        AssertOnce   ("assert:once"    ),
        AssertTest   ("assert:test"    ),
        AssertDebug  ("assert:debug"   ),
        Avoid        ("avoid"          , Category.ContextSensitive),
        Break        ("break"          ),
        Case         ("case"           ),
        Catch        ("catch"          ),
        Class        ("class"          ),
        Conditional  ("conditional"    ),
        Const        ("const"          ),
        Construct    ("construct"      ),
        Continue     ("continue"       ),
        Default      ("default"        ),
        Delegates    ("delegates"      , Category.ContextSensitive),
        Do           ("do"             ),
        Else         ("else"           ),
        Enum         ("enum"           ),
        Extends      ("extends"        , Category.ContextSensitive),
        Finally      ("finally"        ),
        For          ("for"            ),
        Function     ("function"       ),
        If           ("if"             ),
        Immutable    ("immutable"      ),
        Implements   ("implements"     , Category.ContextSensitive),
        Import       ("import"         ),
        ImportEmbed  ("import:embedded"),
        ImportRequire("import:required"),
        ImportDesire ("import:desired" ),
        ImportOption ("import:optional"),
        Incorporates ("incorporates"   , Category.ContextSensitive),
        Interface    ("interface"      ),
        Into         ("into"           , Category.ContextSensitive),
        Is           ("is"             ),
        Mixin        ("mixin"          ),
        Module       ("module"         ),
        New          ("new"            ),
        Outer        ("outer"          , Category.Special),
        Package      ("package"        ),
        Prefer       ("prefer"         , Category.ContextSensitive),
        Private      ("private"        ),
        Protected    ("protected"      ),
        Public       ("public"         ),
        Return       ("return"         ),
        Service      ("service"        ),
        Static       ("static"         ),
        Struct       ("struct"         ),
        Super        ("super"          , Category.Special),
        Switch       ("switch"         ),
        This         ("this"           , Category.Special),
        ThisClass    ("this:class"     , Category.Special),
        ThisModule   ("this:module"    , Category.Special),
        ThisPri      ("this:private"   , Category.Special),
        ThisPro      ("this:protected" , Category.Special),
        ThisPub      ("this:public"    , Category.Special),
        ThisServ     ("this:service"   , Category.Special),
        ThisStruct   ("this:struct"    , Category.Special),
        ThisTarget   ("this:target"    , Category.Special),
        Throw        ("throw"          ),
        Todo         ("TODO"           ),
        Try          ("try"            ),
        Typedef      ("typedef"        ),
        Using        ("using"          ),
        Val          ("val"            , Category.ContextSensitive),
        Var          ("var"            , Category.ContextSensitive),
        Void         ("void"           ),
        While        ("while"          ),
        Identifier   (Null             ),
        LitChar      (Null             ),
        LitString    (Null             ),
        LitBinstr    (Null             ),
        LitInt       (Null             ),
        LitDec       (Null             ),
        LitFloat     (Null             ),
        LitDate      (Null             ),
        LitTime      (Null             ),
        LitDatetime  (Null             ),
        LitTimezone  (Null             ),
        LitDuration  (Null             ),
        LitVersion   (Null             ),
        LitPath      (Null             ),               // generated by Parser, not Lexer
        EolComment   (Null             ),
        EncComment   (Null             ),
        DocComment   (Null             ),
        Template     ("{...}"          , Category.Artificial),
        DotDotEx     ("..<"            , Category.Artificial),
        EnumVal      ("enum-value"     , Category.Artificial);

        /**
         * Keyword token ids, but not including context-sensitive keywords.
         */
        static Map<String, Id> keywords =
            {
            HashMap<String, Id> map = new HashMap();
            for ((String text, Id id) : allKeywords)
                {
                if (id.category != ContextSensitive)
                    {
                    map[text] = id;
                    }
                }
            return map.makeImmutable();
            };

        /**
         * All keyword token ids, including context-sensitive keywords.
         */
        static Map<String, Id> allKeywords =
            {
            HashMap<String, Id> map = new HashMap();
            for (Id id : Id.values)
                {
                String? text = id.text;
                if (id.category != Artificial && text != Null
                        && (text[0].category.letter || text[0] == '_'))
                    {
                    map[text] = id;
                    }
                }
            return map.makeImmutable();
            };


        /**
         * String representations of tokens that have both "normal" and "suffixed" representations.
         */
        static Map<String, Id> prefixes =
            {
            HashMap<String, Id> map = new HashMap();
            for ((String text, Id id) : allKeywords)
                {
                if (Int colon := text.indexOf(':'))
                    {
                    String prefix = text[0..colon);
                    if (!map.contains(prefix) && !allKeywords.contains(prefix))
                        {
                        map[text] = id;
                        }
                    }
                }
            return map.makeImmutable();
            };
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Ecstasty identifiers begin with a letter or an underscore.
     *
     * @param ch  the character to test
     *
     * @return True iff the character can be used to begin an Ecstasy identifier
     */
    static Boolean isIdentifierStart(Char ch)
        {
        return ch.category.letter || ch == '_';
        }

    /**
     * Ecstasty identifiers can contain letters, marks, number, and currency symbols, as well as
     * the underscore character.
     *
     * @param ch  the character to test
     *
     * @return True iff the character can be used to begin an Ecstasy identifier
     */
    static Boolean isIdentifierPart(Char ch)
        {
        switch (ch.category)
            {
            case UppercaseLetter:
            case LowercaseLetter:
            case TitlecaseLetter:
            case ModifierLetter:
            case OtherLetter:
            case NonspacingMark:
            case SpacingMark:
            case EnclosingMark:
            case DecimalNumber:
            case LetterNumber:
            case OtherNumber:
            case CurrencySymbol:
                return True;

            default:
                return ch == '_';
            }
        }
    }

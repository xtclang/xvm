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
     * @param source         the Ecstasy source code
     * @param errs           the ErrorList to log errors to
     * @param synthesizeEof  pass True to automatically append a
     */
    construct(String source, ErrorList? errs = Null, Boolean synthesizeEof = False)
        {
        construct Lexer(new Source(source), errs, synthesizeEof);
        }

    /**
     * Construct an Ecstasy lexical analyzer ("tokenizer") that processes source code from a Reader.
     *
     * @param source         the Ecstasy source code
     * @param errs           the ErrorList to log errors to
     * @param synthesizeEof  pass True to automatically append a
     */
    construct(Source source, ErrorList? errs = Null, Boolean synthesizeEof = False)
        {
        this.source        = source;
        this.reader        = source.createReader();
        this.errs          = errs ?: new ErrorList(10);
        this.synthesizeEof = synthesizeEof;
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
        source = parent.source;
        errs   = parent.errs;
        reader = source.createReader();
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
     * True once the stream of characters is exhausted.
     */
    public Boolean eof.get()
        {
        return reader.eof;
        }

    /**
     * The number of characters past the EOF that the lexer has pretended to read.
     */
    protected/private Int pastEOF;

    /**
     * The number of characters past the EOF that the lexer has pretended to read.
     */
    protected/private Boolean synthesizeEof;

    /**
     * A special "end of file" token.
     */
    public/private Token? eofToken;

    /**
     * Keeps track of whether whitespace was encountered.
     */
    protected/private Boolean whitespace;

    /**
     * The ErrorList to log errors to.
     */
    public/private ErrorList errs;


    // ----- Iterator methods ----------------------------------------------------------------------

    @Override
    conditional Token next()
        {
        if (eof)
            {
            if (synthesizeEof && eofToken == Null)
                {
                // once EOF has been reached, the lexer creates a synthetic (fake) token that
                // represents the end-of-file condition; this is useful for matching/demand parsers
                // that don't want to check for an end-of-file condition everywhere
                TextPosition pos      = reader.position;
                Token        eofToken = new Token(EndOfFile, pos, pos, Null, True, True);
                this.eofToken = eofToken;
                return True, eofToken;
                }

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
    protected static const Mark(TextPosition position, Int pastEOF, Boolean whitespace);

    @Override
    immutable Object mark()
        {
        return new Mark(reader.position, pastEOF, whitespace);
        }

    @Override
    void restore(immutable Object mark, Boolean unmark = False)
        {
        assert mark.is(Mark);
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
            immutable Object mark()
                {
                return index;
                }

            @Override
            void restore(immutable Object mark, Boolean unmark = False)
                {
                index = mark.as(Int);
                }
            };
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Lex a single token.
     *
     * @param before  the position of the first character of the token
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
                            case '/':
                                return ParentDir, Null;

                            default:
                                rewind();
                                return DotDot, Null;
                            }

                    case '/':
                        return CurrentDir, Null;

                    case '0'..'9':
                        rewind(2);
                        return eatNumericLiteral(before);

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
                        return Asn, Null;
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
                rewind();
                return eatNumericLiteral(before);

            case '\'':
                return eatCharLiteral(before);

            case '\"':
                return eatStringLiteral(before);

            case '\\':
                switch (nextChar())
                    {
                    case '|':
                        return eatMultilineLiteral(before);

                    default:
                        rewind();
                        break;
                    }
                continue;

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
        StringBuffer nameBuf = new StringBuffer();

        Char next = first;
        do
            {
            nameBuf.add(next);
            next = nextChar();
            }
        while (isIdentifierPart(next));
        rewind();

        String name = nameBuf.toString();
        if (name == Id.Todo.text)
            {
            // the T0D0 keyword has two different lexical modes: an end-of-line comment mode, and
            // a looks-like-a-function-call mode
            if (next == '(')
                {
                return Todo, Null;
                }
            else
                {
                (_, String text) = eatSingleLineComment(before);
                return Todo, text;
                }
            }

        if (next == ':')
            {
            TextPosition colon = reader.position;
            assert nextChar() == ':';

            StringBuffer buf = new StringBuffer();
            while (!eof)
                {
                Char ch = nextChar();
                if (isIdentifierPart(ch))
                    {
                    buf.add(ch);
                    }
                else
                    {
                    rewind();
                    break;
                    }
                }
            String suffix = buf.toString();

            // check for a possible keyword that has different suffixed variants
            if (Id prefixId := Id.prefixes.get(name))
                {
                // check for a legal suffix, e.g. "this:private"
                String full = name + ':' + suffix;
                if (Id fullId := Id.allKeywords.get(full))
                    {
                    return fullId, Null;
                    }

                reader.position = colon;
                return prefixId, Null;
                }

            // check for suffix of private / protected / public / struct (etc.); these will get
            // lexed in some subsequent call to next()
            reader.position = colon;
            if (!Id.keywords.contains(suffix))
                {
                // check for some specific literal type formats
                switch (name)
                    {
                    case "Date":
                        return eatDateLiteral(before);
                    case "TimeOfDay":
                        return eatTimeOfDayLiteral(before);
                    case "DateTime":
                        return eatDateTimeLiteral(before);
                    case "TimeZone":
                        return eatTimeZoneLiteral(before);
                    case "Duration":
                        return eatDurationLiteral(before);
                    case "Version":
                    case "v":
                        return eatVersionLiteral(before);
                    }
                }
            }

        Id? keyword = Id.keywords[name];
        return (keyword?, Null) : (Identifier, name);
        }

    /**
     * Lex a date literal token, starting with the colon.
     *
     * @param before    the position of the first character of the token
     * @param embedded  True indicates that this literal value is part of a larger datetime value
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Date value) eatDateLiteral(TextPosition before, Boolean embedded = False)
        {
        assert embedded || nextChar() == ':';
        TextPosition start = embedded ? reader.position : before;

        Int year  = 0;
        Int month = 0;
        Int day   = 0;

        if (year := eatDigits(4))
            {
            Boolean sep = match('-');
            if (month := eatDigits(2))
                {
                if (!sep || expect('-'))
                    {
                    day := eatDigits(2);
                    }
                }
            }

        if (year < 1582 || month < 1 || month > 12 || day < 1 || day > Date.daysInMonth(year, month))
            {
            TextPosition end  = reader.position;
            String       date = reader[start..end);
            log(Error, BadDate, [date], before, end);
            return LitDate, new Date(1970, 1, 1);
            }

        if (!embedded)
            {
            peekNotIdentifierOrNumber();
            }

        return LitDate, new Date(year, month, day);
        }

    /**
     * Lex a time-of-day literal token, starting with the colon.
     *
     * @param before    the position of the first character of the token
     * @param embedded  True indicates that this literal value is part of a larger datetime value
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, TimeOfDay value) eatTimeOfDayLiteral(TextPosition before, Boolean embedded = False)
        {
        assert embedded || nextChar() == ':';
        TextPosition start = embedded ? reader.position : before;

        Int hours   = 0;
        Int minutes = 0;
        Int seconds = 0;
        Int picos   = 0;

        if (hours := eatDigits(2))
            {
            Boolean colon = match(':');
            if (minutes := eatDigits(2))
                {
                if ((colon && match(':') || !colon && peekDigit()),
                        seconds := eatDigits(2),
                        match('.'))
                    {
                    Int digits = 0;
                    while (Char ch := nextDigit())
                        {
                        if (++digits <= 12)
                            {
                            picos = picos * 10 + (ch - '0');
                            }
                        }

                    if (digits == 0)
                        {
                        // assume that the '.' is part of the next token
                        rewind();
                        }
                    else
                        {
                        // scale the integer value up to picos (trillionths)
                        while (++digits <= 12)
                            {
                            picos *= 10;
                            }
                        }
                    }
                }
            }

        if (!((0 <= hours <= 23 || hours == 24 && minutes == 0 && seconds == 0)
                && (0 <= minutes <= 59)
                && (0 <= seconds <= 59 || minutes == 59 && seconds == 60)))
            {
            TextPosition end       = reader.position;
            String       timeOfDay = reader[start..end);
            log(Error, BadTimeOfDay, [timeOfDay], before, end);
            return LitTimeOfDay, MIDNIGHT;
            }

        if (!embedded)
            {
            peekNotIdentifierOrNumber();
            }

        return LitTimeOfDay, new TimeOfDay(hours, minutes, seconds, picos);
        }

    /**
     * Lex a timezone literal token, starting with the colon.
     *
     * @param before    the position of the first character of the token
     * @param embedded  True indicates that this literal value is part of a larger datetime value
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, TimeZone value) eatTimeZoneLiteral(TextPosition before, Boolean embedded = False)
        {
        assert embedded || nextChar() == ':';
        TextPosition start = embedded ? reader.position : before;

        if (match('Z') || match('z'))
            {
            peekNotIdentifierOrNumber();
            return LitTimezone, UTC;
            }

        Int     hour   = 0;
        Int     minute = 0;
        Boolean minus  = False;
        Boolean legit  = False;
        switch (nextChar())
            {
            case '-':
                minus = True;
                continue;
            case '+':
                if (hour := eatDigits(2))
                    {
                    if (match(':') || peekDigit())
                        {
                        if (minute := eatDigits(2))
                            {
                            legit = True;
                            peekNotIdentifierOrNumber();
                            }
                        }
                    else
                        {
                        legit = True;
                        peekNotIdentifierOrNumber();
                        }
                    }
                break;

            default:
                rewind();
                break;
            }

        if (!legit || hour > 16 || minute > 59)
            {
            TextPosition end      = reader.position;
            String       timezone = reader[start..end);
            log(Error, BadTimezone, [timezone], before, end);
            return LitTimezone, NoTZ;
            }

        Int offset = hour * TimeOfDay.PICOS_PER_HOUR + minute * TimeOfDay.PICOS_PER_MINUTE;
        return LitTimezone, new TimeZone((minus ? -1 : +1) * offset);
        }

    /**
     * Lex a date/time literal token, starting with the colon.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, DateTime value) eatDateTimeLiteral(TextPosition before)
        {
        assert nextChar() == ':';

        (_, Date date)      = eatDateLiteral(before, True);
        TimeOfDay timeOfDay = MIDNIGHT;
        TimeZone  timezone  = NoTZ;

        if (match('t') || expect('T'))
            {
            (_, timeOfDay) = eatTimeOfDayLiteral(before, True);

            switch (peekChar())
                {
                case 'Z', 'z':
                case '+', '-':
                    (_, timezone) = eatTimeZoneLiteral(before, True);
                    break;
                }
            }
        else
            {
            log(Error, BadDatetime, [reader[before..reader.position).toString()], before, reader.position);
            }

        return LitDatetime, new DateTime(date, timeOfDay, timezone);
        }

    /**
     * Lex a time duration literal token, starting with the colon.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Duration value) eatDurationLiteral(TextPosition before)
        {
        assert nextChar() == ':';

        enum Stage(Boolean naked=False) {Init(True), Head(True), Day, Sep(True), Hour, Minute, Second, Fraction, Err}
        Stage prevStage = Init;

        UInt128 picos = 0;
        Boolean any   = False;
        Boolean err   = False;
        Loop: while (True)
            {
            // read the number
            static UInt MAX   = maxvalue / 10 - 1;
            UInt       value  = 0;
            UInt       digits = 0;
            while (Char digit := nextDigit())
                {
                if (value >= MAX)
                    {
                    err = True;
                    }
                else
                    {
                    value = value * 10 + (digit - '0');
                    }
                ++digits;
                }

            // read the label
            Char label = nextChar();
            Stage stage;
            switch (label)
                {
                case 'P', 'p':
                    // the "P" just indicates a duration value
                    stage = Head;
                    break;

                case 'D', 'd':
                    stage  = Day;
                    picos += value * Duration.PICOS_PER_DAY;
                    break;

                case 'T':
                case 't':
                    stage = Sep;
                    break;

                case 'H', 'h':
                    stage  = Hour;
                    picos += value * Duration.PICOS_PER_HOUR;
                    break;

                case 'M', 'm':
                    stage  = Minute;
                    picos += value * Duration.PICOS_PER_MINUTE;
                    break;

                case '.':
                    stage  = Second;
                    picos += value * Duration.PICOS_PER_SECOND;
                    break;

                case 'S', 's':
                    stage  = Fraction;
                    picos += value * (prevStage == Second ? 1 : Duration.PICOS_PER_SECOND);
                    break;

                default:
                    rewind();
                    err = True;
                    break Loop;
                }

            any |= digits > 0;
            if (stage.naked == digits > 0)
                {
                err = True;
                }

            if (stage > prevStage)
                {
                prevStage = stage;
                }
            else
                {
                err = True;
                }
            }

        if (err || !any)
            {
            log(Error, BadDatetime, [reader[before..reader.position).toString()], before, reader.position);
            return LitDuration, NONE;
            }

        return LitDuration, new Duration(picos);
        }

    /**
     * Lex a version literal token, starting with the colon.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Version value) eatVersionLiteral(TextPosition before)
        {
        assert nextChar() == ':';
        TextPosition start = reader.position;

        // eat the VersionNumbers
        Boolean need = True;
        Boolean err  = False;
        while (nextDigit())
            {
            while (nextDigit())
                {
                }

            if (match('.'))
                {
                need = True;
                continue;
                }

            if (match('-'))
                {
                need = True;
                break;
                }

            need = False;
            }

        private Boolean expectCaseInsens(String s)
            {
            for (Char ch : s)
                {
                if (!match(ch) && !expect(ch.uppercase))
                    {
                    return False;
                    }
                }

            if (peekChar().category.letter)
                {
                TextPosition pos = reader.position;
                log(Error, UnexpectedChar, [peekChar().quoted()], pos, pos);
                return False;
                }

            return True;
            }

        Boolean nonGA = True;
        Char    ch    = peekChar();
        switch (ch)
            {
            case 'A', 'a':
                err = !expectCaseInsens("alpha");
                break;

            case 'B', 'b':
                err = !expectCaseInsens("beta");
                break;

            case 'C', 'c':
                err = !expectCaseInsens("ci");
                break;

            case 'D', 'd':
                err = !expectCaseInsens("dev");
                break;

            case 'Q', 'q':
                err = !expectCaseInsens("qa");
                break;

            case 'R', 'r':
                err = !expectCaseInsens("rc");
                break;

            default:
                nonGA = False;
                err  |= need;
                break;
            }

        if (!err && nonGA)
            {
            need = match('.') || match('-');
            if (nextDigit())
                {
                while (nextDigit())
                    {
                    }
                }
            else
                {
                err = need;
                }
            }

        if (err || match('+'))
            {
            // err: expurgate the remainder of the version string, whatever it is
            // no err: eat the build metadata
            Loop: while (!eof)
                {
                switch (nextChar())
                    {
                    case 'A'..'Z':
                    case 'a'..'z':
                    case '0'..'9':
                    case '+', '-':
                    case '.':
                        break;

                    default:
                        rewind();
                        break Loop;
                    }
                }
            }
        else
            {
            switch (peekChar())
                {
                case 'A'..'Z':
                case 'a'..'z':
                case '0'..'9':
                case '-':
                case '.':
                    err = True;
                    break;
                }
            }

        if (err)
            {
            log(Error, BadVersion, [], before, reader.position);
            return LitVersion, new Version("0");
            }

        return LitVersion, new Version(reader[start..reader.position).toString());
        }

    /**
     * Eat a specified number of decimal digits, and convert them to an integer.
     *
     * @param digits  the number of digits to eat
     *
     * @return True iff the specified number of digits was read
     * @return (conditional) the parsed integer value
     */
    protected conditional Int eatDigits(Int digits)
        {
        Int n = 0;
        for (Int i : 1..digits)
            {
            if (Char ch := nextDigit())
                {
                n = n * 10 + (ch - '0');
                }
            else
                {
                Int          actual  = i-1;
                TextPosition after   = reader.position;
                rewind(actual);
                TextPosition before  = reader.position;
                log(Error, ExpectedDigits, [digits, actual], before, after);
                reader.position = after;
                return False;
                }
            }
        return True, n;
        }

    /**
     * Lex a numeric literal token.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatNumericLiteral(TextPosition before)
        {
        (IntLiteral intVal, Int radix) = eatIntLiteral();
        StringBuffer? fpBuf = Null;

        if (match('.'))
            {
            // could be ".."
            if (match('.'))
                {
                rewind(2);
                return LitInt, intVal;
                }

            // could be a mantissa
            if (Char digit := nextDigit(radix))
                {
                fpBuf = new StringBuffer()
                        .addAll(intVal.toString())
                        .add('.')
                        .add(digit);
                while (digit := nextDigit(radix))
                    {
                    fpBuf.add(digit);
                    }
                }
            else
                {
                // it's something else; spit the dot back out and return the int as the value
                rewind();
                return LitInt, intVal;
                }
            }

        // parse optional exponent
        Boolean fpBin = False;
        switch (Char ch = nextChar())
            {
            case 'P', 'p':
                fpBin = True;
                continue;
            case 'E', 'e':
                IntLiteral exp = eatIntLiteral();
                fpBuf ?:= new StringBuffer().addAll(intVal.toString());
                fpBuf.add(ch);
                fpBuf.addAll(exp.toString());
                break;

            default:
                // the next character must be whitespace or some type of operator/separator
                if (isIdentifierPart(ch))
                    {
                    log(Error, IllegalNumber, [fpBuf?.add(ch).toString() : intVal.toString() + ch],
                            before, reader.position);
                    }
                rewind();
                break;
            }

        if (fpBuf == Null)
            {
            return LitInt, intVal;
            }

        return (fpBin ? LitFloat : LitDec), new FPLiteral(fpBuf.toString());
        }

    /**
     * Eat the IntLiteral that occurs next in the source.
     *
     * @return an IntLiteral
     */
    protected (IntLiteral value, Int radix) eatIntLiteral()
        {
        StringBuffer buf   = new StringBuffer();
        TextPosition start = reader.position;

        // the first character could be a sign (+ or -)
        if (match('+'))
            {
            buf.add('+');
            }
        else if (match('-'))
            {
            buf.add('-');
            }

        // if the next character is '0', it is potentially part of a prefix denoting a radix
        Boolean legal = False;
        Int     radix = 10;
        if (Char ch := nextDigit())
            {
            buf.add(ch);
            legal = True;

            if (ch == '0' && !eof)
                {
                radix = switch (ch = nextChar())
                    {
                    case 'B':
                    case 'b': 2;

                    case 'o': 8;

                    case 'X':
                    case 'x': 16;

                    default : 10;
                    };

                if (radix == 10)
                    {
                    // it wasn't a radix indicator
                    rewind();
                    }
                else
                    {
                    buf.add(ch);
                    legal = False;  // requires at least one more digit
                    }
                }

            while (ch := nextDigit(radix))
                {
                buf.add(ch);
                legal = True;
                }
            }

        if (legal)
            {
            return new IntLiteral(buf.toString()), radix;
            }
        else
            {
            log(Error, IllegalNumber, [buf.toString()], start, reader.position);
            return 0, radix;
            }
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
    protected (Id id, Byte[] value) eatBinaryLiteral(TextPosition before, Boolean multiline)
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
     * Eat the remainder of a single line aka end-of-line comment. The opening `//` has already been
     * eaten.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, String value) eatSingleLineComment(TextPosition before)
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
        log(Error, ExpectedEndComment, [], before, reader.position);

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
        return eatStringChars(before, False, False);
        }

    /**
     * Eat the remainder of a multi-line string literal. The opening "\|" has already been eaten.
     *
     * @param before  the position of the first character of the token
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatMultilineLiteral(TextPosition before)
        {
        return eatStringChars(before, False, True);
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
        return eatStringChars(before, True, False);
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
        return eatStringChars(before, True, True);
        }

    /**
     * Lex a string or template literal, single- or multi-line.
     *
     * The opening delimiter has already been eaten.
     *
     * @param before     the position of the first character of the token
     * @param template   True iff the token is a template
     * @param multiline  True iff the token is in the multi-line format
     *
     * @return id     the token id
     * @return value  the token value
     */
    protected (Id id, Object value) eatStringChars(TextPosition before, Boolean template, Boolean multiline)
        {
        StringBuffer buf    = new StringBuffer();
        Token[]      tokens = template ? new Token[] : [];
        TextPosition start  = before;
        Appending: while (True)
            {
            if (!eof)
                {
                Char ch = nextChar();
                switch (ch)
                    {
                    case '\"':
                        if (multiline)
                            {
                            buf.add(ch);
                            break;
                            }
                        break Appending;

                    case '\\':
                        if (multiline)
                            {
                            if (nextChar().isLineTerminator() && isMultilineContinued())
                                {
                                continue Appending;
                                }
                            else
                                {
                                rewind();
                                }

                            if (!template)
                                {
                                buf.add(ch);
                                break;
                                }
                            }

                        // process escaped char
                        switch (ch = nextChar())
                            {
                            case '\\':
                                buf.add('\\');
                                break;

                            case '\'':
                                buf.add('\'');
                                break;

                            case '\"':
                                buf.add('\"');
                                break;

                            case '0':
                                buf.add('\0');
                                break;

                            case 'b':
                                buf.add('\b');
                                break;

                            case 'd':
                                buf.add('\d');
                                break;

                            case 'e':
                                buf.add('\e');
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

                            case 'v':
                                buf.add('\v');
                                break;

                            case 'z':
                                buf.add('\z');
                                break;

                            case '{':
                                if (template)
                                    {
                                    buf.add('{');
                                    break;
                                    }
                                continue;

                            default:
                                if (ch.isLineTerminator())
                                    {
                                    // log error: newline in string
                                    rewind();
                                    log(Error, StringNoTerm, [], start, reader.position);

                                    // assume it wasn't supposed to be an escape
                                    buf.add('\\');
                                    break Appending;
                                    }
                                else
                                    {
                                    // log error: bad escape
                                    TextPosition endEscape = reader.position;
                                    rewind(2);
                                    log(Error, StringBadEsc, [], reader.position, endEscape);
                                    reader.position = endEscape;

                                    // assume it wasn't supposed to be an escape:
                                    // append both the escape char and the escaped char
                                    buf.add('\\')
                                       .add(ch);
                                    break;
                                    }
                            }
                        break;

                    case '{':
                        if (template)
                            {
                            rewind();
                            if (buf.size > 0)
                                {
                                tokens.add(new Token(LitString, start, reader.position, buf.toString()));
                                buf = new StringBuffer();
                                }

                            // eat from the opening { to the closing } (inclusive of both)
                            start = reader.position;
                            Token[] expression = eatTemplateExpression();
                            tokens.add(new Token(Template, start, reader.position, expression));

                            // start eating a new string portion of the template from this point
                            start = reader.position;
                            break;
                            }
                        continue;

                    default:
                        if (ch.isLineTerminator())
                            {
                            if (multiline)
                                {
                                // eat whitespace and look for a continuation character
                                if (isMultilineContinued())
                                    {
                                    // it is a multi-line continuation, so include the newline that
                                    // we just ate in the resulting text
                                    buf.add(ch);
                                    break;
                                    }
                                else
                                    {
                                    // leave the newline in place (it's not part of the multiline)
                                    rewind();
                                    }
                                }
                            else
                                {
                                // not a multi-line literal; log error: newline in string
                                rewind();
                                log(Error, StringNoTerm, [], start, reader.position);
                                }
                            break Appending;
                            }
                        else
                            {
                            buf.add(ch);
                            break;
                            }
                    }
                }
            else
                {
                // log error: unterminated string
                log(Error, StringNoTerm, [], start, reader.position);
                }
            }

        if (template)
            {
            if (buf.size > 0)
                {
                tokens.add(new Token(LitString, start, reader.position, buf.toString()));
                }
            else if (tokens.empty)
                {
                return LitString, "";
                }

            return Template, tokens;
            }

        return LitString, buf.toString();
        }

    /**
     * Eat an inline template expression that begins with a `{` and ends with a `}`. The contents
     * between the opening and closing curlies are returned as an array of tokens.
     *
     * @return the tokens found between the opening and closing curly braces
     */
    protected Token[] eatTemplateExpression()
        {
        expect('{');
        Int     depth  = 1;
        Token[] tokens = new Token[];
        while (!eof)
            {
            assert Token token := next();
            switch (token.id)
                {
                case LeftCurly:
                    ++depth;
                    break;

                case RightCurly:
                    if (--depth <= 0)
                        {
                        if (token.spaceAfter)
                            {
                            // don't steal the whitespace; we're inside a literal!
                            reader.position = token.end;
                            }

                        return tokens;
                        }
                    break;
                }

            tokens.add(token);
            }

        // fortunately, this is very unlikely to occur; a further error will also be expected since
        // whatever is parsing the template will be missing its termination as well
        TextPosition pos = reader.position;
        log(Error, TemplateNoTerm, [], pos, pos);
        return tokens;
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


    // ----- reader behavior -----------------------------------------------------------------------

    private Boolean containsUnicodeEscapes = False;
    private Boolean containsCRLFs          = False;

    /**
     * Obtain the next character without changing the position in the reader.
     *
     * @return the next character, or an EOF character ('\z')
     */
    protected Char peekChar()
        {
        Char ch = nextChar();
        rewind();
        return ch;
        }

    /**
     * Obtain the next character, if any is available.
     *
     * @return the next character, or an EOF character ('\z')
     */
    protected Char nextChar(ErrorMsg? eofError = Null)
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
            if (eofError != Null)
                {
                TextPosition pos = reader.position;
                log(Error, eofError, [], pos, pos);
                }
            ++pastEOF;
            return '\z';
            }
        }

    /**
     * Get the next character of source code if it is a digit of the specified radix.
     *
     * @param radix  the radix of the desired digit
     *
     * @return True iff the next character is a digit of the specified radix
     * @return (conditional) the digit character
     */
    conditional Char peekDigit(Int radix = 10)
        {
        if (Char ch := nextDigit(radix))
            {
            rewind();
            return True, ch;
            }

        return False;
        }

    /**
     * Get the next character of source code if it is a digit of the specified radix.
     *
     * @param radix  the radix of the desired digit
     *
     * @return True iff the next character is a digit of the specified radix
     * @return (conditional) the digit character
     */
    conditional Char nextDigit(Int radix = 10)
        {
        if (!eof)
            {
            switch (Char ch = nextChar())
                {
                case '0'..'1':
                    return True, ch;

                case '2'..'7':
                    if (radix >= 8)
                        {
                        return True, ch;
                        }
                    break;

                case '8'..'9':
                    if (radix >= 10)
                        {
                        return True, ch;
                        }
                    break;

                case 'A'..'F':
                case 'a'..'f':
                    if (radix >= 16)
                        {
                        return True, ch;
                        }
                    break;
                }
            rewind();
            }

        return False;
        }

    /**
     * Verify that the next character is NOT a number or an identifier char.
     */
    protected void peekNotIdentifierOrNumber()
        {
        Char ch = peekChar();
        if (ch.asciiDigit() || isIdentifierPart(ch))
            {
            TextPosition pos = reader.position;
            log(Error, UnexpectedChar, [ch.quoted()], pos, pos);
            }
        }

    /**
     * Get the next character of source code, making sure that it is the specified character.
     *
     * @param ch  the desired character
     *
     * @return True iff the next character was matched
     */
    protected Boolean match(Char ch)
        {
        if (eof)
            {
            TextPosition pos = reader.position;
            log(Error, UnexpectedEof, [], pos, pos);
            return False;
            }

        Char actual = nextChar();
        if (actual != ch)
            {
            rewind();
            return False;
            }

        return True;
        }

    /**
     * Get the next character of source code, making sure that it is the specified character.
     *
     * @param ch  the expected character
     *
     * @return True iff the expected character was found
     */
    protected Boolean expect(Char ch)
        {
        if (eof)
            {
            TextPosition pos = reader.position;
            log(Error, UnexpectedEof, [], pos, pos);
            return False;
            }

        Char actual = nextChar();
        if (actual != ch)
            {
            TextPosition after = reader.position;
            rewind();
            log(Error, ExpectedChar, [ch.quoted(), actual.quoted()], reader.position, after);
            return False;
            }

        return True;
        }

    /**
     * Rewind the specified number of characters, default to one.
     *
     * @param count  the number of characters to rewind
     */
    protected void rewind(Int count = 1)
        {
        if (count == 0)
            {
            return;
            }

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
                // if the character is an \n and containsCRLFs is True, or if the character is a
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
        return errs.log(new Error(severity, errmsg.code, ErrorMsg.lookup, params, source, before, after));
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
        ExpectedEndComment("LEXER-02", "Expected a comment-ending \"star slash\" but never found one."),
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
        BadTimeOfDay      ("LEXER-14", "Invalid ISO-8601 time \"{0}\"; time-of-day must be in the format \"hh:mm:ss.sss\" or \"hhmmss.sss\" (with seconds and fractions of seconds optional) with valid values for each."),
        BadDatetime       ("LEXER-15", "Invalid ISO-8601 datetime \"{0}\"; datetime must be in the format date+\"T\"+timeOfDay+timezone (with timezone optional), with valid values for each."),
        BadTimezone       ("LEXER-16", "Invalid ISO-8601 timezone \"{0}\"; timezone must be \"Z\" (for UTC), or in the format \"+hh:mm\" or \"+hhmm\" (using either \"+\" or \"-\", and with minutes optional) with valid values for each."),
        BadDuration       ("LEXER-17", "Invalid ISO-8601 duration \"{0}\"; duration must be in the format \"PnYnMnDTnHnMnS\" (with the year, month, day, and time-of-day value optional, and the hours, minutes, and seconds values optional within the time portion), with valid values for each."),
        UnexpectedChar    ("LEXER-18", "Unexpected character: \"{0}\"."),
        TemplateNoTerm    ("LEXER-19", "Template is not terminated."),
        BadVersion        ("PARSER-04", "Bad version value.");

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
     * An Ecstasy token has a lexical identity (what the element type is), a location in the text
     * stream (with the ending position being the position _after_ the token), and an optional
     * value.
     */
    static const Token
           (Id           id,
            TextPosition start,
            TextPosition end,
            Object       value,
            Boolean      spaceBefore = False,
            Boolean      spaceAfter  = False)
        {
        assert()
            {
            assert value.is(id.Value);
            }

        /**
         * @return the value's text, or if there is no value, then the token id's text
         */
        String valueText.get()
            {
            return value == Null
                    ? (id.text? : "")
                    : value.toString();
            }

        /**
         * @return a longer form of the `toString()` output that includes the token's position
         */
        public String toDebugString()
            {
            String       s   = toString();
            StringBuffer buf = new StringBuffer(s.size + 18);

            buf.add('[');
            (start.lineNumber + 1).appendTo(buf);
            buf.add(',');
            (start.lineOffset + 1).appendTo(buf);
            " - ".appendTo(buf);
            (end.lineNumber + 1).appendTo(buf);
            buf.add(',');
            (end.lineOffset + 1).appendTo(buf);
            "] ".appendTo(buf);
            s.appendTo(buf);

            return buf.toString();
            }

        @Override
        public String toString()
            {
            return switch (id)
                {
                case LitInt:
                case LitDec:
                case LitFloat:
                case LitPath:       value.toString();

                case LitChar:       value.as(Char).quoted();
                case LitString:     value.as(String).quoted();

                case LitDate:      "Date:"      + value.toString();
                case LitTimeOfDay: "TimeOfDay:" + value.toString();
                case LitDatetime:  "DateTime:"  + value.toString();
                case LitTimezone:  "TimeZone:"  + value.toString();
                case LitDuration:  "Duration:"  + value.toString();
                case LitVersion:   "Version:"   + value.toString();

                case LitBinstr:
                    {
                    // TODO most of this should be on Byte[] or Nibble[]
                    Byte[]       bytes = value.as(Byte[]);
                    StringBuffer buf   = new StringBuffer((1+bytes.size * 2).minOf(37));
                    buf.append('#');
                    Loop: for (Byte b : bytes)
                        {
                        if (Loop.count >= 17)
                            {
                            buf.add('.').add('.');
                            break;
                            }
                        buf.add((b >>> 4).toHexit())
                           .add((b      ).toHexit());
                        }
                    return buf.toString();
                    };


                case Identifier:  Id.allKeywords.contains(value.as(String))
                                          ? '$' + value.toString()
                                          : value.toString();

                case DocComment:  value.is(String) ? "/** " + (value.size > 35 ? value[0..33]+".. */" : value + " */") : "/** */";
                case EncComment:  value.is(String) ? "/* "  + (value.size > 35 ? value[0..33]+".. */" : value + " */") : "/* */";
                case EolComment:  value.is(String) ? "// "  + (value.size > 35 ? value[0..33]+".."    : value        ) : "//";

                default: id.text ?: "???";
                };
            }
        }

    /**
     * Token identity categories.
     */
    enum Category {Normal, ContextSensitive, Special, Artificial}

    /**
     * Ecstasy source code is composed of these lexical elements.
     */
    enum Id<Value>(String? text, Category category=Normal)
        {
        Colon        <Object    >(":"              ),
        Semicolon    <Object    >(";"              ),
        Comma        <Object    >(","              ),
        Dot          <Object    >("."              ),
        DotDot       <Object    >(".."             ),
        CurrentDir   <Object    >("./"             ),
        ParentDir    <Object    >("../"            ),
        LeftParen    <Object    >("("              ),
        RightParen   <Object    >(")"              ),
        LeftCurly    <Object    >("{"              ),
        RightCurly   <Object    >("}"              ),
        LeftSquare   <Object    >("["              ),
        RightSquare  <Object    >("]"              ),
        Add          <Object    >("+"              ),
        Sub          <Object    >("-"              ),
        Mul          <Object    >("*"              ),
        Div          <Object    >("/"              ),
        DivRem       <Object    >("/%"             ),
        Modulo       <Object    >("%"              ),
        ShiftLeft    <Object    >("<<"             ),
        ShiftRight   <Object    >(">>"             ),
        ShiftAll     <Object    >(">>>"            ),
        BitAnd       <Object    >("&"              ),
        BitOr        <Object    >("|"              ),
        BitXor       <Object    >("^"              ),
        BitNot       <Object    >("~"              ),
        BoolAnd      <Object    >("&&"             ),
        BoolOr       <Object    >("||"             ),
        BoolXor      <Object    >("^^"             ),
        BoolNot      <Object    >("!"              ),
        BinFile      <Object    >("#"              ),
        StrFile      <Object    >("$"              ),
        At           <Object    >("@"              ),
        Condition    <Object    >("?"              ),
        Elvis        <Object    >("?:"             ),
        Asn          <Object    >("="              ),
        AddAsn       <Object    >("+="             ),
        SubAsn       <Object    >("-="             ),
        MulAsn       <Object    >("*="             ),
        DivAsn       <Object    >("/="             ),
        ModuloAsn    <Object    >("%="             ),
        ShiftLeftAsn <Object    >("<<="            ),
        ShiftRightAsn<Object    >(">>="            ),
        ShiftAllAsn  <Object    >(">>>="           ),
        BitAndAsn    <Object    >("&="             ),
        BitOrAsn     <Object    >("|="             ),
        BitXorAsn    <Object    >("^="             ),
        BoolAndAsn   <Object    >("&&="            ),
        BoolOrAsn    <Object    >("||="            ),
        CondAsn      <Object    >(":="             ),
        NotNullAsn   <Object    >("?="             ),
        ElvisAsn     <Object    >("?:="            ),
        CompareEQ    <Object    >("=="             ),
        CompareNE    <Object    >("!="             ),
        CompareLT    <Object    >("<"              ),
        CompareLTEQ  <Object    >("<="             ),
        CompareGT    <Object    >(">"              ),
        CompareGTEQ  <Object    >(">="             ),
        CompareOrder <Object    >("<=>"            ),
        Increment    <Object    >("++"             ),
        Decrement    <Object    >("--"             ),
        Lambda       <Object    >("->"             ),
        Any          <Object    >("_"              ),
        Allow        <Object    >("allow"          , ContextSensitive),
        As           <Object    >("as"             ),
        Assert       <Object    >("assert"         ),
        AssertRnd    <Object    >("assert:rnd"     ),
        AssertArg    <Object    >("assert:arg"     ),
        AssertBounds <Object    >("assert:bounds"  ),
        AssertTodo   <Object    >("assert:TODO"    ),
        AssertOnce   <Object    >("assert:once"    ),
        AssertTest   <Object    >("assert:test"    ),
        AssertDebug  <Object    >("assert:debug"   ),
        Avoid        <Object    >("avoid"          , ContextSensitive),
        Break        <Object    >("break"          ),
        Case         <Object    >("case"           ),
        Catch        <Object    >("catch"          ),
        Class        <Object    >("class"          ),
        Conditional  <Object    >("conditional"    ),
        Const        <Object    >("const"          ),
        Construct    <Object    >("construct"      ),
        Continue     <Object    >("continue"       ),
        Default      <Object    >("default"        ),
        Delegates    <Object    >("delegates"      , ContextSensitive),
        Do           <Object    >("do"             ),
        Else         <Object    >("else"           ),
        Enum         <Object    >("enum"           ),
        Extends      <Object    >("extends"        , ContextSensitive),
        Finally      <Object    >("finally"        ),
        For          <Object    >("for"            ),
        Function     <Object    >("function"       ),
        If           <Object    >("if"             ),
        Immutable    <Object    >("immutable"      ),
        Implements   <Object    >("implements"     , ContextSensitive),
        Import       <Object    >("import"         ),
        ImportEmbed  <Object    >("import:embedded"),
        ImportRequire<Object    >("import:required"),
        ImportDesire <Object    >("import:desired" ),
        ImportOption <Object    >("import:optional"),
        Incorporates <Object    >("incorporates"   , ContextSensitive),
        Interface    <Object    >("interface"      ),
        Into         <Object    >("into"           , ContextSensitive),
        Is           <Object    >("is"             ),
        Mixin        <Object    >("mixin"          ),
        Module       <Object    >("module"         ),
        New          <Object    >("new"            ),
        Outer        <Object    >("outer"          , Special),
        Package      <Object    >("package"        ),
        Prefer       <Object    >("prefer"         , ContextSensitive),
        Private      <Object    >("private"        ),
        Protected    <Object    >("protected"      ),
        Public       <Object    >("public"         ),
        Return       <Object    >("return"         ),
        Service      <Object    >("service"        ),
        Static       <Object    >("static"         ),
        Struct       <Object    >("struct"         ),
        Super        <Object    >("super"          , Special),
        Switch       <Object    >("switch"         ),
        This         <Object    >("this"           , Special),
        ThisClass    <Object    >("this:class"     , Special),
        ThisModule   <Object    >("this:module"    , Special),
        ThisPri      <Object    >("this:private"   , Special),
        ThisPro      <Object    >("this:protected" , Special),
        ThisPub      <Object    >("this:public"    , Special),
        ThisServ     <Object    >("this:service"   , Special),
        ThisStruct   <Object    >("this:struct"    , Special),
        ThisTarget   <Object    >("this:target"    , Special),
        Throw        <Object    >("throw"          ),
        Todo         <String?   >("TODO"           ),
        Try          <Object    >("try"            ),
        Typedef      <Object    >("typedef"        ),
        Using        <Object    >("using"          ),
        Val          <Object    >("val"            , ContextSensitive),
        Var          <Object    >("var"            , ContextSensitive),
        Void         <Object    >("void"           ),
        While        <Object    >("while"          ),
        Identifier   <String    >(Null             ),
        LitChar      <Char      >(Null             ),
        LitString    <String    >(Null             ),
        LitBinstr    <Byte[]    >(Null             ),
        LitInt       <IntLiteral>(Null             ),
        LitDec       <FPLiteral >(Null             ),
        LitFloat     <FPLiteral >(Null             ),
        LitDate      <Date      >(Null             ),
        LitTimeOfDay <TimeOfDay >(Null             ),
        LitDatetime  <DateTime  >(Null             ),
        LitTimezone  <TimeZone  >(Null             ),
        LitDuration  <Duration  >(Null             ),
        LitVersion   <Version   >(Null             ),
        LitPath      <Path      >(Null             ),       // generated by Parser, not Lexer
        EolComment   <String?   >(Null             ),
        EncComment   <String?   >(Null             ),
        DocComment   <String?   >(Null             ),
        Template     <Object    >("{...}"          , Artificial),
        DotDotEx     <Object    >("..<"            , Artificial),
        EnumVal      <Object    >("enum-value"     , Artificial),
        EndOfFile    <Object    >("<--EOF-->"      , Artificial);

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
     * Ecstasy identifiers begin with a letter or an underscore.
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
     * Ecstasy identifiers can contain letters, marks, number, and currency symbols, as well as
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

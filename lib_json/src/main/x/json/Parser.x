import ecstasy.Markable;

import ecstasy.io.EndOfFile;
import ecstasy.io.Reader;

import Lexer.Id;
import Lexer.Token;

/**
 * A parser for a JSON document. Turns the output of a JSON Lexer into a JSON Doc.
 */
class Parser
        implements AnyParser
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a JSON parser that processes a document from a Reader.
     *
     * @param reader  the source of the JSON document text
     */
    construct(Reader reader)
        {
        construct Parser(new Lexer(reader));
        }

    /**
     * Construct a JSON parser that processes a document from a Lexer.
     *
     * @param lexer  a JSON Lexer
     */
    construct(Iterator<Token> lexer)
        {
        lexer       = lexer.ensureMarkable();
        this.lexer  = lexer;
        this.token := lexer.next();
        }

    /**
     * Construct a JSON parser that processes a document from a Lexer and a first pre-primed token.
     *
     * @param lexer  a JSON Lexer
     * @param token  the first token (previously primed from the Lexer)
     */
    construct(Iterator<Token> lexer, Token token)
        {
        this.lexer = lexer.ensureMarkable();
        this.token = token;
        }

    /**
     * Internal constructor used by sub-classes.
     */
    protected construct(Parser raw)
        {
        this.lexer = raw.lexer;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying [Lexer].
     */
    protected/private Markable + Iterator<Token> lexer;

    /**
     * The next [Token] to process.
     */
    protected/private Token? token = Null;

    /**
     * True causes the values for duplicate identical names inside a JSON object to be collated
     * together as an array. False retains only the last value for the duplicated name.
     *
     * Consider the following example:
     *
     *     {
     *     "name" : "Ralph",
     *     "name" : "George"
     *     }
     *
     * In the case of collateDups=False, this will produce:
     *
     *     [name=George]
     *
     * In the case of collateDups=True, this will produce:
     *
     *     [name=[Ralph,George]]
     */
    Boolean collateDups;

    /**
     * Determine if the parser has encountered the end of file (no tokens left).
     */
    @Override
    Boolean eof.get()
        {
        return token == Null;
        }


    // ----- Markable ------------------------------------------------------------------------------

    protected static const Mark(immutable Object mark, Token? token);

    @Override
    immutable Object mark()
        {
        return new Mark(lexer.mark(), token);
        }

    @Override
    void restore(immutable Object mark, Boolean unmark = False)
        {
        assert mark.is(Mark);
        lexer.restore(mark.mark);
        token = mark.token;

        if (unmark)
            {
            this.unmark(mark);
            }
        }

    @Override
    void unmark(Object mark)
        {
        assert mark.is(Mark);
        lexer.unmark(mark.mark);
        }


    // ----- Iterator ------------------------------------------------------------------------------

    @Override
    conditional Doc next()
        {
        return eof
                ? False
                : (True, parseDoc());
        }


    // ----- other ---------------------------------------------------------------------------------

    /**
     * Skip over the next JSON document.
     *
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return the array that was passed in, if one was passed in, otherwise an empty array
     */
    @Override
    Token[] skip(Token[]? skipped = Null)
        {
        if (!eof)
            {
            skipDoc(skipped);
            }

        return skipped ?: [];
        }

    /**
     * Skip over all remaining JSON documents.
     *
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return the array that was passed in, if one was passed in, otherwise an empty array
     */
    @Override
    Token[] skipRemaining(Token[]? skipped = Null)
        {
        while (!eof)
            {
            skip(skipped);
            }

        return skipped ?: [];
        }


    // ----- parsing (advanced) --------------------------------------------------------------------

    /**
     * Parse a JSON value, which is called a "document" here. A JSON value can be an individual
     * value, an array of JSON values, or a JSON object, which is a sequence of name/value pairs.
     *
     * @return a JSON value
     */
    @Override
    Doc parseDoc()
        {
        switch (token?.id)
            {
            case NoVal:
            case BoolVal:
            case IntVal:
            case FPVal:
            case StrVal:
                return takeToken().value;

            case ArrayEnter:
                return parseArray();

            case ObjectEnter:
                return parseObject();
            }

        throw eof
                ? new EndOfFile()
                : new IllegalJSON($"unexpected token: {token}");
        }

    /**
     * Skip a JSON value, which is called a "document" here. A JSON value can be an individual
     * value, an array of JSON values, or a JSON object, which is a sequence of name/value pairs.
     *
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return the first token of the JSON document skipped
     * @return the last token of the JSON document skipped
     */
    @Override
    (Token first, Token last) skipDoc(Token[]? skipped = Null)
        {
        switch (token?.id)
            {
            case NoVal:
            case BoolVal:
            case IntVal:
            case FPVal:
            case StrVal:
                Token value = takeToken();
                skipped?.add(value);
                return value, value;

            case ArrayEnter:
                return skipArray(skipped);

            case ObjectEnter:
                return skipObject(skipped);
            }

        throw eof ? new EndOfFile()
                  : new IllegalJSON($"unexpected token: {token}");
        }

    /**
     * Test for the next document being a Boolean, and if so, return that Boolean value.
     *
     * @return True iff the next document is a Boolean value
     * @return (conditional) the Boolean value
     */
    conditional Boolean matchBoolean()
        {
        if (token?.id == BoolVal)
            {
            return True, parseDoc().as(Boolean);
            }

        return False;
        }

    /**
     * Obtain the next document as a Boolean, or throw a parsing exception.
     *
     * @return the Boolean value
     */
    Boolean expectBoolean()
        {
        if (Boolean value := matchBoolean())
            {
            return value;
            }

        throw new IllegalJSON($"JSON parsing error: Boolean expected, but {token?.id.toString() : "EOF"} encountered.");
        }

    /**
     * Test for the next document being an IntLiteral, and if so, return that IntLiteral value.
     *
     * @return True iff the next document is a IntLiteral value
     * @return (conditional) the IntLiteral value
     */
    conditional IntLiteral matchIntLiteral()
        {
        if (token?.id == IntVal)
            {
            return True, parseDoc().as(IntLiteral);
            }

        return False;
        }

    /**
     * Obtain the next document as an IntLiteral, or throw a parsing exception.
     *
     * @return the IntLiteral value
     */
    IntLiteral expectIntLiteral()
        {
        if (IntLiteral value := matchIntLiteral())
            {
            return value;
            }

        throw new IllegalJSON($"JSON parsing error: IntLiteral expected, but {token?.id.toString() : "EOF"} encountered.");
        }

    /**
     * Test for the next document being an Int, and if so, return that Int value.
     *
     * @return True iff the next document is a Int value
     * @return (conditional) the Int value
     */
    conditional Int matchInt()
        {
        if (token?.id == IntVal)
            {
            return True, parseDoc().as(IntLiteral).toInt64();
            }

        return False;
        }

    /**
     * Obtain the next document as an Int, or throw a parsing exception.
     *
     * @param range  (optional) the range that the value is expected to be within
     *
     * @return the Int value
     */
    Int expectInt(Range<Int>? range = Null)
        {
        if (Int value := matchInt())
            {
            if (!range?.contains(value))
                {
                throw new IllegalJSON($"JSON parsing error: An Int value in the range {range} was expected, but {value} encountered.");
                }

            return value;
            }

        throw new IllegalJSON($"JSON parsing error: Int expected, but {token?.id.toString() : "EOF"} encountered.");
        }

    /**
     * Test for the next document being a FPLiteral, and if so, return that FPLiteral value.
     *
     * @return True iff the next document is a FPLiteral value
     * @return (conditional) the FPLiteral value
     */
    conditional FPLiteral matchFPLiteral()
        {
        if (token?.id == FPVal)
            {
            return True, parseDoc().as(FPLiteral);
            }

        if (token?.id == IntVal)
            {
            return True, parseDoc().as(IntLiteral).toFPLiteral();
            }

        return False;
        }

    /**
     * Obtain the next document as a FPLiteral, or throw a parsing exception.
     *
     * @return the FPLiteral value
     */
    FPLiteral expectFPLiteral()
        {
        if (FPLiteral value := matchFPLiteral())
            {
            return value;
            }

        throw new IllegalJSON($"JSON parsing error: FPLiteral expected, but {token?.id.toString() : "EOF"} encountered.");
        }

    /**
     * Test for the next document being a decimal value, and if so, return that decimal value.
     *
     * @return True iff the next document is a decimal value
     * @return (conditional) the decimal value
     */
    conditional Dec matchDec()
        {
        if (token?.id == FPVal)
            {
            return True, parseDoc().as(FPLiteral).toDec64();
            }

        if (token?.id == IntVal)
            {
            return True, parseDoc().as(IntLiteral).toDec64();
            }

        return False;
        }

    /**
     * Obtain the next document as a decimal value, or throw a parsing exception.
     *
     * @param range  (optional) the range that the value is expected to be within
     *
     * @return the decimal value
     */
    Dec expectDec(Range<Dec>? range = Null)
        {
        if (Dec value := matchDec())
            {
            if (!range?.contains(value))
                {
                throw new IllegalJSON($"JSON parsing error: A decimal value in the range {range} was expected, but {value} encountered.");
                }

            return value;
            }

        throw new IllegalJSON($"JSON parsing error: Decimal value expected, but {token?.id.toString() : "EOF"} encountered.");
        }

    /**
     * Test for the next document being a Double, and if so, return that Double value.
     *
     * @return True iff the next document is a Double value
     * @return (conditional) the Double value
     */
    conditional Double matchDouble()
        {
        if (token?.id == FPVal)
            {
            return True, parseDoc().as(FPLiteral).toFloat64();
            }

        if (token?.id == IntVal)
            {
            return True, parseDoc().as(IntLiteral).toFloat64();
            }

        return False;
        }

    /**
     * Obtain the next document as a Double, or throw a parsing exception.
     *
     * @param range  (optional) the range that the value is expected to be within
     *
     * @return the Double value
     */
    Double expectDouble(Range<Double>? range = Null)
        {
        if (Double value := matchDouble())
            {
            if (!range?.contains(value))
                {
                throw new IllegalJSON($"JSON parsing error: A Double value in the range {range} was expected, but {value} encountered.");
                }

            return value;
            }

        throw new IllegalJSON($"JSON parsing error: Double expected, but {token?.id.toString() : "EOF"} encountered.");
        }

    /**
     * Test for the next document being a String, and if so, return that String value.
     *
     * @return True iff the next document is a String value
     * @return (conditional) the String value
     */
    conditional String matchString()
        {
        if (token?.id == StrVal)
            {
            return True, parseDoc().as(String);
            }

        return False;
        }

    /**
     * Obtain the next document as a String, or throw a parsing exception.
     *
     * @return the String value
     */
    String expectString()
        {
        if (String value := matchString())
            {
            return value;
            }

        throw new IllegalJSON($"JSON parsing error: String expected, but {token?.id.toString() : "EOF"} encountered.");
        }

    /**
     * Parse an array of JSON values.
     *
     * @return an array of JSON values
     */
    @Override
    Array<Doc> parseArray()
        {
        Doc[] array = new Doc[];
        expect(ArrayEnter);
        if (!match(ArrayExit))
            {
            do
                {
                array += parseDoc();
                }
            while (match(Comma));
            expect(ArrayExit);
            }
        return array;
        }

    /**
     * Skip an array of JSON values.
     *
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return the first token of the JSON array skipped
     * @return the last token of the JSON array skipped
     */
    @Override
    (Token first, Token last) skipArray(Token[]? skipped = Null)
        {
        Token first = expect(ArrayEnter, skipped);
        Token last;

        if (!(last := match(ArrayExit, skipped)))
            {
            do
                {
                skipDoc(skipped);
                }
            while (match(Comma, skipped));
            last = expect(ArrayExit, skipped);
            }

        return first, last;
        }

    /**
     * Test to see if the next token is the start of an array.
     *
     * This method is intended to be used with a `using` statement, such as:
     *
     *     if (ArrayParser arrayParser = parser.matchArray())
     *         {
     *         using (arrayParser)
     *             {
     *             console.println("Array contents:");
     *             while (!arrayParser.eof)
     *                 {
     *                 Int index = arrayParser.index;
     *                 Doc doc   = arrayParser.parseDoc();
     *                 console.println($"[{index}] = {doc}");
     *                 }
     *             }
     *         }
     *
     * While the ArrayParser is not yet closed, the parser from which it came must not be used,
     * or the parse stream may be corrupted. Always make sure to close the returned ArrayParser.
     *
     * @return True iff the next token was an opening square bracket of an array
     * @return (conditional) the opening square bracket token
     */
    @Override
    conditional ArrayParser matchArray()
        {
        return match(ArrayEnter)
                ? (True, new ArrayParser(this))
                : False;
        }

    /**
     * Obtain the next token, which must be the opening square bracket of an array.
     *
     * This method is intended to be used within a `using` statement, such as:
     *
     *     using (ArrayParser arrayParser = parser.expectArray())
     *         {
     *         console.println("Array contents:");
     *         while (!arrayParser.eof)
     *             {
     *             Int index = arrayParser.index;
     *             Doc doc   = arrayParser.parseDoc();
     *             console.println($"[{index}] = {doc}");
     *             }
     *         }
     *
     * While the ArrayParser is not yet closed, the parser from which it came must not be used,
     * or the parse stream may be corrupted. Always make sure to close the returned ArrayParser.
     *
     * @return the opening square bracket token
     */
    @Override
    ArrayParser expectArray()
        {
        expect(ArrayEnter);
        return new ArrayParser(this);
        }

    /**
     * Parse a JSON object, which is a sequence of name/value pairs.
     *
     * @return a Map representing a JSON object
     */
    @Override
    Map<String, Doc> parseObject()
        {
        ListMap<String, Doc> map = new ListMap();
        expect(ObjectEnter);
        if (!match(ObjectExit))
            {
            Set<String>? dups = Null;
            do
                {
                String name = expect(StrVal).value.as(String);
                Token  sep  = expect(Colon);
                Doc    doc  = parseDoc();
                if (collateDups)
                    {
                    map.process(name, entry ->
                        {
                        if (entry.exists)
                            {
                            // there is a duplicate name, which is not explicitly forbidden by the
                            // JSON spec, so store the values that share this name in an array
                            if (dups == Null)
                                {
                                dups = new HashSet<String>();
                                }
                            Doc[] values = dups.addIfAbsent(entry.key)
                                    ? (new Doc[]).add(entry.value)
                                    : entry.value.as((Doc[]));
                            entry.value = values.add(doc);
                            }
                        else
                            {
                            entry.value = doc;
                            }
                        return Null;
                        });
                    }
                else
                    {
                    map.put(name, doc);
                    }
                }
            while (match(Comma));
            expect(ObjectExit);
            }
        return map;
        }

    /**
     * Skip a JSON object, which is a sequence of name/value pairs.
     *
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return the first token of the JSON object skipped
     * @return the last token of the JSON object skipped
     */
    @Override
    (Token first, Token last) skipObject(Token[]? skipped = Null)
        {
        Token first = expect(ObjectEnter, skipped);
        Token last;

        if (!(last := match(ObjectExit, skipped)))
            {
            do
                {
                expect(StrVal, skipped);
                expect(Colon, skipped);
                skipDoc(skipped);
                }
            while (match(Comma, skipped));
            last = expect(ObjectExit, skipped);
            }

        return first, last;
        }

    /**
     * Test to see if the next token is the start of an object.
     *
     * @return True iff the next token was an opening brace of an object
     * @return (conditional) the opening brace token
     */
    @Override
    conditional ObjectParser matchObject()
        {
        return match(ObjectEnter)
                ? (True, new ObjectParser(this))
                : False;
        }

    /**
     * Obtain the next token, which must be the opening brace of a document.
     *
     * @return the opening brace token
     */
    @Override
    ObjectParser expectObject()
        {
        expect(ObjectEnter);
        return new ObjectParser(this);
        }


    // ----- token handling (advanced) -------------------------------------------------------------

    /**
     * Obtain the next [Token] **without** advancing to the next token.
     *
     * This method is primarily intended for use within the parser, but available from outside of
     * the parser to support complex use cases.
     *
     * @return the token
     */
    @Override
    Token peek()
        {
        return token?;
        throw new IllegalJSON("Unexpected EOF");
        }

    /**
     * Take the next [Token]. This automatically loads a new "next token", if one exists.
     *
     * This method is primarily intended for use within the parser, but available from outside of
     * the parser to support complex use cases. Using this method requires knowledge of the parsing
     * implementation, and thus may leave the parser in an unusable state.
     *
     * @return the token
     */
    @Override
    Token takeToken()
        {
        Token? token = this.token;
        advance();
        return token ?: throw new IllegalJSON("Unexpected EOF");
        }

    /**
     * Replace the next [Token] with the token that follows it, or Null if there are no more tokens.
     *
     * This method is primarily intended for use within the parser, but available from outside of
     * the parser to support complex use cases. Using this method requires knowledge of the parsing
     * implementation, and thus may leave the parser in an unusable state.
     */
    @Override
    void advance()
        {
        Token? next = Null;
        next := lexer.next();
        this.token = next;
        }

    /**
     * Obtain the next token, which is expected to have the specified [Token.Id].
     *
     * This method is primarily intended for use within the parser, but available from outside of
     * the parser to support complex use cases. Using this method requires knowledge of the parsing
     * implementation, and thus may leave the parser in an unusable state.
     *
     * @param id  the expected [Token.Id] of the next token
     *
     * @return the expected Token
     *
     * @throws IllegalJSON if the expected Token is not the next token
     */
    @Override
    Token expect(Id id)
        {
        if (Token token := match(id))
            {
            return token;
            }

        throw new IllegalJSON($"JSON format error: {id} expected, but {this.token?.id.toString() : "EOF"} encountered.");
        }

    /**
     * Obtain the next token, which is expected to have the specified [Token.Id].
     *
     * This method is primarily intended for use within the parser, but available from outside of
     * the parser to support complex use cases. Using this method requires knowledge of the parsing
     * implementation, and thus may leave the parser in an unusable state.
     *
     * @param id       the expected [Token.Id] of the next token
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return the expected Token
     *
     * @throws IllegalJSON if the expected Token is not the next token
     */
    @Override
    Token expect(Id id, Token[]? skipped)
        {
        Token token = expect(id);
        skipped?.add(token);
        return token;
        }

    /**
     * Test if the next [Token] is of the specified [Token.Id], and if it is, take it.
     *
     * This method is primarily intended for use within the parser, but available from outside of
     * the parser to support complex use cases. Using this method requires knowledge of the parsing
     * implementation, and thus may leave the parser in an unusable state.
     *
     * @param id  the [Token.Id] to use to determine if the next Token is the desired match
     *
     * @return True iff the next Token has the specified Id
     * @return (conditional) the matching Token
     */
    @Override
    conditional Token match(Id id)
        {
        Token? token = this.token;
        if (token == Null)
            {
            throw new EndOfFile();
            }

        if (token.id == id)
            {
            advance();
            return True, token;
            }

        return False;
        }

    /**
     * Test if the next [Token] is of the specified [Token.Id], and if it is, take it.
     *
     * This method is primarily intended for use within the parser, but available from outside of
     * the parser to support complex use cases. Using this method requires knowledge of the parsing
     * implementation, and thus may leave the parser in an unusable state.
     *
     * @param id       the [Token.Id] to use to determine if the next Token is the desired match
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return True iff the next Token has the specified Id
     * @return (conditional) the matching Token
     */
    @Override
    conditional Token match(Id id, Token[]? skipped)
        {
        if (Token token := match(id))
            {
            skipped?.add(token);
            return True, token;
            }

        return False;
        }


    // ----- nested parser support -----------------------------------------------------------------

    /**
     * The entire parsing API of the Parser class.
     */
    static interface AnyParser
            extends Iterator<Doc>
            extends Markable
            extends Closeable
        {
        @RO Boolean eof;
        Token[] skip(Token[]? skipped = Null);
        Token[] skipRemaining(Token[]? skipped = Null);
        Doc parseDoc();
        (Token first, Token last) skipDoc(Token[]? skipped = Null);
        Array<Doc> parseArray();
        (Token first, Token last) skipArray(Token[]? skipped = Null);
        conditional ArrayParser matchArray();
        ArrayParser expectArray();
        Map<String, Doc> parseObject();
        (Token first, Token last) skipObject(Token[]? skipped = Null);
        conditional ObjectParser matchObject();
        ObjectParser expectObject();
        Token peek();
        Token takeToken();
        void advance();
        Token expect(Id id);
        Token expect(Id id, Token[]? skipped);
        conditional Token match(Id id);
        conditional Token match(Id id, Token[]? skipped);

        @Override
        void close(Exception? cause = Null)
            {
            }
        }

    /**
     * An abstract nested parser.
     */
    protected static @Abstract class NestedParser
            extends Parser
            delegates AnyParser(raw)
        {
        construct(Parser raw, function void()? notifyClosed = Null)
            {
            super(raw);
            this.raw          = raw;
            this.notifyClosed = notifyClosed;
            }

        /**
         * The parser that this ArrayParser delegates to.
         */
        public/protected Parser raw;

        @Override
        protected Token? token.get()
            {
            return raw.token;
            }

        /**
         * Set to True after the end of the parser has been closed.
         */
        protected Boolean closed = False;

        /**
         * An optional notification to make when this parser has been closed.
         */
        protected function void()? notifyClosed;

        @Override
        Token[] skip(Token[]? skipped = Null)
            {
            if (!eof)
                {
                raw.skip(skipped);
                checkDelimiter();
                }

            return skipped ?: [];
            }

        @Override
        @Abstract Token[] skipRemaining(Token[]? skipped = Null);

        @Override
        Doc parseDoc()
            {
            checkEof();
            Doc doc = raw.parseDoc();
            checkDelimiter();
            return doc;
            }

        @Override
        (Token first, Token last) skipDoc(Token[]? skipped = Null)
            {
            checkEof();
            (Token first, Token last) = raw.skipDoc(skipped);
            checkDelimiter();
            return first, last;
            }

        @Override
        Array<Doc> parseArray()
            {
            checkEof();
            Array<Doc> array = raw.parseArray();
            checkDelimiter();
            return array;
            }

        @Override
        (Token first, Token last) skipArray(Token[]? skipped = Null)
            {
            checkEof();
            (Token first, Token last) = raw.skipArray(skipped);
            checkDelimiter();
            return first, last;
            }

        @Override
        conditional ArrayParser matchArray()
            {
            return !eof && match(ArrayEnter)
                    ? (True, new ArrayParser(raw, checkDelimiter))
                    : False;
            }

        @Override
        ArrayParser expectArray()
            {
            checkEof();
            expect(ArrayEnter);
            return new ArrayParser(raw, checkDelimiter);
            }

        @Override
        Map<String, Doc> parseObject()
            {
            checkEof();
            Map<String, Doc> map = raw.parseObject();
            checkDelimiter();
            return map;
            }

        @Override
        (Token first, Token last) skipObject(Token[]? skipped = Null)
            {
            checkEof();
            (Token first, Token last) = raw.skipObject(skipped);
            checkDelimiter();
            return first, last;
            }

        @Override
        conditional ObjectParser matchObject()
            {
            return !eof && match(ObjectEnter)
                    ? (True, new ObjectParser(raw, checkDelimiter))
                    : False;
            }

        @Override
        ObjectParser expectObject()
            {
            checkEof();
            expect(ObjectEnter);
            return new ObjectParser(raw, checkDelimiter);
            }

        /**
         * After each value is read, this method is invoked to handle any counting, delimiters,
         * mode changes, etc.
         */
        protected @Abstract void checkDelimiter();

        @Override
        void close(Exception? cause = Null)
            {
            if (!closed)
                {
                if (cause == Null)
                    {
                    skipRemaining();
                    skipEOF();
                    closed = True;
                    notifyClosed?();
                    }
                else
                    {
                    // don't try to recover; just leave the mess as-is (but mark this parser as
                    // closed)
                    closed = True;
                    }
                }
            }

        protected Boolean checkEof()
            {
            assert !closed as "NestedParser has already been closed";

            if (eof)
                {
                throw new EndOfFile();
                }

            return True;
            }

        /**
         * This method will move "past the EOF". The parser must be "at EOF" when this method is
         * called.
         */
        protected @Abstract void skipEOF();
        }

    /**
     * A nested parser for walking through an array one element at a time.
     */
    static class ArrayParser(Parser raw, function void()? notifyClosed = Null)
            extends NestedParser(raw, notifyClosed)
        {
        /**
         * The index of the next element to read, which is also the count of elements already read.
         */
        public/protected Int index = 0;

        @Override
        @RO Boolean eof.get()
            {
            assert !closed;
            return raw.eof || raw.peek().id == ArrayExit;
            }

        @Override
        Token[] skipRemaining(Token[]? skipped = Null)
            {
            while (!eof)
                {
                skipDoc(skipped);
                }

            return skipped ?: [];
            }

        protected static const ArrayMark(immutable Object mark, Int index);

        @Override
        immutable Object mark()
            {
            return new ArrayMark(raw.mark(), index);
            }

        @Override
        void restore(immutable Object mark, Boolean unmark = False)
            {
            assert !closed;
            assert mark.is(ArrayMark);
            raw.restore(mark.mark);
            index = mark.index;

            if (unmark)
                {
                this.unmark(mark);
                }
            }

        @Override
        void unmark(Object mark)
            {
            assert mark.is(ArrayMark);
            raw.unmark(mark.mark);
            }

        /**
         * After each value is read, there will be a comma separating it from the next value, or
         * (after the last value) there will be an ArrayExit. To set up the parser position on the
         * next value, we must skip over the comma after each parsed value.
         */
        @Override
        protected void checkDelimiter()
            {
            ++index;
            if (raw.token?.id != ArrayExit)
                {
                raw.expect(Comma);
                }
            }

        @Override
        protected void skipEOF()
            {
            expect(ArrayExit);
            }
        }

    /**
     * A nested parser for walking through an object one key and value at a time.
     */
    static class ObjectParser(Parser raw, function void()? notifyClosed = Null)
            extends NestedParser(raw, notifyClosed)
        {
        protected Boolean isKeyNext = True;

        protected Boolean isValueNext.get()
            {
            return !isKeyNext;
            }

        @Override
        @RO Boolean eof.get()
            {
            assert !closed;
            return raw.eof || raw.peek().id == ObjectExit;
            }

        @Override
        Token[] skipRemaining(Token[]? skipped = Null)
            {
            if (isValueNext)
                {
                skipDoc(skipped);
                }

            while (!eof)
                {
                skipDoc(skipped);   // key
                skipDoc(skipped);   // value
                }

            return skipped ?: [];
            }

        protected static const ObjectMark(immutable Object mark, Boolean isKeyNext);

        @Override
        immutable Object mark()
            {
            return new ObjectMark(raw.mark(), isKeyNext);
            }

        @Override
        void restore(immutable Object mark, Boolean unmark = False)
            {
            assert !closed;
            assert mark.is(ObjectMark);
            raw.restore(mark.mark);
            isKeyNext = mark.isKeyNext;

            if (unmark)
                {
                this.unmark(mark);
                }
            }

        @Override
        void unmark(Object mark)
            {
            assert mark.is(ObjectMark);
            raw.unmark(mark.mark);
            }

        /**
         * This method will test the key of the next key/value pair to see if it matches the
         * specified key, or if no key is specified, then it assumes that any key matches.
         *
         * @param key  the key to match
         *
         * @return True iff there was a key/value pair and the key matches the passed key (or no key
         *         was passed)
         * @return the key token, if it was found
         */
        conditional Token matchKey(String? key = Null)
            {
            if (!eof)
                {
                assert isKeyNext as $|JSON parsing error: attempt to match a key while Key \"{key}\"\
                                     | expected, but \"{token?.value : "Null"}\" encountered.
                                    ;

                Token keyToken = peek();
                if (keyToken.id == StrVal && key? == keyToken.value.as(String) : True)
                    {
                    skipDoc();
                    return True, keyToken;
                    }
                }

            return False;
            }

        /**
         * Following a call to [matchObject] or [expectObject], this method will test the key of the
         * next key/value pair to see if it matches the specified key, or if no key is specified, then
         * it assumes that any key matches.
         *
         * Note: Also consumes the colon that separates the key from the value.
         *
         * @param key  the expected key; Null indicates that any key is acceptable
         *
         * @return the key token
         */
        Token expectKey(String? key = Null)
            {
            if (Token keyToken := matchKey(key))
                {
                return keyToken;
                }

            assert val token ?= this.token;
            throw new IllegalJSON(switch (key == Null, token.id)
                {
                case (False, StrVal): $"JSON parsing error: Key \"{key}\" expected, but \"{token.value}\" encountered.";
                case (False, _     ): $"JSON format error: Key \"{key}\" expected, but {token.id} encountered.";
                case (True , _     ): $"JSON format error: Key expected, but {token.id} encountered.";
                });
            }

        /**
         * This method will parse from the current point to the end of the object, looking for the
         * specified key (of a key/value pair).
         *
         * @param key      the key to find
         * @param skipped  the optional array to accrue skipped tokens into
         *
         * @return True iff the key was found inside the object
         * @return Token the key token, if it was found
         */
        conditional Token findKey(String key, Token[]? skipped = Null)
            {
            if (isValueNext)
                {
                skipDoc(skipped);
                assert isKeyNext;
                }

            while (!eof)
                {
                if (Token keyToken := matchKey(key))
                    {
                    return True, keyToken;
                    }

                skipDoc(skipped);   // key
                skipDoc(skipped);   // value
                }

            return False;
            }

        /**
         * After each key is read, there will be a colon separating it from the value, and after
         * each value is read, there will be a comma separating it from the next key, and after the
         * last key/value pair, there will be an ArrayExit. To set up the parser position to the
         * start of the next key or value, we must skip over the colon or comma.
         */
        @Override
        protected void checkDelimiter()
            {
            isKeyNext = !isKeyNext;

            if (isKeyNext)
                {
                if (raw.token?.id != ObjectExit)
                    {
                    raw.expect(Comma);
                    }
                }
            else
                {
                raw.expect(Colon);
                }
            }

        @Override
        protected void skipEOF()
            {
            expect(ObjectExit);
            }
        }
    }
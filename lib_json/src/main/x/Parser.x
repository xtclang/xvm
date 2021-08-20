import ecstasy.Markable;

import ecstasy.io.EndOfFile;
import ecstasy.io.Reader;

import Lexer.Id;
import Lexer.Token;

/**
 * A parser for a JSON document. Turns the output of a JSON Lexer into a JSON Doc.
 */
class Parser
        implements Iterator<Doc>
        implements Markable
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
     */
    void skip(Token[]? skipped = Null)
        {
        if (!eof)
            {
            skipDoc(skipped);
            }
        }


    // ----- advanced ------------------------------------------------------------------------------

    /**
     * Parse a JSON value, which is called a "document" here. A JSON value can be an individual
     * value, an array of JSON values, or a JSON object, which is a sequence of name/value pairs.
     *
     * @return a JSON value
     */
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
     * Parse an array of JSON values.
     *
     * @return an array of JSON values
     */
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
    (Token first, Token last) skipArray(Token[]? skipped = Null)
        {
        Token first = expect(ArrayEnter, skipped);
        Token last;

        if (last := match(ArrayExit, skipped))
            {
            }
        else
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
     * @return True iff the next token was an opening square bracket of an array
     * @return (conditional) the opening square bracket token
     */
    conditional Token matchArray()
        {
        TODO
        }

    /**
     * Obtain the next token, which must be the opening square bracket of an array.
     *
     * @return the opening square bracket token
     */
    Token expectObject()
        {
        TODO
        }

    /**
     * Following a call to [matchObject] or [expectObject], this method will test the key of the
     * next key/value pair to see if it matches the specified key, or if no key is specified, then
     * it assumes that any key matches.
     *
     * @param key  the key to match
     *
     * @return True iff there was a key/value pair and the key matches the passed key (or no key was
     *         passed)
     * @return the key token, if it was found
     */
    conditional Token matchKey(String? key = Null)
        {
        TODO
        }

    /**
     * Following a call to [matchObject] or [expectObject], this method will test the key of the
     * next key/value pair to see if it matches the specified key, or if no key is specified, then
     * it assumes that any key matches.
     *
     * @param key  the expected key; Null indicates that any key is acceptable
     *
     * @return the key token
     */
    Token expectKey(String? key = Null)
        {
        TODO
        }

    /**
     * Following a call to [matchObject] or [expectObject], this method will parse from the current
     * point to the end of the object, looking for the specified key (of a key/value pair).
     *
     * @param key      the key to find
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return True iff the key was found inside the object
     * @return Token the key token, if it was found
     */
    conditional Token findKey(String key, Token[]? skipped = Null)
        {
        TODO
        }

    /**
     * Following a call to [matchArray] or [expectArray], this method will parse from the current
     * point to the end of the array, and read past the closing square bracket.
     *
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return the closing square bracket token
     */
    Token closeObject(Token[]? skipped = Null)
        {
        TODO
        }

    /**
     * Parse a JSON object, which is a sequence of name/value pairs.
     *
     * @return a Map representing a JSON object
     */
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
    (Token first, Token last) skipObject(Token[]? skipped = Null)
        {
        Token first = expect(ObjectEnter, skipped);
        Token last;

        if (last := match(ObjectExit, skipped))
            {
            }
        else
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
    conditional Token matchObject()
        {
        TODO
        }

    /**
     * Obtain the next token, which must be the opening brace of a document.
     *
     * @return the opening brace token
     */
    Token expectObject()
        {
        TODO
        }

    /**
     * Following a call to [matchObject] or [expectObject], this method will test the key of the
     * next key/value pair to see if it matches the specified key, or if no key is specified, then
     * it assumes that any key matches.
     *
     * @param key  the key to match
     *
     * @return True iff there was a key/value pair and the key matches the passed key (or no key was
     *         passed)
     * @return the key token, if it was found
     */
    conditional Token matchKey(String? key = Null)
        {
        TODO
        }

    /**
     * Following a call to [matchObject] or [expectObject], this method will test the key of the
     * next key/value pair to see if it matches the specified key, or if no key is specified, then
     * it assumes that any key matches.
     *
     * @param key  the expected key; Null indicates that any key is acceptable
     *
     * @return the key token
     */
    Token expectKey(String? key = Null)
        {
        TODO
        }

    /**
     * Following a call to [matchObject] or [expectObject], this method will parse from the current
     * point to the end of the object, looking for the specified key (of a key/value pair).
     *
     * @param key      the key to find
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return True iff the key was found inside the object
     * @return Token the key token, if it was found
     */
    conditional Token findKey(String key, Token[]? skipped = Null)
        {
        TODO
        }

    /**
     * Following a call to [matchObject] or [expectObject], this method will parse from the current
     * point to the end of the object, and read past the closing brace.
     *
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return the closing brace token
     */
    Token closeObject(Token[]? skipped = Null)
        {
        TODO
        }

    /**
     * Take the next [Token]. This automatically loads a new "next token", if one exists.
     *
     * @return the token
     */
    Token peek()
        {
        return token?;
        throw new IllegalJSON("Unexpected EOF");
        }

    /**
     * Take the next [Token]. This automatically loads a new "next token", if one exists.
     *
     * @return the token
     */
    Token takeToken()
        {
        Token? token = this.token;
        advance();
        return token ?: throw new IllegalJSON("Unexpected EOF");
        }

    /**
     * Replace the next [Token] with the token that follows it, or Null if there are no more tokens.
     */
    void advance()
        {
        Token? next = Null;
        next := lexer.next();
        this.token = next;
        }

    /**
     * Obtain the next token, which is expected to have the specified [Token.Id].
     *
     * @param id  the expected [Token.Id] of the next token
     *
     * @return the expected Token
     *
     * @throws IllegalJSON if the expected Token is not the next token
     */
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
     * @param id       the expected [Token.Id] of the next token
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return the expected Token
     *
     * @throws IllegalJSON if the expected Token is not the next token
     */
    Token expect(Id id, Token[]? skipped)
        {
        Token token = expect(id);
        skipped?.add(token);
        return token;
        }

    /**
     * Test if the next [Token] is of the specified [Token.Id], and if it is, take it.
     *
     * @param id  the [Token.Id] to use to determine if the next Token is the desired match
     *
     * @return True iff the next Token has the specified Id
     * @return (conditional) the matching Token
     */
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
     * @param id       the [Token.Id] to use to determine if the next Token is the desired match
     * @param skipped  the optional array to accrue skipped tokens into
     *
     * @return True iff the next Token has the specified Id
     * @return (conditional) the matching Token
     */
    conditional Token match(Id id, Token[]? skipped)
        {
        if (Token token := match(id))
            {
            skipped?.add(token);
            return True, token;
            }

        return False;
        }
    }
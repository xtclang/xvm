import io.EndOfFile;
import io.Reader;
import io.Reader.Position;

import collections.ListMap;
import collections.HashSet;

import Lexer.Id;
import Lexer.Token;

/**
 * A parser for a JSON document. Turns the output of a JSON Lexer into a JSON Doc.
 */
class Parser
        implements Iterator<Doc>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a JSON parser that processes a document from a Reader.
     *
     * @param reader       the source of the JSON document text
     * @param collateDups  pass True to allow duplicate names in JSON objects
     */
    construct(Reader reader, Boolean collateDups = False)
        {
        construct Parser(new Lexer(reader), collateDups);
        }

    /**
     * Construct a JSON parser that processes a document from a Lexer.
     *
     * @param lexer        a JSON Lexer
     * @param collateDups  pass True to allow duplicate names in JSON objects
     */
    construct(Iterator<Token> lexer, Boolean collateDups = False)
        {
        this.lexer       = lexer;
        this.token      := lexer.next();
        this.collateDups = collateDups;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying [Lexer].
     */
    protected/private Iterator<Token> lexer;

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
    protected Boolean collateDups;

    /**
     * Determine if the parser has encountered the end of file (no tokens left).
     */
    protected Boolean eof.get()
        {
        return token == Null;
        }


    // ----- Iterator ------------------------------------------------------------------------------

    @Override
    conditional Doc next()
        {
        return eof
                ? False
                : (True, parseDoc());
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Parse a JSON value, which is called a "document" here. A JSON value can be an individual
     * value, an array of JSON values, or a JSON object, which is a sequence of name/value pairs.
     *
     * @return a JSON value
     */
    protected Doc parseDoc()
        {
        switch (token?.id)
            {
            case NoVal:
            case BoolVal:
            case IntVal:
            case FPVal:
            case StrVal:
                return take().value;

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
     * Parse an array of JSON values.
     *
     * @return an array of JSON values
     */
    protected Array<Doc> parseArray()
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
     * Parse a JSON object, which is a sequence of name/value pairs.
     *
     * @return a Map representing a JSON object
     */
    protected Map<String, Doc> parseObject()
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
     * Take the next [Token]. This automatically loads a new "next token", if one exists.
     *
     * @return the token
     */
    protected Token take()
        {
        Token? token = this.token;
        advance();
        return token?;
        throw new EndOfFile();
        }

    /**
     * Replace the next [Token] with the token that follows it, or Null if there are no more tokens.
     */
    protected void advance()
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
    protected Token expect(Id id)
        {
        if (Token token := match(id))
            {
            return token;
            }

        throw new IllegalJSON($"JSON format error: {id} expected, but {this.token?.id.toString() : "EOF"} encountered.");
        }

    /**
     * Test if the next [Token] is of the specified [Token.Id], and if it is, take it.
     *
     * @param id  the [Token.Id] to use to determine if the next Token is the desired match
     *
     * @return True iff the next Token has the specified Id
     * @return (conditional) the matching Token
     */
    protected conditional Token match(Id id)
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
    }
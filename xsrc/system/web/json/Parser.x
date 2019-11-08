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
    construct(Lexer lexer)
        {
        this.lexer  = lexer;
        this.token := lexer.next();
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying Lexer.
     */
    protected/private Lexer lexer;

    /**
     * The next Token to process.
     */
    protected/private Token? token = Null;

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

                map.process(name, entry ->
                    {
                    if (entry.exists)
                        {
                        // there is a duplicate name, which is not explicitly forbidden by the JSON
                        // spec, so store all of the values that share this name in an array
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
            while (match(Comma));
            expect(ObjectExit);
            }
        return map;
        }

    protected Token take()
        {
        Token? token = this.token;
        advance();
        return token?;
        throw new EndOfFile();
        }

    protected void advance()
        {
        Token? next = Null;
        next := lexer.next();
        this.token = next;
        }

    protected Token expect(Id id)
        {
        if (Token token := match(id))
            {
            return token;
            }

        throw new IllegalJSON($"JSON format error: {id} expected, but {this.token?.id.toString() : "EOF"} encountered.");
        }

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
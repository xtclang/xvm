import io.EndOfFile;
import io.Reader;
import io.Reader.Position;

import collections.ListMap;

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
     * The underlying Reader.
     */
    protected/private Lexer lexer;

    /**
     * The underlying Reader.
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
                return parseArray().as(Doc); // TODO GG should not require ".as(Doc)"

            case ObjectEnter:
                return parseObject().as(Doc); // TODO GG should not require ".as(Doc)"
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
            do
                {
                String name = expect(StrVal).value.as(String);
                Token  sep  = expect(Colon);
                Doc    doc  = parseDoc();
                // TODO check for duplicate name (error?) - check JSON spec
                map.put(name, doc);
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
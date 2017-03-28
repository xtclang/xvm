package org.xvm.compiler;


import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.*;

import org.xvm.util.Severity;

import java.util.ArrayList;
import java.util.List;


/**
 * A recursive descent parser for Ecstasy source code.
 *
 * @author cp 2016.11.03
 */
public class Parser
    {
    // ----- constructors ------------------------------------------------------

    /**
     * Construct an XTC lexical analyzer.
     *
     * @param source   the source to parse
     * @param listener the error listener
     */
    public Parser(Source source, ErrorListener listener)
        {
        if (source == null)
            {
            throw new IllegalArgumentException("Source required");
            }
        if (listener == null)
            {
            throw new IllegalArgumentException("ErrorListener required");
            }

        m_source        = source;
        m_errorListener = listener;
        m_lexer         = new Lexer(source, listener);
        }


    // ----- parsing -----------------------------------------------------------

    /**
     * Parse the compilation unit.
     *
     * @return the top level type declaration
     *
     * @throws CompilerException if a parsing error occurs while parsing the
     *         source code that forces the parser to abandon its progress before
     *         completion
     */
    public Statement parseCompilationUnit()
        {
        // parsing can only occur once
        if (!m_fDone)
            {
            // set the completion flag at this point (in case an exception occurs
            // during parsing
            m_fDone = true;

            // prime the token stream
            next();

            List<Statement> list = parseImportsAndTypedefs();

            Statement toptype = parseTypeDeclarationStatement();
            if (list == null)
                {
                m_root = toptype;
                }
            else
                {
                list.add(toptype);
                m_root = new BlockStatement(list);
                }
            }

        return m_root;
        }

    /**
     * At the file level, it is possible to encounter any combination of import
     * and typedef statements.
     */
    List<Statement> parseImportsAndTypedefs()
        {
        List<Statement> list = null;
        try
            {
            while (true)
                {
                if (peek().getId() == Id.IMPORT)
                    {
                    if (list == null)
                        {
                        list = new ArrayList<>();
                        }
                    list.add(parseImport());
                    }
                else if (peek().getId() == Id.TYPEDEF)
                    {
                    if (list == null)
                        {
                        list = new ArrayList<>();
                        }
                    list.add(parseTypedef());
                    }
                else
                    {
                    return list;
                    }
                }
            }
        catch (CompilerException e)
            {
            if (!eof())
                {
                skipToNextStatement();
                }
            return list;
            }
        }

    /**
     * Parse an import statement.
     *
     * @return an ImportStatement
     */
    ImportStatement parseImport()
        {
        List<Token> qualifiedName = new ArrayList<>();
        Token       simpleName    = null;

        expect(Id.IMPORT);

        // parse qualified name
        boolean first = true;
        while (first || (match(Id.DOT) != null))
            {
            simpleName = expect(Id.IDENTIFIER);
            qualifiedName.add(simpleName);
            first = false;
            }

        // optional alias override
        if (match(Id.AS) != null)
            {
            // parse simple name
            simpleName = expect(Id.IDENTIFIER);
            }

        expect(Id.SEMICOLON);

        return new ImportStatement(simpleName, qualifiedName);
        }

    /**
     * Parse a typedef statement.
     *
     * @return a TypedefStatement
     */
    TypedefStatement parseTypedef()
        {
        expect(Id.TYPEDEF);

        // TODO handle the case in which a "function" puts the name BEFORE the param list

        TypeExpression type = parseTypeExpression();

        Token simpleName = expect(Id.IDENTIFIER);

        expect(Id.SEMICOLON);

        return new TypedefStatement(simpleName, type);
        }

    /**
     * Parse a type declaration
     *
     * @return a TypeDeclaration
     */
    Statement parseTypeDeclarationStatement()
        {
        // category of the type
        List<Token> modifiers = null;
        Token       category  = null;
        ParsingPreamble: while (true)
            {
            switch (peek().getId())
                {
                // optional modifiers
                case STATIC:
                case PUBLIC:
                case PROTECTED:
                case PRIVATE:
                    if (modifiers == null)
                        {
                        modifiers = new ArrayList<>();
                        }
                    modifiers.add(current());
                    break;

                case PACKAGE:
                case CLASS:
                case INTERFACE:
                case SERVICE:
                case CONST:
                case ENUM:
                case TRAIT:
                case MIXIN:
                    category = current();
                    break ParsingPreamble;

                default:
                    category = expect(Id.MODULE);
                    break ParsingPreamble;
                }
            }

        // TODO consider using parseName instead

        // type name
        Token name = expect(Id.IDENTIFIER);

        // module name is dot-delimited
        List<Token> qualifier = null;
        if (category.getId() == Id.MODULE)
            {
            qualifier = new ArrayList<>();
            while (match(Id.DOT) != null)
                {
                qualifier.add(expect(Id.IDENTIFIER));
                }
            }

        // TODO package import module version-info (allow / avoid / prefer)

        // optional type parameters
        List<Parameter> typeParams = parseTypeParams();

        List<Parameter> constructorParams = null;
        if (match(Id.L_PAREN) != null)
            {
            constructorParams = new ArrayList<>();
            boolean first = true;
            while (match(Id.R_PAREN) == null)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    expect(Id.COMMA);
                    }

                TypeExpression type  = parseTypeExpression();
                Token          param = expect(Id.IDENTIFIER);
                Expression     value = null;
                if (match(Id.ASN) != null)
                    {
                    value = parseExpression();
                    }
                constructorParams.add(new Parameter(type, param, value));
                }
            }

        List<Composition> composition = new ArrayList<>();
        ParsingComposition: while (true)
            {
            if (match(Id.EXTENDS) != null)
                {
                TypeExpression type = parseTypeExpression();
                Expression constructor = null;
                if (match(Id.L_PAREN) != null)
                    {
                    // TODO constructor
                    skipEnclosed(Id.L_PAREN);
                    }
                composition.add(new Composition.Extends(type, constructor));
                }
            else if (match(Id.IMPLEMENTS) != null)
                {
                do
                    {
                    composition.add(new Composition.Implements(parseTypeExpression()));
                    }
                while (match(Id.COMMA) != null);
                }
            else if (match(Id.DELEGATES) != null)
                {
                do
                    {
                    TypeExpression type = parseTypeExpression();
                    expect(Id.L_PAREN);
                    Expression delegate = parseExpression();
                    expect(Id.R_PAREN);
                    composition.add(new Composition.Delegates(type, delegate));
                    }
                while (match(Id.COMMA) != null);
                }
            else if (match(Id.INCORPORATES) != null)
                {
                do
                    {
                    TypeExpression type = parseTypeExpression();
                    Expression constructor = null;
                    if (match(Id.L_PAREN) != null)
                        {
                        // TODO constructor
                        skipEnclosed(Id.L_PAREN);
                        }
                    composition.add(new Composition.Incorporates(type, constructor));
                    }
                while (match(Id.COMMA) != null);
                }
            else if (match(Id.INTO) != null)
                {
                composition.add(new Composition.Into(parseTypeExpression()));
                }
            else
                {
                break;
                }
            }

        BlockStatement body = null;
        if (peek().getId() == Id.L_CURLY)
            {
            body = parseBlockStatement();
            }
        else
            {
            expect(Id.SEMICOLON);
            }

        return new TypeDeclarationStatement(modifiers, category, name,
                qualifier, typeParams, constructorParams, composition, body, takeDoc());
        }

    /**
     * Parse a block statement.
     *
     * @return a BlockStatement
     */
    BlockStatement parseBlockStatement()
        {
        expect(Id.L_CURLY);
        // TODO
        skipEnclosed(Id.L_CURLY);
        return new BlockStatement(null);
        }

    /**
     * Parse any expression.
     *
     * @return an expression
     */
    Expression parseExpression()
        {
        // TODO for now, just parse a name or a literal
        switch (peek().getId())
            {
            case IDENTIFIER:
                // TODO this is just a place-holder, because lots of expressions start with a name
                return new NameExpression(parseName());

            case LIT_CHAR:
            case LIT_STRING:
            case LIT_INT:
            case LIT_DEC:
            case LIT_BIN:
                return new LiteralExpression(current());

            case TODO:
                return parseTodoExpression();

            default:
                // TODO
                throw new UnsupportedOperationException("parse expr");
            }
        }

    /**
     * Parse a type expression.
     *
     * @return a TypeExpression
     */
    TypeExpression parseTypeExpression()
        {
        if (match(Id.FUNCTION) != null)
            {
            // TODO
            throw new UnsupportedOperationException("parse function");
            }
        else
            {
            // TODO for now, just parse "name.name.name<param extends type, param extends type>"
            List<Token> names = parseName();
            List<Parameter> params = parseTypeParams();
            return new TypeExpression(names, params);
            }
        }

    TodoExpression parseTodoExpression()
        {
        expect(Id.TODO);
        Expression message = null;
        if (match(Id.L_PAREN) != null)
            {
            message = parseExpression();
            expect(Id.R_PAREN);
            }
        return new TodoExpression(message);
        }

    /**
     * Parse a dot-delimited list of names.
     *
     * @return a list of zero or more identifier tokens
     */
    List<Token> parseName()
        {
        List<Token> names = null;
        do
            {
            names.add(expect(Id.IDENTIFIER));
            }
        while (match(Id.DOT) != null);
        return names;
        }

    /**
     * If the next token is a &quot;&lt;&quot;, then parse a list of type params.
     *
     * @return a list of zero or more type params, or null if there were no angle brackets
     */
    List<Parameter> parseTypeParams()
        {
        List<Parameter> typeParams = null;
        if (match(Id.COMP_LT) != null)
            {
            typeParams = new ArrayList<>();
            boolean first = true;
            while (match(Id.COMP_GT) == null)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    expect(Id.COMMA);
                    }

                Token          param = expect(Id.IDENTIFIER);
                TypeExpression type  = null;
                if (match(Id.EXTENDS) != null)
                    {
                    type = parseTypeExpression();
                    }
                typeParams.add(new Parameter(type, param));
                }
            }
        return typeParams;
        }

    /**
     * Attempt to get out of whatever parsing mess we got ourselves into by
     * figuring out where the current statement ends, and starting anew from
     * there.
     */
    void skipToNextStatement()
        {
        while (true)
            {
            switch (peek().getId())
                {
                case SEMICOLON:
                    next();
                    return;

                case L_CURLY:
                case L_PAREN:
                case L_SQUARE:
                    skipEnclosed(current().getId());
                    break;

                case R_CURLY:
                    return;

                case ASSERT:
                case BREAK:
                case CLASS:
                case CONST:
                case CONSTRUCT:
                case CONTINUE:
                case DO:
                case ENUM:
                case IF:
                case IMPORT:
                case INTERFACE:
                case MIXIN:
                case MODULE:
                case PACKAGE:
                case RETURN:
                case SERVICE:
                case SWITCH:
                case THROW:
                case TODO:
                case TRAIT:
                case TRY:
                case TYPEDEF:
                case USING:
                case WHILE:
                    return;

                default:
                    next();
                    break;
                }
            }
        }

    /**
     * Skip some enclosed amount of code between some likely-balanced set of
     * parenthesis / curlies / brackets.
     *
     * @param idOpen  the opening parenthesis / curlies / brackets
     */
    void skipEnclosed(Id idOpen)
        {
        while (true)
            {
            switch (peek().getId())
                {
                case L_CURLY:
                case L_PAREN:
                case L_SQUARE:
                    skipEnclosed(current().getId());
                    break;

                case R_CURLY:
                    if (idOpen == Id.L_CURLY)
                        {
                        next();
                        }
                    // whether or not we were looking for a right curly, it is
                    // likely to signify an end of something significant
                    return;

                case R_PAREN:
                    next();
                    if (idOpen == Id.L_PAREN)
                        {
                        return;
                        }
                    break;

                case R_SQUARE:
                    next();
                    if (idOpen == Id.L_SQUARE)
                        {
                        return;
                        }
                    break;

                default:
                    next();
                    break;
                }
            }
        }

    // ----- token stream handling ---------------------------------------------

    protected Token peek()
        {
        return m_token;
        }

    /**
     * Obtain the current token.
     *
     * @return the current token
     */
    protected Token current()
        {
        final Token token = m_token;
        next();
        return token;
        }

    /**
     * Advance to and obtain the next token.
     *
     * @return the next token (which is now the "current" token)
     */
    protected Token next()
        {
        // weed out the comments (but store off the most recently encountered doc comment in case
        // anyone needs it later)
        while (m_lexer.hasNext())
            {
            m_token = m_lexer.next();
            switch (m_token.getId())
                {
                case ENC_COMMENT:
                    String sComment = (String) m_token.getValue();
                    if (sComment.length() > 0 && sComment.startsWith("*"))
                        {
                        m_doc = m_token;
                        }
                    break;

                case EOL_COMMENT:
                    break;

                default:
                    return m_token;
                }
            }

        if (m_token != null && m_token.getStartPosition() != m_token.getEndPosition())
            {
            // pretend there's one more closing curly brace
            m_token = new Token(m_token.getEndPosition(), m_token.getEndPosition(), Id.R_CURLY);
            return m_token;
            }

        m_errorListener.log(Severity.ERROR, UNEXPECTED_EOF, null,
                m_source, m_source.getPosition(), m_source.getPosition());
        throw new CompilerException("unexpected EOF");
        }

    /**
     * If the current token in the token stream matches the specified identity,
     * then return the current token and advance to the next; if the current
     * token does not match, then do not advance and return null.
     *
     * @param id  the identity of the token being looked for
     *
     * @return a token with the specified id, or null if the id did not match
     */
    protected Token match(Token.Id id)
        {
        if (m_token.getId() == id)
            {
            return current();
            }

        if (m_token.getId() == Id.IDENTIFIER && id.ContextSensitive && m_token.getValue().equals(id.TEXT))
            {
            // advance to the next token
            next();

            // return the previously "current" token
            return m_token.getContextSensitiveKeyword();
            }

        return null;
        }

    /**
     * If the current token in the token stream matches the specified identity,
     * then return the current token and advance to the next; otherwise log a
     * "token expected" error.
     *
     * @param id  the identity of the token being looked for
     *
     * @return a token with the specified id
     */
    protected Token expect(Token.Id id)
        {
        final Token token = m_token;
        if (token.getId() == id)
            {
            return current();
            }

        m_errorListener.log(Severity.ERROR, EXPECTED_TOKEN, null,
                m_source, m_token.getStartPosition(), m_token.getEndPosition());
        throw new CompilerException("expected token: " + id + " (found: " + token + ")");
        }

    /**
     * @return true if the token stream is exhausted
     */
    protected boolean eof()
        {
        return !m_lexer.hasNext();
        }

    /**
     * Take whatever the most recent documentation comment is.
     * @return
     */
    protected Token takeDoc()
        {
        Token doc = m_doc;
        m_doc = null;
        return doc;
        }


    // ----- constants ---------------------------------------------------------

    /**
     * Unexpected End-Of-File (token exhaustion).
     */
    public static final String UNEXPECTED_EOF = "PARSER-01";
    /**
     * Expected a particular token.
     */
    public static final String EXPECTED_TOKEN = "PARSER-02";


    // ----- data members ------------------------------------------------------

    /**
     * The Source to parse.
     */
    private final Source m_source;

    /**
     * The ErrorListener to report errors to.
     */
    private final ErrorListener m_errorListener;

    /**
     * The lexical analyzer.
     */
    private final Lexer m_lexer;

    /**
     * The current token.
     */
    private Token m_token;

    /**
     * The most recent doc comment.
     */
    private Token m_doc;

    /**
     * The top-most (outer-most) type declaration of the compilation unit, such
     * as a module.
     */
    private Statement m_root;

    /**
     * True once parsing has occurred.
     */
    private boolean m_fDone;
    }
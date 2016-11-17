package org.xvm.compiler;


import org.xvm.compiler.Token.Id;


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

        m_source   = source;
        m_listener = listener;
        m_lexer    = new Lexer(source, listener);
        }


    // ----- parsing -----------------------------------------------------------

    interface TypeDeclaration {}

    /**
     * Parse the compilation unit.
     *
     * @return the top level type declaration
     *
     * @throws CompilerException if a parsing error occurs while parsing the
     *         source code that forces the parser to abandon its progress before
     *         completion
     */
    public TypeDeclaration parseCompilationUnit()
        {
        // parsing can only occur once
        if (m_fDone)
            {
            return m_typeTop;
            }

        // set the completion flag at this point (in case an exception occurs
        // during parsing
        m_fDone = true;

        // TODO
        return null;
        }


    // ----- token stream handling ---------------------------------------------

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
        if (!m_lexer.hasNext())
            {
            // TODO log an EOF
            throw new CompilerException("unexpected EOF");
            }

        return m_token = m_lexer.next();
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
    protected Token peek(Token.Id id)
        {
        return m_token.getId() == id ? current() : null;
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
            return token;
            }

        // TODO log an error
        throw new CompilerException("expected Token with Id=" + id);
        }


    // ----- data members ------------------------------------------------------

    /**
     * The Source to parse.
     */
    private final Source m_source;

    /**
     * The ErrorListener to report errors to.
     */
    private final ErrorListener m_listener;

    /**
     * The lexical analyzer.
     */
    private final Lexer m_lexer;

    /**
     * The current token.
     */
    private Token m_token;

    // TODO private Scope m_scope;

    /**
     * The top-most (outer-most) type declaration of the compilation unit, such
     * as a module.
     */
    private TypeDeclaration m_typeTop;

    /**
     * True once parsing has occurred.
     */
    private boolean m_fDone;
    }
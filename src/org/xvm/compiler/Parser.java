package org.xvm.compiler;


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

    public void parse()
        {
        // TODO not void but returns a top level element
        }

    protected void parseCompilationUnit()
        {
        // TODO
        // parseImports();
        }


    // ----- data members ------------------------------------------------------

    /**
     * The Source to parse.
     */
    private Source m_source;

    /**
     * The ErrorListener to report errors to.
     */
    private ErrorListener m_listener;

    /**
     * The lexical analyzer.
     */
    private Lexer m_lexer;
    }
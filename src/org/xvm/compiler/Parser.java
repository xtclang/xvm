package org.xvm.compiler;


import java.util.Iterator;

import java.util.function.Consumer;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.isAsciiLetter;
import static org.xvm.util.Handy.isDigit;
import static org.xvm.util.Handy.parseDelimitedString;
import static org.xvm.util.Handy.quotedChar;


/**
 * An XTC source code parser supporting both demand-based and stream-based
 * parsing.
 *
 * @author cp 2015.11.09
 */
public class Parser
        implements Iterator<Token>
    {
    // ----- constructors ------------------------------------------------------

    /**
     * Construct an XTC Parser.
     *
     * @param source  the source to parse
     */
    public Parser(Source source, ErrorListener errorListener)
        {
        if (source == null)
            {
            throw new IllegalArgumentException("Source required");
            }
        if (errorListener == null)
            {
            throw new IllegalArgumentException("ErrorListener required");
            }

        m_source        = source;
        m_errorListener = errorListener;

        eatWhitespace();
        }


    // ----- Iterator methods --------------------------------------------------

    @Override
    public boolean hasNext()
        {
        return m_source.hasNext();
        }

    @Override
    public Token next()
        {
        final Token token = eatToken();
        eatWhitespace();
        return token;
        }


    // ----- public API --------------------------------------------------------

    /**
     * Parse the source, emitting a stream of tokens to the specified consumer.
     *
     * @param consumer  the Token Consumer
     */
    public void parse(Consumer<Token> consumer)
        {
        final Source source = m_source;
        while (source.hasNext())
            {
            consumer.accept(eatToken());
            eatWhitespace();
            }
        }


    // ----- inner class: ParserException --------------------------------------

    /**
     * An exception class used to report exceptions in the parsing process.
     */
    public static class ParserException
            extends RuntimeException
        {
        public ParserException(String message)
            {
            super(message);
            }

        public ParserException(String message, Throwable cause)
            {
            super(message, cause);
            }

        public ParserException(Throwable cause)
            {
            super(cause);
            }
        }


    // ----- internal ----------------------------------------------------------

    /**
     * Eat the characters defined as whitespace, which include line terminators
     * and the file terminator. Whitespace does not include comments.
     */
    protected void eatWhitespace()
        {
        final Source source = m_source;
        while (source.hasNext())
            {
            if (!isWhitespace(nextChar()))
                {
                // put back the non-whitespace character
                source.rewind();
                break;
                }
            }
        }

    /**
     * Parse a Eat a single line aka end-of-line comment.
     *
     * @return the comment as a Token
     */
    protected Token eatToken()
        {
        final Source source   = m_source;
        final long   lInitPos = source.getPosition();
        final char   chInit   = source.next();
        switch (chInit)
            {
            case '{':
                return new Token(lInitPos, source.getPosition(), Token.ID_L_CURLY);
            case '}':
                return new Token(lInitPos, source.getPosition(), Token.ID_R_CURLY);
            case '(':
                return new Token(lInitPos, source.getPosition(), Token.ID_L_PAREN);
            case ')':
                return new Token(lInitPos, source.getPosition(), Token.ID_R_PAREN);
            case '[':
                return new Token(lInitPos, source.getPosition(), Token.ID_L_SQUARE);
            case ']':
                return new Token(lInitPos, source.getPosition(), Token.ID_R_SQUARE);

            case ':':
                return new Token(lInitPos, source.getPosition(), Token.ID_COLON);
            case ';':
                return new Token(lInitPos, source.getPosition(), Token.ID_SEMICOLON);
            case ',':
                return new Token(lInitPos, source.getPosition(), Token.ID_COMMA);
            case '.':
                if (source.hasNext())
                    {
                    if (source.next() == '.')
                        {
                        return new Token(lInitPos, source.getPosition(), Token.ID_DOTDOT);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Token.ID_DOT);
            case '@':
                return new Token(lInitPos, source.getPosition(), Token.ID_AT);
            case '?':
                return new Token(lInitPos, source.getPosition(), Token.ID_COND);

            case '/':
                if (source.hasNext())
                    {
                    switch (source.next())
                        {
                        case '/':
                            return eatSingleLineComment(lInitPos);

                        case '*':
                            return eatEnclosedComment(lInitPos);

                        case '=':
                            return new Token(lInitPos, source.getPosition(), Token.ID_DIV_MOV);
                        }
                    source.rewind();
                    }

                return new Token(lInitPos, source.getPosition(), Token.ID_DIV);

            default:
                if (!isIdentifierStart(chInit))
                    {
                    m_errorListener.log(Severity.ERROR, ILLEGAL_CHAR,
                            new Object[] {quotedChar(chInit)},
                            Source.calculateLine(lInitPos), Source.calculateOffset(lInitPos),
                            m_source.getLine(), m_source.getOffset());
                    }
                // fall through
            case 'A':case 'B':case 'C':case 'D':case 'E':case 'F':case 'G':
            case 'H':case 'I':case 'J':case 'K':case 'L':case 'M':case 'N':
            case 'O':case 'P':case 'Q':case 'R':case 'S':case 'T':case 'U':
            case 'V':case 'W':case 'X':case 'Y':case 'Z':
            case 'a':case 'b':case 'c':case 'd':case 'e':case 'f':case 'g':
            case 'h':case 'i':case 'j':case 'k':case 'l':case 'm':case 'n':
            case 'o':case 'p':case 'q':case 'r':case 's':case 't':case 'u':
            case 'v':case 'w':case 'x':case 'y':case 'z':
            case '_': // TODO '$' ?
                {
                while (source.hasNext())
                    {
                    if (!isIdentifierPart(source.next()))
                        {
                        source.rewind();
                        break;
                        }
                    }

                String s = source.toString(lInitPos, source.getPosition());
                return Token.isKeyword(s)
                        ? new Token(lInitPos, source.getPosition(), Token.getKeywordId(s))
                        : new Token(lInitPos, source.getPosition(), Token.ID_IDENTIFIER, s);
                }
            }
        }

    /**
     * Eat a single line aka end-of-line comment.
     *
     * @return the comment as a Token
     */
    protected Token eatSingleLineComment(long lPosTokenStart)
        {
        final Source source        = m_source;
        final long   lPosTextStart = source.getPosition();
        while (source.hasNext())
            {
            if (isLineTerminator(nextChar()))
                {
                source.rewind();
                break;
                }
            }
        final long lPosEnd = source.getPosition();
        return new Token(lPosTokenStart, lPosEnd, Token.ID_EOL_COMMENT,
                source.toString(lPosTextStart, lPosEnd));
        }

    /**
     * Eat a multi-line aka enclosed comment.
     *
     * @return the comment as a Token
     */
    protected Token eatEnclosedComment(long lPosTokenStart)
        {
        final Source source        = m_source;
        final long   lPosTextStart = source.getPosition();

        boolean fAsterisk = false;
        while (source.hasNext())
            {
            final char chNext = nextChar();
            if (chNext == '*')
                {
                fAsterisk = true;
                }
            else if (fAsterisk && chNext == '/')
                {
                final long lPosTokenEnd = source.getPosition();
                source.rewind();
                source.rewind();
                final long lPosTextEnd  = source.getPosition();
                source.setPosition(lPosTokenEnd);
                return new Token(lPosTokenStart, lPosTokenEnd, Token.ID_ENC_COMMENT,
                        source.toString(lPosTextStart, lPosTextEnd));
                }
            else
                {
                fAsterisk = false;
                }
            }

        // missing the enclosing "*/"
        m_errorListener.log(Severity.ERROR, EXPECTED_ENDCOMMENT, null,
                Source.calculateLine(lPosTokenStart), Source.calculateOffset(lPosTokenStart),
                m_source.getLine(), m_source.getOffset());

        // just pretend that the rest of the file was all one big comment
        return new Token(lPosTokenStart, source.getPosition(), Token.ID_ENC_COMMENT,
                source.toString(lPosTextStart, source.getPosition()));
        }

    /**
     * Get the next character of source code, but do some additional checks
     * on the character to make sure it's legal, such as checking for an illegal
     * SUB character.
     */
    protected char nextChar()
        {
        final char ch = m_source.next();

        // it is illegal for the SUB aka EOF character to occur
        // anywhere in the source other than at the end
        if (ch == EOF && m_source.hasNext())
            {
            // back up to get the location of the SUB character
            final long lPos = m_source.getPosition();
            m_source.rewind();
            final int iLine   = m_source.getLine();
            final int iOffset = m_source.getOffset();
            m_source.setPosition(lPos);

            m_errorListener.log(Severity.ERROR, UNEXPECTED_EOF, null,
                    iLine, iOffset, m_source.getLine(), m_source.getOffset());
            }

        return ch;
        }


    // ----- helper methods ----------------------------------------------------

    /**
     * Determine if the specified character is white-space.
     *
     * @param ch  the character to evaluate
     *
     * @return true iff the character is defined as an XTC <i>SpacingElement</i>
     */
    public static boolean isWhitespace(char ch)
        {
        // optimize for the ASCII range
        if (ch < 128)
            {
            // this handles the following cases:
            //   U+0009   9  HT   Horizontal Tab
            //   U+000A  10  LF   Line Feed
            //   U+000B  11  VT   Vertical Tab
            //   U+000C  12  FF   Form Feed
            //   U+000D  13  CR   Carriage Return
            //   U+001A  26  SUB  End-of-File, or “control-Z”
            //   U+001C  28  FS   File Separator
            //   U+001D  29  GS   Group Separator
            //   U+001E  30  RS   Record Separator
            //   U+001F  31  US   Unit Separator
            //   U+0020  32  SP   Space
            //
            //                                0               1               2               3
            //                                0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
            return ch < 64 && ((1L << ch) & 0b0000000001111100000000000010111110000000000000000000000000000000L) != 0L;
            // or eliminate the conditional to achieve a slightly more
            // efficient expression: 
            // return (((~(((long) ch) >>> 6) & 1L) << ch) & 0x7C002F80000000L) != 0L;
            }

        // this handles the following cases:
        //   U+0085    133  NEL     Next Line
        //   U+00A0    160  &nbsp;  Non-breaking space
        //   U+1680   5760          Ogham Space Mark
        //   U+2000   8192          En Quad
        //   U+2001   8193          Em Quad
        //   U+2002   8194          En Space
        //   U+2003   8195          Em Space
        //   U+2004   8196          Three-Per-Em Space
        //   U+2005   8197          Four-Per-Em Space
        //   U+2006   8198          Six-Per-Em Space
        //   U+2007   8199          Figure Space
        //   U+2008   8200          Punctuation Space
        //   U+2009   8201          Thin Space
        //   U+200A   8202          Hair Space
        //   U+2028   8232   LS     Line Separator
        //   U+2029   8233   PS     Paragraph Separator
        //   U+202F   8239          Narrow No-Break Space
        switch (ch)
            {
            case 0x0085:
            case 0x00A0:
            case 0x1680:
            case 0x2000:
            case 0x2001:
            case 0x2002:
            case 0x2003:
            case 0x2004:
            case 0x2005:
            case 0x2006:
            case 0x2007:
            case 0x2008:
            case 0x2009:
            case 0x200A:
            case 0x2028:
            case 0x2029:
            case 0x202F:
            case 0x205F:
            case 0x3000:
                return true;

            default:
                return false;
            }
        }

    /**
     * Determine if the specified character is a line terminator.
     *
     * @param ch  the character to evaluate
     *
     * @return true iff the character is defined as an XTC <i>LineTerminator</i>
     */
    public static boolean isLineTerminator(char ch)
        {
        // optimize for the ASCII range
        if (ch < 128)
            {
            // this handles the following cases:
            //   U+000A  10  LF   Line Feed
            //   U+000B  11  VT   Vertical Tab
            //   U+000C  12  FF   Form Feed
            //   U+000D  13  CR   Carriage Return
            return ch >= 10 && ch <= 13;
            }

        // this handles the following cases:
        //   U+0085    133   NEL    Next Line
        //   U+2028   8232   LS     Line Separator
        //   U+2029   8233   PS     Paragraph Separator
        return ch == 0x0085 | ch == 0x2028 | ch == 0x2029;
        }

    /**
     * Determine if the specified character can be used as the first character
     * of an identifier.
     *
     * @param ch  the character to evaluate
     *
     * @return true iff the specified character can be the start of an
     *         identifier
     */
    public static boolean isIdentifierStart(char ch)
        {
        return Character.isUnicodeIdentifierStart(ch) || ch == '_';
        }

    /**
     * Determine if the specified character can be part of an identifier.
     *
     * @param ch  the character to evaluate
     *
     * @return true iff the specified character can be part of an identifier
     */
    public static boolean isIdentifierPart(char ch)
        {
        return Character.isUnicodeIdentifierPart(ch) || ch == '_';
        }

    /**
     * Validate the specified identifier.
     *
     * @param sName  the identifier
     *
     * @return true iff the identifier is a lexically valid identifier
     */
    public static boolean isValidIdentifier(String sName)
        {
        if (sName == null)
            {
            return false;
            }

        final int cch = sName.length();
        if (cch == 0)
            {
            return false;
            }

        if (!isIdentifierStart(sName.charAt(0)))
            {
            return false;
            }

        for (int i = 1; i < cch; ++i)
            {
            if (!isIdentifierPart(sName.charAt(i)))
                {
                return false;
                }
            }

        // TODO should this check if it is a reserved word?

        return true;
        }

    /**
     * Validate the specified RFC1035 label. TODO internationalization support
     *
     * @param sName  the RFC1035 label
     *
     * @return true iff the name is a lexically valid RFC1035 label
     */
    public static boolean isValidRFC1035Label(String sName)
        {
        if (sName == null)
            {
            return false;
            }

        final int cch = sName.length();
        if (cch == 0 || cch > 63)
            {
            return false;
            }

        if (!isAsciiLetter(sName.charAt(0)))
            {
            return false;
            }

        for (int i = 1; i < cch; ++i)
            {
            final char ch = sName.charAt(i);
            if (!(isAsciiLetter(ch) || isDigit(ch) || i < cch - 1 && ch == '-'))
                {
                return false;
                }
            }

        return true;
        }

    /**
     * Validate the specified module name.
     *
     * @param sName  the module name
     *
     * @return true iff the name is a lexically valid qualified module name
     */
    public static boolean isValidQualifiedModule(String sName)
        {
        if (sName == null)
            {
            return false;
            }

        final String[] asName = parseDelimitedString(sName, '.');
        final int      cNames = asName.length;
        if (cNames < 1)
            {
            return false;
            }

        boolean fIdentifierAllowed = true;
        for (int i = 0; i < cNames; ++i)
            {
            final String s = asName[i];
            if (fIdentifierAllowed)
                {
                if (isValidIdentifier(s))
                    {
                    continue;
                    }
                else
                    {
                    fIdentifierAllowed = false;
                    }
                }

            if (!isValidRFC1035Label(s))
                {
                return false;
                }
            }

        return true;
        }


    // ----- constants ---------------------------------------------------------

    /**
     * Unicode: Horizontal Tab.
     */
    public static final char HT  = 0x0009;
    /**
     * Unicode: Line Feed.
     */
    public static final char LF  = 0x000A;
    /**
     * Unicode: Vertical Tab.
     */
    public static final char VT  = 0x000B;
    /**
     * Unicode: Form Feed.
     */
    public static final char FF  = 0x000C;
    /**
     * Unicode: Carriage Return.
     */
    public static final char CR  = 0x000D;
    /**
     * Unicode: End-Of-File aka control-z aka SUB.
     */
    public static final char EOF = 0x001A;
    /**
     * Unicode: File Separator.
     */
    public static final char FS  = 0x001C;
    /**
     * Unicode: Group Separator.
     */
    public static final char GS  = 0x001D;
    /**
     * Unicode: Record Separator.
     */
    public static final char RS  = 0x001E;
    /**
     * Unicode: Unit Separator.
     */
    public static final char US  = 0x001F;
    /**
     * Unicode: Next Line.
     */
    public static final char NEL = 0x0085;
    /**
     * Unicode: Non-Breaking Space aka "&nbsp".
     */
    public static final char NBS = 0x00A0;
    /**
     * Unicode: Line Separator.
     */
    public static final char LS  = 0x2028;
    /**
     * Unicode: Paragraph Separator.
     */
    public static final char PS  = 0x2029;

    /**
     * Unexpected End-Of-File (SUB character).
     */
    public static final String UNEXPECTED_EOF       = "PARSE-01";
    /**
     * Expected a comment-ending "star slash" but never found one.
     */
    public static final String EXPECTED_ENDCOMMENT  = "PARSE-02";
    /**
     * A character was encountered that cannot be the start of a valid token.
     */
    public static final String ILLEGAL_CHAR         = "PARSE-03";


    // ----- data members ------------------------------------------------------

    /**
     * The Source to parse.
     */
    private Source m_source;

    /**
     * The ErrorListener to report errors to.
     */
    private ErrorListener m_errorListener;
    }

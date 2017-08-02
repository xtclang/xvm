package org.xvm.compiler;


import java.math.BigDecimal;
import java.math.BigInteger;

import java.net.IDN;

import java.util.Iterator;
import java.util.NoSuchElementException;

import java.util.function.Consumer;

import org.xvm.compiler.Token.Id;

import org.xvm.util.PackedInteger;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.hexitValue;
import static org.xvm.util.Handy.isAsciiLetter;
import static org.xvm.util.Handy.isDigit;
import static org.xvm.util.Handy.isHexit;
import static org.xvm.util.Handy.parseDelimitedString;
import static org.xvm.util.Handy.quotedChar;


/**
 * An XTC source code parser supporting both demand-based and stream-based
 * parsing.
 *
 * @author cp 2015.11.09
 */
public class Lexer
        implements Iterator<Token>
    {
    // ----- constructors ------------------------------------------------------

    /**
     * Construct an XTC lexical analyzer.
     *
     * @param source  the source to parse
     */
    public Lexer(Source source, ErrorListener errorListener)
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
        boolean fWhitespaceBefore = m_fWhitespace;
        final Token token = eatToken();
        boolean fWhitespaceAfter = eatWhitespace();
        token.noteWhitespace(fWhitespaceBefore, fWhitespaceAfter);
        return token;
        }

    /**
     * Turn on a special pass-through mode for binary data encoded as hex characters.
     *
     * While in hex mode, each group of contiguos hex characters are return as a literal string
     * token.
     *
     * The lexer exits hex mode as soon as a non-hex, non-whitespace character is encountered.
     */
    public void expectHex()
        {
        m_fHexMode = true;
        }

    // ----- public API --------------------------------------------------------

    /**
     * Lexically analyze the source, emitting a stream of tokens to the
     * specified consumer.
     *
     * @param consumer  the Token Consumer
     */
    public void emit(Consumer<Token> consumer)
        {
        final Source source = m_source;
        while (source.hasNext())
            {
            consumer.accept(next());
            eatWhitespace();
            }
        }


    // ----- internal ----------------------------------------------------------

    /**
     * Eat the characters defined as whitespace, which include line terminators
     * and the file terminator. Whitespace does not include comments.
     */
    protected boolean eatWhitespace()
        {
        boolean fWhitespace = false;
        final Source source = m_source;
        while (source.hasNext())
            {
            if (isWhitespace(nextChar()))
                {
                fWhitespace = true;
                }
            else
                {
                // put back the non-whitespace character
                source.rewind();
                break;
                }
            }
        m_fWhitespace = fWhitespace;
        return fWhitespace;
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
        final char   chInit   = nextChar();

        if (m_fHexMode)
            {
            if (isHexit(chInit))
                {
                StringBuilder sb = new StringBuilder();
                char ch = chInit;
                while (true)
                    {
                    sb.append(ch);
                    if (source.hasNext())
                        {
                        ch = source.next();
                        if (!isHexit(ch))
                            {
                            source.rewind();
                            break;
                            }
                        }
                    else
                        {
                        break;
                        }
                    }
                return new Token(lInitPos, source.getPosition(), Id.LIT_STRING, sb.toString());
                }

            m_fHexMode = false;
            }

        switch (chInit)
            {
            case '{':
                return new Token(lInitPos, source.getPosition(), Id.L_CURLY);
            case '}':
                return new Token(lInitPos, source.getPosition(), Id.R_CURLY);
            case '(':
                return new Token(lInitPos, source.getPosition(), Id.L_PAREN);
            case ')':
                return new Token(lInitPos, source.getPosition(), Id.R_PAREN);
            case '[':
                return new Token(lInitPos, source.getPosition(), Id.L_SQUARE);
            case ']':
                return new Token(lInitPos, source.getPosition(), Id.R_SQUARE);

            case ';':
                return new Token(lInitPos, source.getPosition(), Id.SEMICOLON);
            case ',':
                return new Token(lInitPos, source.getPosition(), Id.COMMA);
            case '.':
                if (source.hasNext())
                    {
                    switch (nextChar())
                        {
                        case '.':
                            if (source.hasNext())
                                {
                                if (nextChar() == '.')
                                    {
                                    return new Token(lInitPos, source.getPosition(), Id.ELLIPSIS);
                                    }
                                source.rewind();
                                }
                            return new Token(lInitPos, source.getPosition(), Id.DOTDOT);

                        case '0': case '1': case '2': case '3': case '4':
                        case '5': case '6': case '7': case '8': case '9':
                            source.rewind();
                            source.rewind();
                            return eatNumericLiteral();
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.DOT);
            case '@':
                return new Token(lInitPos, source.getPosition(), Id.AT);

            case '?':
                if (source.hasNext())
                    {
                    switch (nextChar())
                        {
                        case '=':
                            return new Token(lInitPos, source.getPosition(), Id.COND_ASN);

                        case ':':
                            return new Token(lInitPos, source.getPosition(), Id.COND_ELSE);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.COND);

            case ':':
                return new Token(lInitPos, source.getPosition(), Id.COLON);

            case '+':
                if (source.hasNext())
                    {
                    switch (nextChar())
                        {
                        case '+':
                            return new Token(lInitPos, source.getPosition(), Id.INC);

                        case '=':
                            return new Token(lInitPos, source.getPosition(), Id.ADD_ASN);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.ADD);

            case '-':
                if (source.hasNext())
                    {
                    switch (nextChar())
                        {
                        case '-':
                            return new Token(lInitPos, source.getPosition(), Id.DEC);

                        case '>':
                            return new Token(lInitPos, source.getPosition(), Id.LAMBDA);

                        case '=':
                            return new Token(lInitPos, source.getPosition(), Id.SUB_ASN);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.SUB);

            case '*':
                if (source.hasNext())
                    {
                    if (nextChar() == '=')
                        {
                        return new Token(lInitPos, source.getPosition(), Id.MUL_ASN);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.MUL);

            case '/':
                if (source.hasNext())
                    {
                    switch (nextChar())
                        {
                        case '/':
                            return eatSingleLineComment(lInitPos);

                        case '*':
                            return eatEnclosedComment(lInitPos);

                        case '=':
                            return new Token(lInitPos, source.getPosition(), Id.DIV_ASN);

                        case '%':
                            if (source.hasNext())
                                {
                                if (nextChar() == '=')
                                    {
                                    return new Token(lInitPos, source.getPosition(), Id.DIVMOD_ASN);
                                    }
                                source.rewind();
                                }
                            return new Token(lInitPos, source.getPosition(), Id.DIVMOD);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.DIV);

            case '<':
                if (source.hasNext())
                    {
                    switch (nextChar())
                        {
                        case '<':
                            if (source.hasNext())
                                {
                                if (nextChar() == '=')
                                    {
                                    return new Token(lInitPos, source.getPosition(), Id.SHL_ASN);
                                    }
                                source.rewind();
                                }
                            return new Token(lInitPos, source.getPosition(), Id.SHL);

                        case '=':
                            if (source.hasNext())
                                {
                                if (nextChar() == '>')
                                    {
                                    return new Token(lInitPos, source.getPosition(), Id.COMP_ORD);
                                    }
                                source.rewind();
                                }
                            return new Token(lInitPos, source.getPosition(), Id.COMP_LTEQ);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.COMP_LT);

            case '>':
                if (source.hasNext())
                    {
                    switch (nextChar())
                        {
                        case '>':
                            if (source.hasNext())
                                {
                                switch (nextChar())
                                    {
                                    case '>':
                                        if (source.hasNext())
                                            {
                                            if (nextChar() == '=')
                                                {
                                                return new Token(lInitPos, source.getPosition(), Id.USHR_ASN);
                                                }
                                            source.rewind();
                                            }
                                        return new Token(lInitPos, source.getPosition(), Id.USHR);

                                    case '=':
                                        return new Token(lInitPos, source.getPosition(), Id.SHR_ASN);
                                    }
                                source.rewind();
                                }
                            return new Token(lInitPos, source.getPosition(), Id.SHR);

                        case '=':
                            return new Token(lInitPos, source.getPosition(), Id.COMP_GTEQ);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.COMP_GT);

            case '&':
                if (source.hasNext())
                    {
                    switch (nextChar())
                        {
                        case '&':
                            return new Token(lInitPos, source.getPosition(), Id.COND_AND);

                        case '=':
                            return new Token(lInitPos, source.getPosition(), Id.BIT_AND_ASN);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.BIT_AND);

            case '|':
                if (source.hasNext())
                    {
                    switch (nextChar())
                        {
                        case '|':
                            return new Token(lInitPos, source.getPosition(), Id.COND_OR);

                        case '=':
                            return new Token(lInitPos, source.getPosition(), Id.BIT_OR_ASN);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.BIT_OR);

            case '=':
                if (source.hasNext())
                    {
                    if (nextChar() == '=')
                        {
                        return new Token(lInitPos, source.getPosition(), Id.COMP_EQ);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.ASN);

            case '%':
                if (source.hasNext())
                    {
                    if (nextChar() == '=')
                        {
                        return new Token(lInitPos, source.getPosition(), Id.MOD_ASN);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.MOD);

            case '!':
                if (source.hasNext())
                    {
                    if (nextChar() == '=')
                        {
                        return new Token(lInitPos, source.getPosition(), Id.COMP_NEQ);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.NOT);

            case '^':
                if (source.hasNext())
                    {
                    if (nextChar() == '=')
                        {
                        return new Token(lInitPos, source.getPosition(), Id.BIT_XOR_ASN);
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.BIT_XOR);

            case '~':
                return new Token(lInitPos, source.getPosition(), Id.BIT_NOT);

            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                source.rewind();
                return eatNumericLiteral();

            case '\'':
                source.rewind();
                return eatCharLiteral();

            case '\"':
                source.rewind();
                return eatStringLiteral();

            case '┌':
            case '┍':
            case '┎':
            case '┏':
            case '╒':
            case '╓':
            case '╔':
            case '╭':
                source.rewind();
                return eatFreeformLiteral();

            default:
                if (!isIdentifierStart(chInit))
                    {
                    log(Severity.ERROR, ILLEGAL_CHAR, new Object[]{quotedChar(chInit)},
                            lInitPos, source.getPosition());
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
            case '_':
                {
                while (source.hasNext())
                    {
                    if (!isIdentifierPart(nextChar()))
                        {
                        source.rewind();
                        break;
                        }
                    }

                String name = source.toString(lInitPos, source.getPosition());
                if (Token.Id.valueByPrefix(name) != null && source.hasNext())
                    {
                    long lPos = source.getPosition();
                    if (source.next() == ':')
                        {
                        // check for a legal suffix, e.g. "this:private"
                        while (source.hasNext())
                            {
                            if (!isIdentifierPart(nextChar()))
                                {
                                source.rewind();
                                break;
                                }
                            }

                        String full = source.toString(lInitPos, source.getPosition());
                        if (Id.valueByContextSensitiveText(full) != null)
                            {
                            name = full;
                            }
                        else
                            {
                            // false alarm; back up and just take "this"
                            source.setPosition(lPos);
                            }
                        }
                    else
                        {
                        source.rewind();
                        }
                    }
                else if (name.equals(Id.TODO.TEXT) && source.hasNext())
                    {
                    char chNext = source.next();
                    source.rewind();

                    if (chNext != '(')
                        {
                        // parse the to-do statement in the same manner as a single line comment
                        Token comment = eatSingleLineComment(lInitPos);
                        return new Token(comment.getStartPosition(), comment.getEndPosition(),
                                Id.TODO, comment.getValue());
                        }
                    }

                Id id = Id.valueByText(name);
                return id == null
                        ? new Token(lInitPos, source.getPosition(), Id.IDENTIFIER, name)
                        : new Token(lInitPos, source.getPosition(), id); 
                }
            }
        }

    /**
     * Eat a character literal.
     *
     * @return a character literal as a token
     */
    protected Token eatCharLiteral()
        {
        final Source source = m_source;

        // skip opening quote
        final long lPosStart = source.getPosition();
        source.next();
        final long lPosChar = source.getPosition();

        char    ch   = '?';
        boolean term = false;
        if (source.hasNext())
            {
            switch (ch = source.next())
                {
                case '\'':
                    if (source.hasNext())
                        {
                        if (source.next() == '\'')
                            {
                            // assume the previous one should have been escaped
                            source.rewind();
                            log(Severity.ERROR, CHAR_BAD_ESC, null,
                                    lPosChar, source.getPosition());
                            }
                        else
                            {
                            // assume the encountered quote that we thought was supposed to be
                            // the character value was instead supposed to be closing quote
                            source.rewind();
                            source.rewind();
                            log(Severity.ERROR, CHAR_NO_CHAR, null,
                                    lPosChar, lPosChar);
                            }
                        }
                    break;

                case '\\':
                    // process escaped char
                    switch (ch = source.next())
                        {
                        case '\r':
                        case '\n':
                            // log error: newline in string
                            source.rewind();
                            log(Severity.ERROR, CHAR_NO_TERM, null,
                                    lPosStart, source.getPosition());
                            // assume it wasn't supposed to be an escape
                            ch = '\\';
                            break;

                        case '\\':
                        case '\'':
                        case '\"':
                            break;
                        
                        case 'b':
                            ch = '\b';
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

                        default:
                            // log error: bad escape
                            log(Severity.ERROR, CHAR_BAD_ESC, null,
                                    lPosChar, source.getPosition());
                            break;
                        }
                    break;

                case '\r':
                case '\n':
                    // log error: newline in string
                    source.rewind();
                    log(Severity.ERROR, CHAR_NO_TERM, null,
                            lPosStart, source.getPosition());
                    break;

                default:
                    break;
                }

            if (source.hasNext())
                {
                if (source.next() == '\'')
                    {
                    term = true;
                    }
                else
                    {
                    source.rewind();
                    }
                }
            }

        if (!term)
            {
            // log error: unterminated string
            log(Severity.ERROR, CHAR_NO_TERM, null,
                    lPosStart, source.getPosition());
            }

        return new Token(lPosStart, source.getPosition(), Id.LIT_CHAR, new Character(ch));
        }

    /**
     * Eat a string literal.
     *
     * @return a string literal as a token
     */
    protected Token eatStringLiteral()
        {
        final Source source = m_source;

        // skip opening quote
        final long lPosStart = source.getPosition();
        source.next();

        StringBuilder sb = new StringBuilder();
        Appending: while (true)
            {
            if (source.hasNext())
                {
                char ch = source.next();
                switch (ch)
                    {
                    case '\"':
                        break Appending;

                    case '\\':
                        // process escaped char
                        switch (ch = source.next())
                            {
                            case '\r':
                            case '\n':
                                // log error: newline in string
                                source.rewind();
                                log(Severity.ERROR, STRING_NO_TERM, null,
                                        lPosStart, source.getPosition());
                                // assume it wasn't supposed to be an escape
                                sb.append('\\');
                                break Appending;

                            case '\\':
                                sb.append('\\');
                                break;
                            case '\'':
                                sb.append('\'');
                                break;
                            case '\"':
                                sb.append('\"');
                                break;
                            case 'b':
                                sb.append('\b');
                                break;
                            case 'f':
                                sb.append('\f');
                                break;
                            case 'n':
                                sb.append('\n');
                                break;
                            case 'r':
                                sb.append('\r');
                                break;
                            case 't':
                                sb.append('\t');
                                break;

                            default:
                                // log error: bad escape
                                long lPosEscEnd = source.getPosition();
                                source.rewind();
                                source.rewind();
                                log(Severity.ERROR, STRING_BAD_ESC, null,
                                        source.getPosition(), lPosEscEnd);
                                source.setPosition(lPosEscEnd);

                                // assume it wasn't supposed to be an escape:
                                // append both the escape char and the escaped char
                                sb.append('\\')
                                  .append(ch);
                                break;
                            }
                        break;

                    case '\r':
                    case '\n':
                        // log error: newline in string
                        source.rewind();
                        log(Severity.ERROR, STRING_NO_TERM, null,
                                lPosStart, source.getPosition());
                        break Appending;

                    default:
                        sb.append(ch);
                        break;
                    }
                }
            else
                {
                // log error: unterminated string
                log(Severity.ERROR, STRING_NO_TERM, null,
                        lPosStart, source.getPosition());
                }
            }

        return new Token(lPosStart, source.getPosition(), Id.LIT_STRING, sb.toString());
        }

    /**
     * Eat a "free form" literal:
     *
     * <p/><code><pre>
     * #   ╔═════════════════════╗
     * #   ║This could be any    ║
     * #   ║freeform text that   ║
     * #   ║could be inside of an║
     * #   ║Ecstasy source file  ║
     * #   ╚═════════════════════╝
     * #
     * #        U+2550
     * # U+2554 ╔═════╗ U+2557
     * # U+2551 ║     ║ U+2551
     * # U+255A ╚═════╝ U+255D
     * #        U+2550
     * #
     * #
     * #        U+2500
     * # U+256D ╭─────╮ U+256E
     * # U+2502 │     │ U+2502
     * # U+2570 ╰─────╯ U+256F
     * #        U+2500
     * #
     * FreeformLiteral
     *     FreeformTop FreeformLines FreeformBottom
     *
     * FreeformTop
     *     Whitespace-opt FreeformUpperLeft NoWhitespace FreeformHorizontals NoWhitespace FreeformUpperRight Whitespace-opt LineTerminator
     *
     * FreeformLines
     *     FreeformLine
     *     FreeformLines FreeformLine
     *
     * FreeformLine
     *     Whitespace-opt FreeformVertical FreeformChars FreeformVertical Whitespace-opt LineTerminator
     *
     * FreeformChars
     *     FreeformChar
     *     FreeformChars FreeformChars
     *
     * FreeformChar
     *     InputCharacter except FreeFormReserved or LineTerminator
     *
     * FreeformBottom
     *     Whitespace-opt FreeformLowerLeft NoWhitespace FreeformHorizontals NoWhitespace FreeformLowerRight
     *
     * FreeFormReserved
     *     FreeformUpperLeft
     *     FreeformUpperRight
     *     FreeformLowerLeft
     *     FreeformLowerRight
     *     FreeformHorizontal
     *     FreeformVertical
     *
     * FreeformUpperLeft
     *     U+250C  ┌
     *     U+250D  ┍
     *     U+250E  ┎
     *     U+250F  ┏
     *     U+2552  ╒
     *     U+2553  ╓
     *     U+2554  ╔
     *     U+256D  ╭
     *
     * FreeformUpperRight
     *     U+2510  ┐
     *     U+2511  ┑
     *     U+2512  ┒
     *     U+2513  ┓
     *     U+2555  ╕
     *     U+2556  ╖
     *     U+2557  ╗
     *     U+256E  ╮
     *
     * FreeformLowerLeft
     *     U+2514  └
     *     U+2515  ┕
     *     U+2516  ┖
     *     U+2517  ┗
     *     U+2558  ╘
     *     U+2559  ╙
     *     U+255A  ╚
     *     U+2570  ╰
     *
     * FreeformLowerRight
     *     U+2518  ┘
     *     U+2519  ┙
     *     U+251A  ┚
     *     U+251B  ┛
     *     U+255B  ╛
     *     U+255C  ╜
     *     U+255D  ╝
     *     U+256F  ╯
     *
     * FreeformHorizontals
     *     FreeformHorizontal
     *     FreeformHorizontals NoWhitespace FreeformHorizontal
     *
     * FreeformHorizontal
     *     U+2500  ─
     *     U+2501  ━
     *     U+2504  ┄
     *     U+2505  ┅
     *     U+2508  ┈
     *     U+2509  ┉
     *     U+254C  ╌
     *     U+254D  ╍
     *     U+2550  ═
     *
     * FreeformVertical
     *     U+2502  │
     *     U+2503  ┃
     *     U+2506  ┆
     *     U+2507  ┇
     *     U+250A  ┊
     *     U+250B  ┋
     *     U+254E  ╎
     *     U+254F  ╏
     *     U+2551  ║
     * </pre></code>
     *
     * @return
     */
    protected Token eatFreeformLiteral()
        {
        final Source source    = m_source;
        final long   lPosStart = source.getPosition();
        StringBuilder sb = new StringBuilder();

        char ch = source.next();
        if (!isFreeformUpperLeft(ch) || !source.hasNext())
            {
            return badFreeform(sb);
            }

        ch = source.next();
        while (isFreeformHorizontal(ch) && source.hasNext())
            {
            ch = source.next();
            }

        if (!isFreeformUpperRight(ch) || !source.hasNext())
            {
            return badFreeform(sb);
            }

        ch = source.next();
        boolean firstLine = true;
        while (source.hasNext())
            {
            // parse non-line-terminating whitespace followed by a line terminator followed by
            // non-line-terminating whitespace followed by a "freeform vertical"
            boolean fLineTerminated = false;
            while (isWhitespace(ch) && source.hasNext())
                {
                if ((isLineTerminator(ch)))
                    {
                    if (fLineTerminated)
                        {
                        return badFreeform(sb);
                        }
                    else if (ch == '\r' && source.hasNext() && source.next() != '\n')
                        {
                        // we were looking for cr:lf but only found cr
                        source.rewind();
                        }
                    fLineTerminated = true;
                    }
                ch = source.next();
                }
            if (!fLineTerminated)
                {
                return badFreeform(sb);
                }

            if (isFreeformLowerLeft(ch))
                {
                break;
                }

            // opening vertical
            if (!isFreeformVertical(ch) || !source.hasNext())
                {
                return badFreeform(sb);
                }

            if (firstLine)
                {
                firstLine = false;
                }
            else
                {
                sb.append('\n');
                }

            // parse freeform text
            ch = source.next();
            int nonWhiteSpaceLength = sb.length();
            while (!isFreeformVertical(ch) && !isLineTerminator(ch) && source.hasNext())
                {
                sb.append(ch);
                if (!isWhitespace(ch))
                    {
                    nonWhiteSpaceLength = sb.length();
                    }
                ch = source.next();
                }

            // chop off any trailing whitespace
            if (sb.length() > nonWhiteSpaceLength)
                {
                sb.setLength(nonWhiteSpaceLength);
                }

            // closing vertical
            if (!isFreeformVertical(ch) || !source.hasNext())
                {
                return badFreeform(sb);
                }

            ch = source.next();
            }

        if (!isFreeformLowerLeft(ch) || !source.hasNext())
            {
            return badFreeform(sb);
            }

        ch = source.next();
        while (isFreeformHorizontal(ch) && source.hasNext())
            {
            ch = source.next();
            }

        if (!isFreeformLowerRight(ch))
            {
            return badFreeform(sb);
            }

        return new Token(lPosStart, source.getPosition(), Id.LIT_STRING, sb.toString());
        }

    protected Token badFreeform(StringBuilder sb)
        {
        // pretend we parsed something successfully
        final Source source  = m_source;
        final Token  literal = new Token(source.getPosition(), source.getPosition(), Id.LIT_STRING, sb.toString());

        log(Severity.ERROR, FREEFORM_BAD, null, source.getPosition(), source.getPosition());

        // expurgate the remainder of the literal (or the remainder of the file if necessary)
        while (source.hasNext() && !isFreeformLowerRight(source.next()))
            {
            }

        return literal;
        }

    /**
     * Determine if the specified character is a free-form upper-left.
     *
     * @param ch  the character
     *
     * @return true iff the character is a free-form upper left corner
     */
    public static boolean isFreeformUpperLeft(char ch)
        {
        switch (ch)
            {
            case '┌':
            case '┍':
            case '┎':
            case '┏':
            case '╒':
            case '╓':
            case '╔':
            case '╭':
                return true;

            default:
                return false;
            }
        }

    /**
     * Determine if the specified character is a free-form upper-right.
     *
     * @param ch  the character
     *
     * @return true iff the character is a free-form upper right corner
     */
    public static boolean isFreeformUpperRight(char ch)
        {
        switch (ch)
            {
            case '┐':
            case '┑':
            case '┒':
            case '┓':
            case '╕':
            case '╖':
            case '╗':
            case '╮':
                return true;

            default:
                return false;
            }
        }

    /**
     * Determine if the specified character is a free-form lower-left.
     *
     * @param ch  the character
     *
     * @return true iff the character is a free-form lower left corner
     */
    public static boolean isFreeformLowerLeft(char ch)
        {
        switch (ch)
            {
            case '└':
            case '┕':
            case '┖':
            case '┗':
            case '╘':
            case '╙':
            case '╚':
            case '╰':
                return true;

            default:
                return false;
            }
        }

    /**
     * Determine if the specified character is a free-form lower-right.
     *
     * @param ch  the character
     *
     * @return true iff the character is a free-form lower right corner
     */
    public static boolean isFreeformLowerRight(char ch)
        {
        switch (ch)
            {
            case '┘':
            case '┙':
            case '┚':
            case '┛':
            case '╛':
            case '╜':
            case '╝':
            case '╯':
                return true;

            default:
                return false;
            }
        }

    /**
     * Determine if the specified character is a free-form top or bottom horizontal piece.
     *
     * @param ch  the character
     *
     * @return true iff the character is a free-form horizontal
     */
    public static boolean isFreeformHorizontal(char ch)
        {
        switch (ch)
            {
            case '─':
            case '━':
            case '┄':
            case '┅':
            case '┈':
            case '┉':
            case '╌':
            case '╍':
            case '═':
                return true;

            default:
                return false;
            }
        }

    /**
     * Determine if the specified character is a free-form left or right vertical piece.
     *
     * @param ch  the character
     *
     * @return true iff the character is a free-form vertical
     */
    public static boolean isFreeformVertical(char ch)
        {
        switch (ch)
            {
            case '│':
            case '┃':
            case '┆':
            case '┇':
            case '┊':
            case '┋':
            case '╎':
            case '╏':
            case '║':
                return true;

            default:
                return false;
            }
        }

    /**
     * Eat a numeric literal.
     *
     * @return the numeric literal as a Token
     */
    protected Token eatNumericLiteral()
        {
        final Source source    = m_source;
        final long   lPosStart = source.getPosition();

        // eat the first part of the number (or the entire number, if it is an integer literal)
        int[] results = new int[2];
        PackedInteger piWhole = eatIntegerLiteral(results);
        int mantissaRadix = results[0];
        int signScalar    = results[1];

        // parse optional '.' + value
        PackedInteger piFraction = null;
        int           fractionalDigits = 0;
        if (source.hasNext())
            {
            if (source.next() == '.')
                {
                // could be ".."
                if (source.hasNext())
                    {
                    if (source.next() == '.')
                        {
                        source.rewind();
                        source.rewind();
                        return new Token(lPosStart, source.getPosition(), Id.LIT_INT, piWhole);
                        }
                    }

                piFraction = eatDigits(false, mantissaRadix, results);
                fractionalDigits = results[0];

                if (fractionalDigits == 0)
                    {
                    log(Severity.ERROR, ILLEGAL_NUMBER, null,
                            lPosStart, source.getPosition());
                    }
                }
            else
                {
                source.rewind();
                }
            }

        // parse optional exponent
        PackedInteger piExp = null;
        boolean mustBeBinary = false;
        if (source.hasNext())
            {
            char ch = source.next();
            switch (ch)
                {
                case 'E': case 'e':
                    piExp = eatIntegerLiteral(null);
                    break;

                case 'P': case 'p':
                    piExp = eatIntegerLiteral(null);
                    mustBeBinary = true;
                    break;

                default:
                    // anything else should be whitespace or some type of operator/separator
                    long lEndPos = source.getPosition();
                    source.rewind();
                    if (isIdentifierPart(ch))
                        {
                        log(Severity.ERROR, ILLEGAL_NUMBER, null,
                                source.getPosition(), lEndPos);
                        }
                    break;
                }
            }

        final long lPosEnd = source.getPosition();
        if (piFraction == null && piExp == null)
            {
            return new Token(lPosStart, lPosEnd, Id.LIT_INT, piWhole);
            }
        else if (!mustBeBinary && mantissaRadix == 10)
            {
            // TODO convert to IEEE-754 decimal floating point format
            BigDecimal dec;
            if (piFraction == null)
                {
                dec = new BigDecimal(piWhole.getBigInteger());
                }
            else
                {
                BigInteger biWhole = piWhole.getBigInteger();
                if (biWhole.signum() < 0)
                    {
                    biWhole = biWhole.negate();
                    }
                dec = new BigDecimal(biWhole.multiply(BigInteger.valueOf(10 * fractionalDigits))
                        .add(piFraction.getBigInteger()), fractionalDigits);
                if (signScalar < 0)
                    {
                    // the unfortunate side-effect of not having a -0
                    dec = dec.negate();
                    }
                }
            if (piExp != null)
                {
                long lExp = piExp.getLong();
                if (lExp > 6144 || lExp < (1-6144))
                    {
                    log(Severity.ERROR, ILLEGAL_NUMBER, null,
                            lPosStart, lPosEnd);
                    }
                else
                    {
                    dec = dec.scaleByPowerOfTen((int) lExp);
                    }
                }
            // TODO exponent
            return new Token(lPosStart, lPosEnd, Id.LIT_DEC, dec);
            }
        else
            {
            // convert to IEEE-754 binary floating point format
            // TODO
            return new Token(lPosStart, lPosEnd, Id.LIT_BIN, source.toString(lPosStart, lPosEnd));
            }
        }

    /**
     * The next character must begin an integer literal. Parse it and return it as a PackedInteger.
     *
     * @return a PackedInteger
     */
    protected PackedInteger eatIntegerLiteral(int[] otherResults)
        {
        final Source source    = m_source;
        final long   lPosStart = source.getPosition();

        // the first character could be a sign (+ or -)
        boolean fNeg = false;
        char    ch   = needCharOrElse(ILLEGAL_NUMBER);
        if (ch == '+' || ch == '-')
            {
            fNeg = (ch == '-');
            ch   = needCharOrElse(ILLEGAL_NUMBER);
            }

        // if the next character is '0', it is potentially part of a prefix denoting a radix
        int radix = 10;
        if (ch == '0' && source.hasNext())
            {
            switch (nextChar())
                {
                case 'B':
                case 'b':
                    radix = 2;
                    break;
                case 'o':
                    radix = 8;
                    break;
                case 'X':
                case 'x':
                    radix = 16;
                    break;
                default:
                    source.rewind();
                    source.rewind();
                    break;
                }
            }
        else
            {
            source.rewind();
            }

        // don't you just wish that Java had multiple return values?
        if (otherResults != null)
            {
            if (otherResults.length > 0)
                {
                otherResults[0] = radix;
                }
            if (otherResults.length > 1)
                {
                otherResults[1] = fNeg ? -1 : 1;
                }
            }

        return eatDigits(fNeg, radix, null);
        }

    /**
     * The next character must begin a sequence of digits of the specified radix. Parse it and
     * return it as a PackedInteger.
     *
     * @return a PackedInteger
     */
    protected PackedInteger eatDigits(boolean fNeg, int radix, int[] digitCount)
        {
        long       lValue  = 0;
        BigInteger bigint  = null;   // just in case
        boolean    fError  = false;
        int        cDigits = 0;

        final Source source = m_source;
        final long lPosStart = source.getPosition();
        Parsing: while (source.hasNext())
            {
            final long lPos = source.getPosition();
            final char ch   = nextChar();
            switch (ch)
                {
                case 'A': case 'B': case 'C': case 'D': case 'E': case 'F':
                case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
                    if (radix < 16)
                        {
                        // "e" is used as the decimal exponent indicator
                        if (ch != 'E' && ch != 'e')
                            {
                            if (!fError)
                                {
                                log(Severity.ERROR, ILLEGAL_NUMBER, null,
                                        lPos, source.getPosition());
                                fError = true;
                                }
                            }
                        source.rewind();
                        break Parsing;
                        }
                    break;

                case '9': case '8':
                    if (radix < 10)
                        {
                        if (!fError)
                            {
                            log(Severity.ERROR, ILLEGAL_NUMBER, null,
                                    lPos, source.getPosition());
                            fError = true;
                            }
                        // while an error was encountered, it was at least a digit, so continue
                        // parsing those digits (even if they are bad)
                        continue Parsing;
                        }
                    break;

                case '7': case '6': case '5': case '4': case '3': case '2':
                    if (radix < 8)
                        {
                        if (!fError)
                            {
                            log(Severity.ERROR, ILLEGAL_NUMBER, null,
                                    lPos, source.getPosition());
                            fError = true;
                            }
                        // while an error was encountered, it was at least a digit, so continue
                        // parsing those digits (even if they are bad)
                        continue Parsing;
                        }
                    break;

                case '1': case '0':
                    break;

                case '_':
                    if (cDigits == 0 && !fError)
                        {
                        // it's an error to start the sequence of digits with an underscore
                        log(Severity.ERROR, ILLEGAL_NUMBER, null,
                                lPos, source.getPosition());
                        fError = true;
                        }
                    continue Parsing;

                default:
                    // anything else (including '.') means go to the next step
                    source.rewind();
                    break Parsing;
                }

            if (bigint == null)
                {
                lValue = lValue * radix + hexitValue(ch);
                if (lValue > 0x00FFFFFFFFFFFFFFL)
                    {
                    bigint = BigInteger.valueOf(fNeg ? -lValue : lValue);
                    }
                }
            else
                {
                bigint = bigint.multiply(BigInteger.valueOf(radix)).add(BigInteger.valueOf(hexitValue(ch)));
                }
            ++cDigits;
            }

        if (!fError && cDigits == 0)
            {
            log(Severity.ERROR, ILLEGAL_NUMBER, null,
                    lPosStart, source.getPosition());
            }

        if (digitCount != null && digitCount.length > 0)
            {
            digitCount[0] = cDigits;
            }
        return bigint == null ? new PackedInteger(fNeg ? -lValue : lValue) : new PackedInteger(bigint);
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
        return new Token(lPosTokenStart, lPosEnd, Id.EOL_COMMENT,
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
                return new Token(lPosTokenStart, lPosTokenEnd, Id.ENC_COMMENT,
                        source.toString(lPosTextStart, lPosTextEnd));
                }
            else
                {
                fAsterisk = false;
                }
            }

        // missing the enclosing "*/"
        log(Severity.ERROR, EXPECTED_ENDCOMMENT, null,
                lPosTextStart, source.getPosition());

        // just pretend that the rest of the file was all one big comment
        return new Token(lPosTokenStart, source.getPosition(), Id.ENC_COMMENT,
                source.toString(lPosTextStart, source.getPosition()));
        }

    /**
     * Obtain a cookie that represents the lexer's current location.
     *
     * @return a position cookie
     */
    public long getPosition()
        {
        // this adds a bit of information to the source's position info
        long lPos = m_source.getPosition();
        assert (lPos & (3L << 62)) == 0L;
        if (m_fWhitespace)
            {
            lPos |= (1L << 63);
            }
        if (m_fHexMode)
            {
            lPos |= (1L << 62);
            }
        return lPos;
        }

    /**
     * Using a previously returned position cookie, restore that state of the lexer.
     *
     * @param lPos  a previously returned position cookie
     */
    public void setPosition(long lPos)
        {
        m_fWhitespace = (lPos & (1L << 63)) != 0L;
        m_fHexMode    = (lPos & (1L << 62)) != 0L;
        m_source.setPosition(lPos & ~(1L << 63));
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
            final long lStartPos = m_source.getPosition();
            m_source.setPosition(lPos);

            log(Severity.ERROR, UNEXPECTED_EOF, null,
                    lStartPos, m_source.getPosition());
            }

        return ch;
        }

    /**
     * Get the next character of source code, but do some additional checks
     * on the character to make sure it's legal, such as checking for an illegal
     * SUB character.
     */
    protected char needCharOrElse(String sError)
        {
        try
            {
            return nextChar();
            }
        catch (NoSuchElementException e)
            {
            log(Severity.ERROR, sError, null,
                    m_source.getPosition(), m_source.getPosition());
            }

        // already logged an error; just pretend we hit a closing brace (since all roads should have
        // gone there)
        return '}';
        }

    /**
     * Log an error.
     *
     * @param severity
     * @param sCode
     * @param aoParam
     * @param lPosStart
     * @param lPosEnd
     */
    protected void log(Severity severity, String sCode, Object[] aoParam, long lPosStart, long lPosEnd)
        {
        if (m_errorListener.log(severity, sCode, aoParam, m_source, lPosStart, lPosEnd))
            {
            throw new CompilerException("error list is full: " + m_errorListener);
            }
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
            //                                               2               1      0
            //                                               0FEDCBA9876543210FEDCBA9
            return ch >= 9 && ch <= 32 && ((1 << (ch-9)) & 0b111110100000000000011111) != 0;
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

        // check if it is a reserved word
        if (Token.Id.valueByText(sName) != null)
            {
            return false;
            }

        return true;
        }

    /**
     * Validate the specified RFC1035 label.
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

        // internationalization support; see https://tools.ietf.org/html/rfc5894
        // convert the label to an ASCII label
        try
            {
            sName = IDN.toASCII(sName);
            }
        catch (IllegalArgumentException e)
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

        // the first simple name must be a valid identifier
        if (!isValidIdentifier(asName[0]))
            {
            return false;
            }

        // the identifier must be followed by a domain name, which is composed of at least two
        // RFC1035 labels
        if (cNames == 2)
            {
            return false;
            }

        // check the optional domain name
        for (int i = 1; i < cNames; ++i)
            {
            if (!isValidRFC1035Label(asName[i]))
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
    public static final String UNEXPECTED_EOF       = "LEXER-01";
    /**
     * Expected a comment-ending "star slash" but never found one.
     */
    public static final String EXPECTED_ENDCOMMENT  = "LEXER-02";
    /**
     * A character was encountered that cannot be the start of a valid token.
     */
    public static final String ILLEGAL_CHAR         = "LEXER-03";
    /**
     * Number format exception.
     */
    public static final String ILLEGAL_NUMBER       = "LEXER-04";
    /**
     * An illegal character literal, missing closing quote.
     */
    public static final String CHAR_NO_TERM         = "LEXER-05";
    /**
     * An illegally escaped character literal.
     */
    public static final String CHAR_BAD_ESC         = "LEXER-06";
    /**
     * An illegal character literal missing the character.
     */
    public static final String CHAR_NO_CHAR         = "LEXER-07";
    /**
     * An illegal character string literal.
     */
    public static final String STRING_NO_TERM       = "LEXER-08";
    /**
     * An illegal character string literal.
     */
    public static final String STRING_BAD_ESC       = "LEXER-08";
    /**
     * An illegal freeform literal.
     */
    public static final String FREEFORM_BAD         = "LEXER-09";


    // ----- data members ------------------------------------------------------

    /**
     * The Source to parse.
     */
    private Source m_source;

    /**
     * The ErrorListener to report errors to.
     */
    private ErrorListener m_errorListener;

    /**
     * Keeps track of whether whitespace was encountered.
     */
    private boolean m_fWhitespace;

    /**
     * A special mode that parses raw hex literals.
     */
    private boolean m_fHexMode;
    }

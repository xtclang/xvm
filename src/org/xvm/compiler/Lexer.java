package org.xvm.compiler;


import java.math.BigDecimal;
import java.math.BigInteger;

import java.util.Iterator;

import java.util.NoSuchElementException;

import java.util.function.Consumer;

import org.xvm.compiler.Token.Id;

import org.xvm.util.PackedInteger;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.hexitValue;
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
        final Token token = eatToken();
        eatWhitespace();
        return token;
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
            consumer.accept(eatToken());
            eatWhitespace();
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
        final char   chInit   = nextChar();
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

            case ':':
                return new Token(lInitPos, source.getPosition(), Id.COLON);
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
                return new Token(lInitPos, source.getPosition(), Id.COND);

            case '+':
                if (source.hasNext())
                    {
                    switch (nextChar())
                        {
                        case '+':
                            return new Token(lInitPos, source.getPosition(), Id.INC);

                        case '=':
                            return new Token(lInitPos, source.getPosition(), Id.ADD_MOV);

                        case '0': case '1': case '2': case '3': case '4':
                        case '5': case '6': case '7': case '8': case '9':
                        case '.':
                            source.rewind();
                            source.rewind();
                            return eatNumericLiteral();
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

                        case '=':
                            return new Token(lInitPos, source.getPosition(), Id.SUB_MOV);

                        case '0': case '1': case '2': case '3': case '4':
                        case '5': case '6': case '7': case '8': case '9':
                        case '.':
                            source.rewind();
                            source.rewind();
                            return eatNumericLiteral();
                        }
                    source.rewind();
                    }
                return new Token(lInitPos, source.getPosition(), Id.SUB);

            case '*':
                if (source.hasNext())
                    {
                    if (nextChar() == '=')
                        {
                        return new Token(lInitPos, source.getPosition(), Id.MUL_MOV);
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
                            return new Token(lInitPos, source.getPosition(), Id.DIV_MOV);

                        case '%':
                            if (source.hasNext())
                                {
                                if (nextChar() == '=')
                                    {
                                    return new Token(lInitPos, source.getPosition(), Id.DIVMOD_MOV);
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
                                    return new Token(lInitPos, source.getPosition(), Id.SHL_MOV);
                                    }
                                source.rewind();
                                }
                            return new Token(lInitPos, source.getPosition(), Id.SHL);

                        case '=':
                            // TODO spaceship
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
                                                return new Token(lInitPos, source.getPosition(), Id.USHR_MOV);
                                                }
                                            source.rewind();
                                            }
                                        return new Token(lInitPos, source.getPosition(), Id.USHR);

                                    case '=':
                                        return new Token(lInitPos, source.getPosition(), Id.SHR_MOV);
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
                            return new Token(lInitPos, source.getPosition(), Id.BIT_AND_MOV);
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
                            return new Token(lInitPos, source.getPosition(), Id.BIT_OR_MOV);
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
                return new Token(lInitPos, source.getPosition(), Id.MOV);

            case '%':
                if (source.hasNext())
                    {
                    if (nextChar() == '=')
                        {
                        return new Token(lInitPos, source.getPosition(), Id.MOD_MOV);
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
                        return new Token(lInitPos, source.getPosition(), Id.BIT_XOR_MOV);
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

            default:
                if (!isIdentifierStart(chInit))
                    {
                    m_errorListener.log(Severity.ERROR, ILLEGAL_CHAR,
                            new Object[] {quotedChar(chInit)},
                            source, lInitPos, source.getPosition());
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
                    if (!isIdentifierPart(nextChar()))
                        {
                        source.rewind();
                        break;
                        }
                    }

                String s = source.toString(lInitPos, source.getPosition());
                Id id = Id.valueByText(s);
                return id == null
                        ? new Token(lInitPos, source.getPosition(), Id.IDENTIFIER, s)
                        : new Token(lInitPos, source.getPosition(), id); 
                }
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
                piFraction = eatDigits(false, mantissaRadix, results);
                fractionalDigits = results[0];

                if (fractionalDigits == 0)
                    {
                    m_errorListener.log(Severity.ERROR, ILLEGAL_NUMBER, null,
                            source, lPosStart, source.getPosition());
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
                        m_errorListener.log(Severity.ERROR, ILLEGAL_NUMBER, null,
                                source, source.getPosition(), lEndPos);
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
                    m_errorListener.log(Severity.ERROR, ILLEGAL_NUMBER, null,
                            source, lPosStart, lPosEnd);
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
                                m_errorListener.log(Severity.ERROR, ILLEGAL_NUMBER, null,
                                        source, lPos, source.getPosition());
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
                            m_errorListener.log(Severity.ERROR, ILLEGAL_NUMBER, null,
                                    source, lPos, source.getPosition());
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
                            m_errorListener.log(Severity.ERROR, ILLEGAL_NUMBER, null,
                                    source, lPos, source.getPosition());
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
                        m_errorListener.log(Severity.ERROR, ILLEGAL_NUMBER, null,
                                source, lPos, source.getPosition());
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
            m_errorListener.log(Severity.ERROR, ILLEGAL_NUMBER, null,
                    source, lPosStart, source.getPosition());
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
        m_errorListener.log(Severity.ERROR, EXPECTED_ENDCOMMENT, null,
                source, lPosTextStart, source.getPosition());

        // just pretend that the rest of the file was all one big comment
        return new Token(lPosTokenStart, source.getPosition(), Id.ENC_COMMENT,
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
            final long lStartPos = m_source.getPosition();
            m_source.setPosition(lPos);

            m_errorListener.log(Severity.ERROR, UNEXPECTED_EOF, null,
                    m_source, lStartPos, m_source.getPosition());
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
            m_errorListener.log(Severity.ERROR, sError, null,
                    m_source, m_source.getPosition(), m_source.getPosition());
            }

        // already logged an error; just pretend we hit a closing brace (since all roads should have
        // gone there)
        return '}';
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
    public static final String ILLEGAL_NUMBER       = "LEXER-03";


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

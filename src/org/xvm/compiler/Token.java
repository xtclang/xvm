package org.xvm.compiler;


import java.util.HashMap;
import java.util.Map;

import static org.xvm.util.Handy.appendChar;
import static org.xvm.util.Handy.appendString;


/**
 * Representation of a language token.
 *
 * @author cp 2015.11.11
 */
public class Token
    {
    // ----- constructors ------------------------------------------------------

    /**
     * Construct an XTC token.
     *
     * @param lStartPos  starting position in the Source (inclusive)
     * @param lEndPos    ending position in the Source (exclusive)
     * @param id         identity of the token
     */
    public Token(long lStartPos, long lEndPos, Id id)
        {
        this(lStartPos, lEndPos, id, null);
        }

    /**
     * Construct an XTC token.
     *
     * @param lStartPos  starting position in the Source (inclusive)
     * @param lEndPos    ending position in the Source (exclusive)
     * @param id         identity of the token
     * @param oValue     value of the token (if it is a literal)
     */
    public Token(long lStartPos, long lEndPos, Id id, Object oValue)
        {
        m_lStartPos = lStartPos;
        m_lEndPos   = lEndPos;
        m_id        = id;
        m_oValue    = oValue;
        }

    /**
     * Record whitespace information onto the token.
     *
     * @param fWhitespaceBefore
     * @param fWhitespaceAfter
     */
    public void noteWhitespace(boolean fWhitespaceBefore, boolean fWhitespaceAfter)
        {
        m_fLeadingWhitespace  = fWhitespaceBefore;
        m_fTrailingWhitespace = fWhitespaceAfter;
        }


    // ----- accessors ---------------------------------------------------------

    /**
     * Determine the starting position in the source at which this token occurs.
     *
     * @return the Source position of the token
     */
    public long getStartPosition()
        {
        return m_lStartPos;
        }

    /**
     * Determine the ending position (exclusive) in the source for this token.
     *
     * @return the Source position of the end of the token
     */
    public long getEndPosition()
        {
        return m_lEndPos;
        }

    /**
     * Determine if this token follows whitespace in the source.
     *
     * @return true iff this token follows whitespace
     */
    public boolean hasLeadingWhitespace()
        {
        return m_fLeadingWhitespace;
        }

    /**
     * Determine if this token precedes whitespace in the source.
     *
     * @return true iff this token is followed by whitespace
     */
    public boolean hasTrailingWhitespace()
        {
        return m_fTrailingWhitespace;
        }

    /**
     * Determine the identity of the token. A token identity specifies the type
     * of the token (e.g. operator, identifier) and
     *
     * @return the identity of the token
     */
    public Id getId()
        {
        return m_id;
        }

    /**
     * Determine the value of the token.
     *
     * @return the value of the token, or null
     */
    public Object getValue()
        {
        return m_oValue;
        }

    public Token convertToKeyword()
        {
        if (m_id != Id.IDENTIFIER)
            {
            throw new IllegalStateException("not an identifier! (" + toString() + ")");
            }

        Id id = Id.valueByContextSensitiveText((String) getValue());
        if (id == null)
            {
            throw new IllegalStateException("missing context sensitive keyword for: " + getValue());
            }

        return new Token(m_lStartPos, m_lEndPos, id);
        }

    /**
     * Allow a token to be "peeled off" the front of this token, if possible.
     *
     * @param id  the token to peel off of this token
     *
     * @return the new token
     */
    public Token peel(Id id, Source source)
        {
        if (id == Id.COMP_GT)
            {
            Id newId;
            switch (m_id)
                {
                default:
                    return null;

                case USHR_ASN:
                    newId = Id.SHR_ASN;
                    break;

                case USHR:
                    newId = Id.SHR;
                    break;

                case SHR_ASN:
                    newId = Id.COMP_GTEQ;
                    break;

                case SHR:
                    newId = Id.COMP_GT;
                    break;

                case COMP_GTEQ:
                    newId = Id.ASN;
                    break;
                }

            // get the location of "this" token
            long start  = m_lStartPos;

            // get the location of the end of the new peeled token / start of this token (adjusted)
            long current = source.getPosition();
            source.setPosition(start);
            source.next();
            long middle = source.getPosition();
            source.setPosition(current);

            // adjust this token
            m_lStartPos = middle;
            m_id        = newId;

            // return the new token
            return new Token(start, middle, id);
            }

        return null;
        }

    // ----- Object methods ----------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append('[')
          .append(Source.calculateLine(m_lStartPos))
          .append(",")
          .append(Source.calculateOffset(m_lStartPos))
          .append(" - ")
          .append(Source.calculateLine(m_lEndPos))
          .append(",")
          .append(Source.calculateOffset(m_lEndPos))
          .append("] ");
        
        switch (m_id)
            {
            case LIT_CHAR:
                {
                sb.append('\'');
                appendChar(sb, (Character) m_oValue);
                sb.append('\'');
                }
                break;

            case LIT_STRING:
                sb.append('\"');
                appendString(sb, (String) m_oValue);
                sb.append('\"');
                break;

            case LIT_INT:
            case LIT_DEC:
            case LIT_BIN:
                sb.append(m_oValue);
                break;

            case IDENTIFIER:
                sb.append("name:")
                  .append(m_oValue);
                break;

            case ENC_COMMENT:
                {
                String sComment = (String) m_oValue;
                if (sComment.length() > 47)
                    {
                    sComment = sComment.substring(0, 44) + "...";
                    }
                appendString(sb.append("/*"), sComment).append("*/");
                }
                break;

            case EOL_COMMENT:
                {
                String sComment = (String) m_oValue;
                if (sComment.length() > 50)
                    {
                    sComment = sComment.substring(0, 47) + "...";
                    }
                appendString(sb.append("//"), sComment);
                }
                break;

            default:
                sb.append(m_id.TEXT);
                break;
            }

        return sb.toString();
        }


    // ----- Token identities --------------------------------------------------

    /**
     * Token Identity. 
     */
    public enum Id
        {
        COLON       (":"            ),
        SEMICOLON   (";"            ),
        COMMA       (","            ),
        DOT         ("."            ),
        DOTDOT      (".."           ),
        ELLIPSIS    ("..."          ),
        AT          ("@"            ),
        COND        ("?"            ),
        COND_THEN   ("?."           ),
        COND_ELSE   ("?:"           ),
        L_PAREN     ("("            ),
        R_PAREN     (")"            ),
        L_CURLY     ("{"            ),
        R_CURLY     ("}"            ),
        L_SQUARE    ("["            ),
        R_SQUARE    ("]"            ),
        ADD         ("+"            ),
        SUB         ("-"            ),
        MUL         ("*"            ),
        DIV         ("/"            ),
        MOD         ("%"            ),
        DIVMOD      ("/%"           ),
        SHL         ("<<"           ),
        SHR         (">>"           ),
        USHR        (">>>"          ),
        BIT_AND     ("&"            ),
        BIT_OR      ("|"            ),
        BIT_XOR     ("^"            ),
        BIT_NOT     ("~"            ),
        ASN         ("="            ),
        ADD_ASN     ("+="           ),
        SUB_ASN     ("-="           ),
        MUL_ASN     ("*="           ),
        DIV_ASN     ("/="           ),
        MOD_ASN     ("%="           ),
        DIVMOD_ASN  ("/%="          ),
        SHL_ASN     ("<<="          ),
        SHR_ASN     (">>="          ),
        USHR_ASN    (">>>="         ),
        BIT_AND_ASN ("&="           ),
        BIT_OR_ASN  ("|="           ),
        BIT_XOR_ASN ("^="           ),
        COND_AND    ("&&"           ),
        COND_OR     ("||"           ),
        NOT         ("!"            ),
        COMP_EQ     ("=="           ),
        COMP_NEQ    ("!="           ),
        COMP_ORD    ("<=>"          ),
        COMP_LT     ("<"            ),
        COMP_LTEQ   ("<="           ),
        COMP_GT     (">"            ),
        COMP_GTEQ   (">="           ),
        INC         ("++"           ),
        DEC         ("--"           ),
        ALLOW       ("allow"        , true),
        AS          ("as"           , true),
        ASSERT      ("assert"       ),
        AVOID       ("avoid"        , true),
        BREAK       ("break"        ),
        CASE        ("case"         ),
        CATCH       ("catch"        ),
        CLASS       ("class"        ),
        CONDITIONAL ("conditional"  ),
        CONST       ("const"        ),
        CONSTRUCT   ("construct"    ),
        CONTINUE    ("continue"     ),
        DEFAULT     ("default"      ),
        DELEGATES   ("delegates"    ),
        DO          ("do"           ),
        ELSE        ("else"         ),
        ENUM        ("enum"         ),
        EXTENDS     ("extends"      ),
        FINALLY     ("finally"      ),
        FUNCTION    ("function"     ),
        IF          ("if"           ),
        IMMUTABLE   ("immutable"    ),
        IMPLEMENTS  ("implements"   ),
        IMPORT      ("import"       ),
        INCORPORATES("incorporates" ),
        INTERFACE   ("interface"    ),
        INTO        ("into"         , true),
        MIXIN       ("mixin"        ),
        MODULE      ("module"       ),
        PACKAGE     ("package"      ),
        PREFER      ("prefer"       , true),
        PRIVATE     ("private"      ),
        PROTECTED   ("protected"    ),
        PUBLIC      ("public"       ),
        RETURN      ("return"       ),
        SERVICE     ("service"      ),
        STATIC      ("static"       ),
        SUPER       ("super"        , true),
        SWITCH      ("switch"       ),
        THIS        ("this"         , true),
        THROW       ("throw"        ),
        TODO        ("TODO"         ),
        TRAIT       ("trait"        ),
        TRY         ("try"          ),
        TYPEDEF     ("typedef"      ),
        USING       ("using"        ),
        WHILE       ("while"        ),
        IDENTIFIER  (null           ),
        EOL_COMMENT (null           ),
        ENC_COMMENT (null           ),
        LIT_CHAR    (null           ),
        LIT_STRING  (null           ),
        LIT_INT     (null           ),
        LIT_DEC     (null           ),
        LIT_BIN     (null           );

        /**
         * Constructor.
         *
         * @param sText  a textual representation of the token, or null
         */
        Id(final String sText)
            {
            this(sText, false);
            }

        /**
         * Constructor.
         *
         * @param sText  a textual representation of the token, or null
         */
        Id(final String sText, boolean fContextSensitive)
            {
            TEXT = sText;
            this.ContextSensitive = fContextSensitive;
            }
        /**
         * Look up an Id enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Format enum for the specified ordinal
         */
        public static Id valueOf(int i)
            {
            return IDs[i];
            }

        /**
         * Look up an Id enum by its {@link #TEXT}.
         * 
         * @param sText  the textual representation of the Id
         *               
         * @return an instance of Id, or null if there is no matching
         *         {@link #TEXT}
         */
        public static Id valueByText(String sText)
            {
            return KEYWORDS.get(sText);
            }

        /**
         * Look up an Id enum by its {@link #TEXT}, including context-sensitive keywords.
         *
         * @param sText  the textual representation of the Id
         *
         * @return an instance of Id, or null if there is no matching
         *         {@link #TEXT}
         */
        public static Id valueByContextSensitiveText(String sText)
            {
            return ALL_KEYWORDS.get(sText);
            }

        /**
         * All of the Format enums.
         */
        private static final Id[] IDs = Id.values();

        /**
         * String representations of tokens that have constant representations, excluding context-
         * sensitive keywords.
         */
        private static final Map<String, Id> KEYWORDS = new HashMap<>();
        /**
         * String representations of all tokens that have constant representations.
         */
        private static final Map<String, Id> ALL_KEYWORDS = new HashMap<>();
        static
            {
            for (Id id : IDs)
                {
                String sText = id.TEXT;
                if (sText != null)
                    {
                    ALL_KEYWORDS.put(sText, id);
                    if (!id.ContextSensitive)
                        {
                        KEYWORDS.put(sText, id);
                        }
                    }
                }
            }

        /**
         * A textual representation of the token, if it has a constant
         * textual representation; otherwise null.
         */
        final public String TEXT;

        /**
         * True if the token is context-sensitive, i.e. if it is not always a
         * reserved word.
         */
        final boolean ContextSensitive;
        }


    // ----- data members ------------------------------------------------------

    /**
     * Starting position (inclusive) in the source of this token.
     */
    private long m_lStartPos;

    /**
     * Ending position (exclusive) in the source of this token.
     */
    private long m_lEndPos;

    /**
     * Identifier of the token.
     */
    private Id m_id;

    /**
     * Value of the Token (if it is a literal).
     */
    private Object m_oValue;

    /**
     * Each token konws if it follows whitespace.
     */
    private boolean m_fLeadingWhitespace;

    /**
     * Each token konws if it has whitespace following.
     */
    private boolean m_fTrailingWhitespace;
    }

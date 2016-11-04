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
        this.id = id;
        m_oValue    = oValue;
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
     * Determine the identity of the token. A token identity specifies the type
     * of the token (e.g. operator, identifier) and
     *
     * @return the identity of the token
     */
    public Id getId()
        {
        return id;
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
        
        switch (id)
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
                sb.append(m_oValue);
                break;

            case IDENTIFIER:
                sb.append("name:")
                  .append(m_oValue);
                break;

            default:
                sb.append(id.TEXT);
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
        COLON      (":"        ),
        SEMICOLON  (";"        ),
        COMMA      (","        ),
        DOT        ("."        ),
        DOTDOT     (".."       ),
        AT         ("@"        ),
        COND       ("?"        ),
        L_PAREN    ("("        ),
        R_PAREN    (")"        ),
        L_CURLY    ("{"        ),
        R_CURLY    ("}"        ),
        L_SQUARE   ("["        ),
        R_SQUARE   ("]"        ),
        ADD        ("+"        ),
        SUB        ("-"        ),
        MUL        ("*"        ),
        DIV        ("/"        ),
        MOD        ("%"        ),
        DIVMOD     ("/%"       ),
        SHL        ("<<"       ),
        SHR        (">>"       ),
        USHR       (">>>"      ),
        BIT_AND    ("&"        ),
        BIT_OR     ("|"        ),
        BIT_XOR    ("^"        ),
        BIT_NOT    ("~"        ),
        MOV        ("="        ),
        ADD_MOV    ("+="       ),
        SUB_MOV    ("-="       ),
        MUL_MOV    ("*="       ),
        DIV_MOV    ("/="       ),
        MOD_MOV    ("%="       ),
        DIVMOD_MOV ("/%="      ),
        SHL_MOV    ("<<="      ),
        SHR_MOV    (">>="      ),
        USHR_MOV   (">>>="     ),
        BIT_AND_MOV("&="       ),
        BIT_OR_MOV ("|="       ),
        BIT_XOR_MOV("^="       ),
        COND_AND   ("&&"       ),
        COND_OR    ("||"       ),
        NOT        ("!"        ),
        COMP_EQ    ("=="       ),
        COMP_NEQ   ("!="       ),
        COMP_LT    ("<"        ),
        COMP_LTEQ  ("<="       ),
        COMP_GT    (">"        ),
        COMP_GTEQ  (">="       ),
        INC        ("++"       ),
        DEC        ("--"       ),
        MODULE     ("module"   ),
        PACKAGE    ("package"  ),
        CLASS      ("class"    ),
        IMPORT     ("import"   ),
        PUBLIC     ("public"   ),
        PRIVATE    ("private"  ),
        PROTECTED  ("protected"),
        THIS       ("this"     ),
        SUPER      ("super"    ),
        TRY        ("try"      ),
        CATCH      ("catch"    ),
        THROW      ("throw"    ),
        IF         ("if"       ),
        ELSE       ("else"     ),
        DO         ("do"       ),
        WHILE      ("while"    ),
        SWITCH     ("switch"   ),
        CASE       ("case"     ),
        DEFAULT    ("default"  ),
        BREAK      ("break"    ),
        CONTINUE   ("continue" ),
        RETURN     ("return"   ),
        IDENTIFIER (null       ),
        EOL_COMMENT(null       ),
        ENC_COMMENT(null       ),
        LIT_CHAR   (null       ),
        LIT_STRING (null       ),
        LIT_INT    (null       ),
        LIT_DEC    (null       );

        /**
         * Constructor.
         *
         * @param sText  a textual representation of the token, or null
         */
        Id(final String sText)
            {
            TEXT = sText;
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
         * 
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
         * All of the Format enums.
         */
        private static final Id[] IDs = Id.values();

        /**
         * String representations of tokens that have constant representations.
         */
        private static final Map<String, Id> KEYWORDS = new HashMap<>();
        static
            {
            for (Id id : IDs)
                {
                String sText = id.TEXT;
                if (sText != null)
                    {
                    KEYWORDS.put(sText, id);
                    }
                }
            }

        /**
         * A textual representation of the token, if it has a constant
         * textual representation; otherwise null.
         */
        final public String TEXT;
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
    private Id id;

    /**
     * Value of the Token (if it is a literal).
     */
    private Object m_oValue;
    }

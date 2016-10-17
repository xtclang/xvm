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
     * @param nId        identity of the token
     */
    public Token(long lStartPos, long lEndPos, int nId)
        {
        this(lStartPos, lEndPos, nId, null);
        }

    /**
     * Construct an XTC token.
     *
     * @param lStartPos  starting position in the Source (inclusive)
     * @param lEndPos    ending position in the Source (exclusive)
     * @param nId        identity of the token
     * @param oValue     value of the token (if it is a literal)
     */
    public Token(long lStartPos, long lEndPos, int nId, Object oValue)
        {
        m_lStartPos = lStartPos;
        m_lEndPos   = lEndPos;
        m_nId       = nId;
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
    public int getId()
        {
        return m_nId;
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
        
        switch (m_nId)
            {
            case ID_LIT_CHAR:
                {
                sb.append('\'');
                appendChar(sb, (Character) m_oValue);
                sb.append('\'');
                }
                break;

            case ID_LIT_STRING:
                sb.append('\"');
                appendString(sb, (String) m_oValue);
                sb.append('\"');
                break;

            case ID_LIT_INT:
            case ID_LIT_DEC:
                sb.append(m_oValue);
                break;

            case ID_IDENTIFIER:
                sb.append("name:")
                  .append(m_oValue);
                break;

            default:
                sb.append(DESC[m_nId]);
                break;
            }

        return sb.toString();
        }


    // ----- helpers -----------------------------------------------------------

    /**
     * For a given token ID, obtain the textual description of that token. This
     * applies only to those token IDs that correspond to predefined operators,
     * separators and keywords, i.e. those tokens that have constant
     * representations.
     *
     * @param nId  a token ID
     *
     * @return the textual form of the token as it would be expected to occur
     *         within XTC source code
     */
    public static String getDescription(int nId)
        {
        if (nId < 0 || nId >= DESC.length)
            {
            throw new IllegalArgumentException("token id out of range: " + nId);
            }

        return DESC[nId];
        }

    /**
     * Determine if the specified String is an XTC keyword.
     *
     * @param s  a String
     *
     * @return true iff the specified String is a keyword
     */
    public static boolean isKeyword(String s)
        {
        return KEYWORDS.containsKey(s);
        }

    /**
     * Determine the token ID of an XTC keyword.
     *
     * @param s  a keyword
     *
     * @return the token ID for the keyword
     */
    public static int getKeywordId(String s)
        {
        Integer id = KEYWORDS.get(s);
        if (id == null)
            {
            throw new IllegalArgumentException("not a keyword: " + s);
            }
        return id;
        }


    // ----- constants ---------------------------------------------------------

    public static final int ID_COLON        =  0;
    public static final int ID_SEMICOLON    =  1;
    public static final int ID_COMMA        =  2;
    public static final int ID_DOT          =  3;
    public static final int ID_DOTDOT       =  4;
    public static final int ID_AT           =  5;
    public static final int ID_COND         =  6;
    public static final int ID_L_PAREN      =  7;
    public static final int ID_R_PAREN      =  8;
    public static final int ID_L_CURLY      =  9;
    public static final int ID_R_CURLY      = 10;
    public static final int ID_L_SQUARE     = 11;
    public static final int ID_R_SQUARE     = 12;
    public static final int ID_ADD          = 13;
    public static final int ID_SUB          = 14;
    public static final int ID_MUL          = 15;
    public static final int ID_DIV          = 16;
    public static final int ID_MOD          = 17;
    public static final int ID_DIVMOD       = 18;
    public static final int ID_SHL          = 19;
    public static final int ID_SHR          = 20;
    public static final int ID_USHR         = 21;
    public static final int ID_BIT_AND      = 22;
    public static final int ID_BIT_OR       = 23;
    public static final int ID_BIT_XOR      = 24;
    public static final int ID_BIT_NOT      = 25;
    public static final int ID_MOV          = 26;
    public static final int ID_ADD_MOV      = 27;
    public static final int ID_SUB_MOV      = 28;
    public static final int ID_MUL_MOV      = 29;
    public static final int ID_DIV_MOV      = 30;
    public static final int ID_MOD_MOV      = 31;
    public static final int ID_DIVMOD_MOV   = 32;
    public static final int ID_SHL_MOV      = 33;
    public static final int ID_SHR_MOV      = 34;
    public static final int ID_USHR_MOV     = 35;
    public static final int ID_BIT_AND_MOV  = 36;
    public static final int ID_BIT_OR_MOV   = 37;
    public static final int ID_BIT_XOR_MOV  = 38;
    public static final int ID_COND_AND     = 39;
    public static final int ID_COND_OR      = 40;
    public static final int ID_NOT          = 41;
    public static final int ID_COMP_EQ      = 42;
    public static final int ID_COMP_NEQ     = 43;
    public static final int ID_COMP_LT      = 44;
    public static final int ID_COMP_LTEQ    = 45;
    public static final int ID_COMP_GT      = 46;
    public static final int ID_COMP_GTEQ    = 47;
    public static final int ID_INC          = 48;
    public static final int ID_DEC          = 49;
    public static final int ID_MODULE       = 50;
    public static final int ID_PACKAGE      = 51;
    public static final int ID_CLASS        = 52;
    public static final int ID_PUBLIC       = 53;
    public static final int ID_PRIVATE      = 54;
    public static final int ID_PROTECTED    = 55;
    public static final int ID_THIS         = 56;
    public static final int ID_SUPER        = 57;
    public static final int ID_TRY          = 58;
    public static final int ID_CATCH        = 59;
    public static final int ID_THROW        = 60;
    public static final int ID_IF           = 61;
    public static final int ID_ELSE         = 62;
    public static final int ID_DO           = 63;
    public static final int ID_WHILE        = 64;
    public static final int ID_SWITCH       = 65;
    public static final int ID_CASE         = 66;
    public static final int ID_DEFAULT      = 67;
    public static final int ID_BREAK        = 68;
    public static final int ID_CONTINUE     = 69;
    public static final int ID_RETURN       = 70;
    public static final int ID_IDENTIFIER   = 71;
    public static final int ID_EOL_COMMENT  = 72;
    public static final int ID_ENC_COMMENT  = 73;
    public static final int ID_LIT_CHAR     = 74;
    public static final int ID_LIT_STRING   = 75;
    public static final int ID_LIT_INT      = 76;
    public static final int ID_LIT_DEC      = 77;

    /**
     * String representations of tokens that have constant representations.
     */
    private static final String[]             DESC     = new String[71];
    private static final Map<String, Integer> KEYWORDS = new HashMap<>();
    static
        {
        DESC[ID_COLON      ] = ":";
        DESC[ID_SEMICOLON  ] = ";";
        DESC[ID_COMMA      ] = ",";
        DESC[ID_DOT        ] = ".";
        DESC[ID_DOTDOT     ] = "..";
        DESC[ID_AT         ] = "@";
        DESC[ID_COND       ] = "?";
        DESC[ID_L_PAREN    ] = "(";
        DESC[ID_R_PAREN    ] = ")";
        DESC[ID_L_CURLY    ] = "{";
        DESC[ID_R_CURLY    ] = "}";
        DESC[ID_L_SQUARE   ] = "[";
        DESC[ID_R_SQUARE   ] = "]";
        DESC[ID_ADD        ] = "+";
        DESC[ID_SUB        ] = "-";
        DESC[ID_MUL        ] = "*";
        DESC[ID_DIV        ] = "/";
        DESC[ID_MOD        ] = "%";
        DESC[ID_DIVMOD     ] = "/%";
        DESC[ID_SHL        ] = "<<";
        DESC[ID_SHR        ] = ">>";
        DESC[ID_USHR       ] = ">>>";
        DESC[ID_BIT_AND    ] = "&";
        DESC[ID_BIT_OR     ] = "|";
        DESC[ID_BIT_XOR    ] = "^";
        DESC[ID_BIT_NOT    ] = "~";
        DESC[ID_MOV        ] = "=";
        DESC[ID_ADD_MOV    ] = "+=";
        DESC[ID_SUB_MOV    ] = "-=";
        DESC[ID_MUL_MOV    ] = "*=";
        DESC[ID_DIV_MOV    ] = "/=";
        DESC[ID_MOD_MOV    ] = "%=";
        DESC[ID_DIVMOD_MOV ] = "/%=";
        DESC[ID_SHL_MOV    ] = "<<=";
        DESC[ID_SHR_MOV    ] = ">>=";
        DESC[ID_USHR_MOV   ] = ">>>=";
        DESC[ID_BIT_AND_MOV] = "&=";
        DESC[ID_BIT_OR_MOV ] = "|=";
        DESC[ID_BIT_XOR_MOV] = "^=";
        DESC[ID_COND_AND   ] = "&&";
        DESC[ID_COND_OR    ] = "||";
        DESC[ID_NOT        ] = "!";
        DESC[ID_COMP_EQ    ] = "==";
        DESC[ID_COMP_NEQ   ] = "!=";
        DESC[ID_COMP_LT    ] = "<";
        DESC[ID_COMP_LTEQ  ] = "<=";
        DESC[ID_COMP_GT    ] = ">";
        DESC[ID_COMP_GTEQ  ] = ">=";
        DESC[ID_INC        ] = "++";
        DESC[ID_DEC        ] = "--";
        DESC[ID_MODULE     ] = "module";
        DESC[ID_PACKAGE    ] = "package";
        DESC[ID_CLASS      ] = "class";
        DESC[ID_PUBLIC     ] = "public";
        DESC[ID_PRIVATE    ] = "private";
        DESC[ID_PROTECTED  ] = "protected";
        DESC[ID_THIS       ] = "this";
        DESC[ID_SUPER      ] = "super";
        DESC[ID_TRY        ] = "try";
        DESC[ID_CATCH      ] = "catch";
        DESC[ID_THROW      ] = "throw";
        DESC[ID_IF         ] = "if";
        DESC[ID_ELSE       ] = "else";
        DESC[ID_DO         ] = "do";
        DESC[ID_WHILE      ] = "while";
        DESC[ID_SWITCH     ] = "switch";
        DESC[ID_CASE       ] = "case";
        DESC[ID_DEFAULT    ] = "default";
        DESC[ID_BREAK      ] = "break";
        DESC[ID_CONTINUE   ] = "continue";
        DESC[ID_RETURN     ] = "return";

        for (int i = ID_MODULE; i <= ID_RETURN; ++i)
            {
            KEYWORDS.put(DESC[i], i);
            }
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
    private int m_nId;

    /**
     * Value of the Token (if it is a literal).
     */
    private Object m_oValue;
    }

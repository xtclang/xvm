// Copyright 2000-2022 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.xtclang.sdk.language;


import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.xtclang.sdk.language.lexer.EcstasyLexerAdapter;
import org.xvm.compiler.Token;


import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class EcstasySyntaxHighlighter extends SyntaxHighlighterBase
    {
    public static final TextAttributesKey SEPARATOR =
        createTextAttributesKey("ECSTASY_SEPARATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey KEY =
        createTextAttributesKey("ECSTASY_KEY", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey VALUE =
        createTextAttributesKey("ECSTASY_VALUE", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey COMMENT =
        createTextAttributesKey("ECSTASY_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey BAD_CHARACTER =
        createTextAttributesKey("ECSTASY_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);


    private static final TextAttributesKey[] BAD_CHAR_KEYS = new TextAttributesKey[]{BAD_CHARACTER};
    private static final TextAttributesKey[] SEPARATOR_KEYS = new TextAttributesKey[]{SEPARATOR};
    private static final TextAttributesKey[] KEY_KEYS = new TextAttributesKey[]{KEY};
    private static final TextAttributesKey[] VALUE_KEYS = new TextAttributesKey[]{VALUE};
    private static final TextAttributesKey[] COMMENT_KEYS = new TextAttributesKey[]{COMMENT};
    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

    @NotNull
    @Override
    public Lexer getHighlightingLexer()
        {
        return new EcstasyLexerAdapter();
        }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType)
        {
        final TextAttributesKey textAttributesKey;

        try
            {
            Token.Id idValue = Token.Id.valueOf(tokenType.getDebugName());
            textAttributesKey = switch (idValue)
                {
                case EOL_COMMENT, TODO: // TODO: fix TODOs
                    yield DefaultLanguageHighlighterColors.LINE_COMMENT;
                case ENC_COMMENT:
                    yield DefaultLanguageHighlighterColors.BLOCK_COMMENT;
                case SEMICOLON:
                    yield DefaultLanguageHighlighterColors.SEMICOLON;
                case COMMA:
                    yield DefaultLanguageHighlighterColors.COMMA;
                case DOT:
                    yield DefaultLanguageHighlighterColors.DOT;
                case COLON, TEMPLATE:
                    // TODO: Not sure about these
                    yield HighlighterColors.NO_HIGHLIGHTING;
                case IDENTIFIER:
                    yield DefaultLanguageHighlighterColors.IDENTIFIER;
                case DIR_CUR, DIR_PARENT, ENUM_VAL:
                    yield DefaultLanguageHighlighterColors.CONSTANT;
                case I_RANGE_I, E_RANGE_I, I_RANGE_E, E_RANGE_E, ADD, SUB, MUL, DIV, MOD, DIVREM, SHL, SHR, USHR, BIT_AND, BIT_OR, BIT_XOR, BIT_NOT, NOT, COND_AND, COND_XOR, COND_OR, COND_ELSE, ASN, ADD_ASN, SUB_ASN, MUL_ASN, DIV_ASN, MOD_ASN, SHL_ASN, SHR_ASN, USHR_ASN, BIT_AND_ASN, BIT_OR_ASN, BIT_XOR_ASN, COND_AND_ASN, COND_OR_ASN, COND_ASN, COND_NN_ASN, COND_ELSE_ASN, COMP_EQ, COMP_NEQ, COMP_ORD, COMP_LT, COMP_LTEQ, COMP_GT, COMP_GTEQ, INC, DEC:
                    yield DefaultLanguageHighlighterColors.OPERATION_SIGN;
                case LIT_CHAR, LIT_STRING, LIT_BINSTR, STR_FILE, BIN_FILE, LIT_PATH: // TODO: can they highlight interpolated strings?
                    yield DefaultLanguageHighlighterColors.STRING;
                case AT:
                    yield DefaultLanguageHighlighterColors.METADATA;
                case LIT_BIT, LIT_NIBBLE, LIT_INT, LIT_INT8, LIT_INT16, LIT_INT32, LIT_INT64, LIT_INT128, LIT_INTN, LIT_UINT8, LIT_UINT16, LIT_UINT32, LIT_UINT64, LIT_UINT128, LIT_UINTN, LIT_DEC, LIT_DEC32, LIT_DEC64, LIT_DEC128, LIT_DECN, LIT_FLOAT, LIT_FLOAT16, LIT_FLOAT32, LIT_FLOAT64, LIT_FLOAT128, LIT_FLOATN, LIT_BFLOAT16, LIT_DATE, LIT_TIMEOFDAY, LIT_TIME, LIT_TIMEZONE, LIT_DURATION, LIT_VERSION:
                    yield DefaultLanguageHighlighterColors.NUMBER;
                case L_PAREN, ASYNC_PAREN, R_PAREN:
                    yield DefaultLanguageHighlighterColors.PARENTHESES;
                case L_CURLY, R_CURLY:
                    yield DefaultLanguageHighlighterColors.BRACES;
                case L_SQUARE, R_SQUARE:
                    yield DefaultLanguageHighlighterColors.BRACKETS;
                case COND, LAMBDA, ASN_EXPR, ANY, ALLOW, AS, ASSERT, ASSERT_RND, ASSERT_ARG, ASSERT_BOUNDS, ASSERT_TODO, ASSERT_ONCE, ASSERT_TEST, ASSERT_DBG, AVOID, BREAK, CASE, CATCH, CLASS, CONDITIONAL, CONST, CONSTRUCT, CONTINUE, DEFAULT, DELEGATES, DO, ELSE, ENUM, EXTENDS, FINALLY, FOR, FUNCTION, IF, IMMUTABLE, IMPLEMENTS, IMPORT, IMPORT_EMBED, IMPORT_REQ, IMPORT_WANT, IMPORT_OPT, INCORPORATES, INTERFACE, INTO, IS, MIXIN, MODULE, NEW, OUTER, PACKAGE, PREFER, PRIVATE, PROTECTED, PUBLIC, RETURN, SERVICE, STATIC, STRUCT, SUPER, SWITCH, THIS, THIS_CLASS, THIS_MODULE, THIS_PRI, THIS_PRO, THIS_PUB, THIS_SERV, THIS_STRUCT, THIS_TARGET, THROW, TRY, TYPEDEF, USING, VAL, VAR, VOID, WHILE:
                    yield DefaultLanguageHighlighterColors.KEYWORD;
                };
            }
        catch (IllegalArgumentException e)
            {
            return new TextAttributesKey[]{HighlighterColors.NO_HIGHLIGHTING};
            }
        TextAttributesKey key = createTextAttributesKey(String.format("ECSTASY_%s", textAttributesKey.getExternalName()), textAttributesKey);
        return new TextAttributesKey[]{key};
        }
    }

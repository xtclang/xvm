package org.xvm.compiler.ast;


import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;


/**
 * A keyword type expression is a type expression that is composed of a keyword that identifies an
 * entire type (or composition) category.
 */
public class KeywordTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public KeywordTypeExpression(Token keyword)
        {
        this.keyword  = keyword;

        this.m_format = switch (keyword.getId())
            {
            case CONST   -> Format.IsConst;
            case ENUM    -> Format.IsEnum;
            case MODULE  -> Format.IsModule;
            case PACKAGE -> Format.IsPackage;
            case SERVICE -> Format.IsService;
            case CLASS   -> Format.IsClass;
            default      -> throw new IllegalStateException("keyword=" + keyword);
            };
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return keyword.getEndPosition();
        }


    // ----- TypeExpression methods ----------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx, ErrorListener errs)
        {
        ConstantPool pool = pool();

        return pool.ensureTerminalTypeConstant(pool.ensureKeywordConstant(m_format));
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return keyword.getId().TEXT;
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The parsed keyword indicating a type category.
     */
    protected Token keyword;

    /**
     * The constant format corresponding to the keyword.
     */
    protected Format m_format;
    }

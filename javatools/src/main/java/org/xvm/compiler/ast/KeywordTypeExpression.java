package org.xvm.compiler.ast;


import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


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

        if (keyword.getId() == Token.Id.ANY)
            {
            log(errs, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED, keyword.getValueText());
            return pool.typeObject(); // TODO need a poison/error type that is known by the compiler
            }

        return switch (keyword.getId())
            {
            case IMMUTABLE -> pool.ensureImmutableTypeConstant(pool.typeObject());
            case SERVICE   -> pool.ensureServiceTypeConstant(pool.typeObject());
            case CONST     -> pool.ensureTerminalTypeConstant(pool.ensureKeywordConstant(Format.IsConst));
            case ENUM      -> pool.ensureTerminalTypeConstant(pool.ensureKeywordConstant(Format.IsEnum));
            case MODULE    -> pool.ensureTerminalTypeConstant(pool.ensureKeywordConstant(Format.IsModule));
            case PACKAGE   -> pool.ensureTerminalTypeConstant(pool.ensureKeywordConstant(Format.IsPackage));
            case CLASS     -> pool.ensureTerminalTypeConstant(pool.ensureKeywordConstant(Format.IsClass));
            default        -> throw new IllegalStateException("keyword=" + keyword);
            };
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
    }

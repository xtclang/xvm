package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;


/**
 * A decorated type expression is a type expression preceded by a keyword that adjusts the meaning
 * of the type expression.
 */
public class DecoratedTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public DecoratedTypeExpression(Token keyword, TypeExpression type)
        {
        this.keyword = keyword;
        this.type    = type;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected boolean canResolveNames()
        {
        return super.canResolveNames() || type.canResolveNames();
        }

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return type.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant()
        {
        switch (keyword.getId())
            {
            case IMMUTABLE:
                return pool().ensureImmutableTypeConstant(type.ensureTypeConstant());

            case CONDITIONAL:
                // TODO
                throw new UnsupportedOperationException();

            default:
                throw new IllegalStateException("keyword=" + keyword);
            }
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public void resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        if (getStage().ordinal() < org.xvm.compiler.Compiler.Stage.Resolved.ordinal())
            {
            // resolve the sub-type
            type.resolveNames(listRevisit, errs);

            // store off a type constant for this type expression
            ensureTypeConstant();

            super.resolveNames(listRevisit, errs);
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT)
          .append(' ')
          .append(type);

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token          keyword;
    protected TypeExpression type;

    private static final Field[] CHILD_FIELDS = fieldsForNames(DecoratedTypeExpression.class, "type");
    }

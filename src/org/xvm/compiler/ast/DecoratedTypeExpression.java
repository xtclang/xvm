package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Format;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;


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

    @Override
    public boolean isIntroductoryType()
        {
        return true;
        }

    @Override
    public TypeExpression unwrapIntroductoryType()
        {
        return type;
        }

    @Override
    public void replaceIntroducedType(TypeExpression type)
        {
        this.type = type;
        type.setParent(this);
        }


    // ----- inner class compilation support -------------------------------------------------------

    @Override
    public Format getInnerClassFormat()
        {
        Format format = type.getInnerClassFormat();
        if (format == null)
            {
            return null;
            }

        switch (format)
            {
            case INTERFACE:
            case CLASS:
            case CONST:
                return keyword.getId() == Id.IMMUTABLE ? Format.CONST : format;

            case SERVICE:
                // a service cannot be immutable
                return keyword.getId() == Id.IMMUTABLE ? null : format;

            default:
                return null;
            }
        }

    @Override
    public String getInnerClassName()
        {
        return type.getInnerClassName();
        }

    @Override
    public TypeExpression collectContributions(List<Annotation> listAnnos, List<Contribution> listContribs)
        {
        TypeExpression typeNew = type.collectContributions(listAnnos, listContribs);
        return type == typeNew
                ? this
                : new DecoratedTypeExpression(keyword, typeNew);
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

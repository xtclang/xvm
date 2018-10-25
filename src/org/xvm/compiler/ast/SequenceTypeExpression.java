package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;


/**
 * An sequence type expression is a type expression followed by an ellipsis.
 */
public class SequenceTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public SequenceTypeExpression(TypeExpression type, Token tokDots)
        {
        this(type, tokDots.getEndPosition());
        }

    private SequenceTypeExpression(TypeExpression type, long lEndPos)
        {
        this.type    = type;
        this.lEndPos = lEndPos;
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
        return type.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
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
        // build an Array type
        ConstantPool pool = pool();
        return pool.ensureClassTypeConstant(pool.clzSequence(), null, type.ensureTypeConstant());
        }


    // ----- inner class compilation support -------------------------------------------------------

    @Override
    public Format getInnerClassFormat()
        {
        Format format = type.getInnerClassFormat();
        return format == null ? null : Format.CLASS;
        }

    @Override
    public String getInnerClassName()
        {
        String sName = type.getInnerClassName();
        return sName == null
                ? null
                : sName + "...";
        }

    @Override
    public TypeExpression collectAnnotations(List<Annotation> annotations)
        {
        TypeExpression typeNew = type.collectAnnotations(annotations);
        return type == typeNew
                ? this
                : new SequenceTypeExpression(type, lEndPos);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append("...");

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression type;
    protected long           lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SequenceTypeExpression.class, "type");
    }

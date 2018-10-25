package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;


/**
 * An array type expression is a type expression followed by an array indicator. Because an array
 * type can be used to (e.g.) "new" an array, it also has to support actual index extents, in
 * addition to just supporting the number of dimensions.
 */
public class ArrayTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ArrayTypeExpression(TypeExpression type, int dims, long lEndPos)
        {
        this(type, dims, null, lEndPos);
        }

    public ArrayTypeExpression(TypeExpression type, List<Expression> indexes, long lEndPos)
        {
        this(type, indexes.size(), indexes, lEndPos);
        }

    private ArrayTypeExpression(TypeExpression type, int dims, List<Expression> indexes, long lEndPos)
        {
        this.type    = type;
        this.dims    = dims;
        this.indexes = indexes;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the number of array dimensions, 0 or more
     */
    public int getDimensions()
        {
        return dims;
        }

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
        final ConstantPool pool = pool();
        return pool.ensureClassTypeConstant(pool.clzArray(), null, type.ensureTypeConstant());
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
        if (sName == null)
            {
            return null;
            }

        if (dims <= 1)
            {
            return sName + "[]";
            }

        StringBuilder sb = new StringBuilder("[?");
        for (int i = 2; i <= dims; ++i)
            {
            sb.append(",?");
            }
        return sb.append("]").toString();
        }

    @Override
    public TypeExpression collectAnnotations(List<Annotation> annotations)
        {
        TypeExpression typeNew = type.collectAnnotations(annotations);
        return type == typeNew
                ? this
                : new ArrayTypeExpression(typeNew, dims, indexes, lEndPos);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append('[');

        for (int i = 0; i < dims; ++i)
            {
            if (i > 0)
                {
                sb.append(',');
                }

            if (indexes == null)
                {
                sb.append('?');
                }
            else
                {
                sb.append(indexes.get(i));
                }
            }

          sb.append(']');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression   type;
    protected int              dims;
    protected List<Expression> indexes;
    protected long             lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ArrayTypeExpression.class, "type", "indexes");
    }

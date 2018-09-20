package org.xvm.asm.constants;

import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;

/**
 * Transient pseudo type constant that represents the content of a Tuple type.
 *
 * This TypeConstant is *never* registered with the ConstantPool and is intended to be used only by
 * {@link GenericTypeResolver} implementations to indicate a type resolution "extension".
 */
public class TupleElementsTypeConstant
        extends TypeConstant
    {
    /**
     * Construct a type constant representing an array of specified elements.
     */
    public TupleElementsTypeConstant(ConstantPool pool, TypeConstant[] atypeElements)
        {
        super(pool);

        m_atypeElements = atypeElements;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------


    @Override
    public boolean containsUnresolved()
        {
        for (TypeConstant type : m_atypeElements)
            {
            if (type.containsUnresolved())
                {
                return true;
                }
            }

        return false;
        }

    @Override
    public TypeConstant[] getParamTypesArray()
        {
        return m_atypeElements;
        }

    @Override
    public boolean isModifyingType()
        {
        throw new IllegalStateException();
        }

    @Override
    public Format getFormat()
        {
        throw new IllegalStateException();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        // see equals()
        return -1;
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        throw new IllegalStateException();
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        throw new IllegalStateException();
        }

    @Override
    public boolean equals(Object that)
        {
        return that instanceof TupleElementsTypeConstant
            && Arrays.equals(m_atypeElements, ((TupleElementsTypeConstant) that).m_atypeElements);
        }

    @Override
    public int hashCode()
        {
        return 0;
        }

    @Override
    public String toString()
        {
        return getValueString();
        }

    @Override
    public String getValueString()
        {
        StringBuilder sb = new StringBuilder("TupleElements:<");

        boolean first = true;
        for (TypeConstant type : m_atypeElements)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(type.getValueString());
            }

        sb.append('>');

        return sb.toString();
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The underlying Tuple type.
     */
    private TypeConstant[] m_atypeElements;
    }

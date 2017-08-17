package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.List;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the type of a module, package, or class.
 *
 * @author cp 2017.04.25
 */
public class AccessTypeConstant
        extends TypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public AccessTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iType  = readIndex(in);
        m_access = Access.valueOf(readIndex(in));
        }

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param constType  a ModuleConstant, PackageConstant, or ClassConstant
     * @param access     one of: Public, Protected, Private, or Struct
     */
    public AccessTypeConstant(ConstantPool pool, TypeConstant constType, Access access)
        {
        super(pool);

        if (constType == null)
            {
            throw new IllegalArgumentException("type is required");
            }
        if (access == null)
            {
            throw new IllegalArgumentException("access is required");
            }
        if (constType.isAccessSpecified())
            {
            throw new IllegalArgumentException("type access is already specified");
            }

        m_constType = constType;
        m_access    = access;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    @Override
    public boolean isImmutabilitySpecified()
        {
        return m_constType.isImmutabilitySpecified();
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return m_constType.isAutoNarrowing();
        }

    @Override
    public boolean isAccessSpecified()
        {
        return true;
        }

    @Override
    public Access getAccess()
        {
        return m_access;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.TerminalType;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constType);
        for (Constant param : m_listParams)
            {
            visitor.accept(param);
            }
        }

    @Override
    protected Object getLocator()
        {
        return m_access == Access.PUBLIC && m_listParams.isEmpty()
                ? m_constType
                : null;
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        AccessTypeConstant that = (AccessTypeConstant) obj;
        int n = this.m_constType.compareTo(that.m_constType);
        if (n == 0)
            {
            n = this.m_access.compareTo(that.m_access);
            }

        if (n == 0)
            {
            List<TypeConstant> listThis = this.m_listParams;
            List<TypeConstant> listThat = that.m_listParams;
            for (int i = 0, c = Math.min(listThis.size(), listThat.size()); i < c; ++i)
                {
                n = listThis.get(i).compareTo(listThat.get(i));
                if (n != 0)
                    {
                    return n;
                    }
                }
            n = listThis.size() - listThat.size();
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(m_constType.getValueString());

        if (!m_listParams.isEmpty())
            {
            sb.append('<');

            boolean first = true;
            for (TypeConstant type : m_listParams)
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
            }

        if (m_access != Access.PUBLIC)
            {
            sb.append(':').append(m_access.KEYWORD);
            }

        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, indexOf(m_constType));
        writePackedLong(out, m_access.ordinal());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constType.hashCode() + m_access.ordinal();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the underlying TypeConstant.
     */
    private transient int m_iType;

    /**
     * The underlying TypeConstant.
     */
    private TypeConstant m_constType;

    /**
     * The public/protected/private/struct modifier for the type referred to.
     */
    private Access m_access;
    }

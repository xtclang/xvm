package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.runtime.TypeSet;

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
        if (!constType.isSingleDefiningConstant())
            {
            throw new IllegalArgumentException("access cannot be specified for a relational type");
            }

        m_constType = constType;
        m_access    = access;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isModifyingType()
        {
        return true;
        }

    @Override
    public TypeConstant getUnderlyingType()
        {
        return m_constType;
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

    @Override
    public TypeConstant resolveTypedefs()
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constResolved = constOriginal.resolveTypedefs();
        return constResolved == constOriginal
                ? this
                : getConstantPool().ensureAccessTypeConstant(constResolved, m_access);
        }

    /**
     * Determine if this type consumes a formal type with the specified name in context
     * of the given TypeComposition and access policy.
     */
    public boolean consumesFormalType(String sTypeName, TypeSet types, Access access)
        {
        return getUnderlyingType().consumesFormalType(sTypeName, types, m_access);
        }

    /**
     * Determine if this type produces a formal type with the specified name in context
     * of the given TypeComposition and access policy..
     */
    public boolean producesFormalType(String sTypeName, TypeSet types, Access access)
        {
        return getUnderlyingType().producesFormalType(sTypeName, types, m_access);
        }

    @Override
    public boolean isNullable()
        {
        assert !m_constType.isNullable();
        return false;
        }

    @Override
    protected TypeConstant unwrapForCongruence()
        {
        return m_constType.unwrapForCongruence();
        }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.AccessType;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_constType.containsUnresolved();
        }

    @Override
    public Constant simplify()
        {
        m_constType = (TypeConstant) m_constType.simplify();
        return this;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constType);
        }

    @Override
    protected Object getLocator()
        {
        return m_access == Access.PUBLIC
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
        return n;
        }

    @Override
    public String getValueString()
        {
        return new StringBuilder()
                .append(m_constType.getValueString())
                .append(':')
                .append(m_access.KEYWORD)
                .toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constType = (TypeConstant) getConstantPool().getConstant(m_iType);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constType = (TypeConstant) pool.register(m_constType);
        }

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

package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.xvm.asm.ComponentResolver.ResolutionCollector;
import org.xvm.asm.ComponentResolver.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.util.Hash;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents an accessibility constraint for an underlying type that
 * represents module, package, or class.
 */
public class AccessTypeConstant
        extends TypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is an access specified type.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param constType  a type constant that represents a module, package, or class
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

    @Override
    protected void resolveConstants()
        {
        m_constType = (TypeConstant) getConstantPool().getConstant(m_iType);
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
    public boolean isImmutable()
        {
        // by default, the struct access for a type is a mutable structure that is used to construct
        // an object of the corresponding class type
        if (m_access == Access.STRUCT)
            {
            return false;
            }

        return super.isImmutable();
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
    public TypeConstant removeAccess()
        {
        return m_constType;
        }

    @Override
    public TypeConstant ensureAccess(Access access)
        {
        return access == m_access
                ? this
                : getConstantPool().ensureAccessTypeConstant(m_constType, access);
        }

    @Override
    public boolean isNullable()
        {
        assert !m_constType.isNullable();
        return false;
        }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return pool.ensureAccessTypeConstant(type, m_access);
        }

    @Override
    public ResolutionResult resolveContributedName(
            String sName, Access access, MethodConstant idMethod, ResolutionCollector collector)
        {
        access = m_access == Access.STRUCT || access == Access.STRUCT
                ? Access.PRIVATE
                : m_access.minOf(access);
        return super.resolveContributedName(sName, access, idMethod, collector);
        }


    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        // since the immutable modifier is not allowed, it can be assumed that the first
        // TypeConstant in the chain is the access type constant
        Access access = m_access;
        if (access == Access.PUBLIC || access == Access.PROTECTED)
            {
            TypeInfo info = getConstantPool().ensureAccessTypeConstant(
                    getUnderlyingType(), Access.PRIVATE).ensureTypeInfoInternal(errs);
            return info == null
                    ? null
                    : info.limitAccess(access);
            }

        return super.buildTypeInfo(errs);
        }

    @Override
    public void invalidateTypeInfo()
        {
        super.invalidateTypeInfo();

        // clear the TypeInfo for the base and PRIVATE types
        getUnderlyingType().clearTypeInfo();
        if (getAccess() != Access.PRIVATE)
            {
            getConstantPool().ensureAccessTypeConstant(
                    getUnderlyingType(), Access.PRIVATE).clearTypeInfo();
            }
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight, Access accessLeft,
                                                               List<TypeConstant> listLeft)
        {
        return super.isInterfaceAssignableFrom(typeRight, m_access, listLeft);
        }

    @Override
    public Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return super.checkConsumption(sTypeName, m_access, listParams);
        }

    @Override
    public Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return super.checkProduction(sTypeName, m_access, listParams);
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
        return !isHashCached() && m_constType.containsUnresolved();
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
        if (!(obj instanceof AccessTypeConstant that))
            {
            return -1;
            }
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
        return m_constType.getValueString() + ':' + m_access.KEYWORD;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

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
        writePackedLong(out, m_constType.getPosition());
        writePackedLong(out, m_access.ordinal());
        }

    @Override
    public boolean validate(ErrorListener errs)
        {
        if (!isValidated())
            {
            // an access type constant can modify an annotated, a parameterized, or a terminal type
            // constant that refers to a class/interface
            TypeConstant type = m_constType.resolveTypedefs();
            if (!(type instanceof PropertyClassTypeConstant || type.isExplicitClassIdentity(true)))
                {
                log(errs, Severity.ERROR, VE_ACCESS_TYPE_ILLEGAL, type.getValueString());
                return true;
                }

            return super.validate(errs);
            }

        return false;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constType,
               Hash.of(m_access));
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
    private final Access m_access;
    }
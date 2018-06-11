package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import java.util.Set;
import java.util.function.Consumer;

import org.xvm.asm.Component;
import org.xvm.asm.Component.ContributionChain;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the type of a module, package, or class.
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
    protected TypeConstant cloneSingle(TypeConstant type)
        {
        return getConstantPool().ensureAccessTypeConstant(type, m_access);
        }

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


    // ----- type comparison support ---------------------------------------------------------------


    @Override
    public List<ContributionChain> collectContributions(TypeConstant typeLeft, List<TypeConstant> listRight,
                                                        List<ContributionChain> chains)
        {
        switch (m_access)
            {
            case STRUCT:
                if (typeLeft.equals(getConstantPool().typeStruct()))
                    {
                    // any struct is a Struct
                    chains.add(new ContributionChain(
                        new Component.Contribution(Component.Composition.Equal, null)));
                    return chains;
                    }
                if (typeLeft.getAccess() != Access.STRUCT)
                    {
                    // struct is not assignable to anything but a struct
                    return chains;
                    }
                break;

            case PUBLIC:
            case PROTECTED:
            case PRIVATE:
                if (typeLeft.getAccess().compareTo(m_access) > 0)
                    {
                    // for now, disallow any access widening
                    // TODO: allow for MaybeDuckType
                    return chains;
                    }
                break;
            }
        return super.collectContributions(typeLeft, listRight, chains);
        }

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight, Access accessLeft,
                                                               List<TypeConstant> listLeft)
        {
        return super.isInterfaceAssignableFrom(typeRight, m_access, listLeft);
        }

    @Override
    protected boolean validateContributionFrom(TypeConstant typeRight, Access accessLeft,
                                               ContributionChain chain)
        {
        if (chain.first().getComposition() != Component.Composition.MaybeDuckType &&
                typeRight.getAccess().compareTo(m_access) < 0)
            {
            // the l-value (this) should have access no greater that the r-value (that)
            return false;
            }
        return super.validateContributionFrom(typeRight, m_access, chain);
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

    @Override
    public boolean validate(ErrorListener errs)
        {
        boolean fHalt = false;

        if (!isValidated())
            {
            fHalt |= super.validate(errs);

            // an access type constant can modify an annotated, a parameterized, or a terminal type
            // constant that refers to a class/interface
            TypeConstant type = m_constType.resolveTypedefs();
            if (!(type instanceof AnnotatedTypeConstant || type.isExplicitClassIdentity(true)))
                {
                fHalt |= log(errs, Severity.ERROR, VE_ACCESS_TYPE_ILLEGAL, type.getValueString());
                }
            }

        return fHalt;
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

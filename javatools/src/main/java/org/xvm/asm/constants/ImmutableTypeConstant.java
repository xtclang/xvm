package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.util.Hash;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a constant that specifies an explicitly immutable form of an underlying type.
 */
public class ImmutableTypeConstant
        extends TypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is an immutable type.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param constType  a TypeConstant that this constant modifies to be immutable
     */
    public ImmutableTypeConstant(ConstantPool pool, TypeConstant constType)
        {
        super(pool);

        if (constType == null)
            {
            throw new IllegalArgumentException("type required");
            }

        m_constType = constType;
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
    public ImmutableTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iType = readMagnitude(in);
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
    public boolean isImmutabilitySpecified()
        {
        return true;
        }

    @Override
    public boolean isImmutable()
        {
        return true;
        }

    @Override
    public TypeConstant freeze()
        {
        return this;
        }

    @Override
    public TypeConstant ensureAccess(Access access)
        {
        // make sure it's "immutable private C" rather than "private immutable C"
        ConstantPool pool = getConstantPool();
        return cloneSingle(pool, pool.ensureAccessTypeConstant(m_constType, access));
        }

    @Override
    public boolean isNullable()
        {
        return m_constType.isNullable();
        }

    @Override
    public TypeConstant removeNullable()
        {
        return isNullable()
                ? cloneSingle(getConstantPool(), m_constType.removeNullable())
                : this;
        }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return type.isImmutabilitySpecified() ||
                    !type.containsUnresolved() && type.isImmutable()
                ? type
                : pool.ensureImmutableTypeConstant(type);
        }

    @Override
    public TypeConstant resolveConstraints(boolean fPendingOnly)
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = constOriginal.resolveConstraints(fPendingOnly);
        return constResolved == constOriginal
                ? this
                : constResolved.freeze();
        }


    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        // the "immutable" keyword does not affect the TypeInfo, even though the type itself is
        // slightly different
        return m_constType.ensureTypeInfoInternal(errs);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected boolean isDuckTypeAbleFrom(TypeConstant typeRight)
        {
        return typeRight.isImmutable() && super.isDuckTypeAbleFrom(typeRight);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ImmutableType;
        }

    @Override
    protected Object getLocator()
        {
        return m_constType;
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
    protected int compareDetails(Constant obj)
        {
        return obj instanceof ImmutableTypeConstant that
                ? this.m_constType.compareTo(that.m_constType)
                : -1;
        }

    @Override
    public String getValueString()
        {
        return "immutable " + m_constType.getValueString();
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
        writePackedLong(out, indexOf(m_constType));
        }

    @Override
    public boolean validate(ErrorListener errs)
        {
        if (!isValidated())
            {
            boolean fHalt = false;

            // the immutable type constant can modify any type constant other than an immutable
            // type constant
            TypeConstant type = m_constType;
            if (type instanceof ImmutableTypeConstant)
                {
                fHalt |= log(errs, Severity.WARNING, VE_IMMUTABLE_REDUNDANT);
                }

            // a service type cannot be immutable
            if (type.isExplicitClassIdentity(true))
                {
                IdentityConstant idClz = getSingleUnderlyingClass(true);
                if (idClz.getComponent().getFormat() == Component.Format.SERVICE)
                    {
                    log(errs, Severity.ERROR, VE_IMMUTABLE_SERVICE_ILLEGAL, type.getValueString());
                    fHalt = true;
                    }
                }

            if (!fHalt)
                {
                return super.validate(errs);
                }
            }

        return false;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constType);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the type constant.
     */
    private int m_iType;

    /**
     * The type referred to.
     */
    private TypeConstant m_constType;
    }
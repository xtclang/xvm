package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.InitializingHandle;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a singleton instance of a const class as a constant value.
 */
public class SingletonConstant
        extends ValueConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a literal.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param format      the format
     * @param constClass  the class constant for the singleton value
     */
    public SingletonConstant(ConstantPool pool, Format format, IdentityConstant constClass)
        {
        super(pool);

        switch (format)
            {
            case SingletonConst:
            case EnumValueConst:
            case SingletonService:
                break;

            default:
                throw new IllegalArgumentException("invalid format " + format);
            }

        if (constClass == null)
            {
            throw new IllegalArgumentException("class of the singleton value required");
            }

        f_fmt        = format;
        m_constClass = constClass;
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
    public SingletonConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        f_fmt    = format;
        m_iClass = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        m_constClass = (IdentityConstant) getConstantPool().getConstant(m_iClass);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        return m_constClass.getType();
        }

    /**
     * @return  the class constant for the singleton value
     */
    public IdentityConstant getClassConstant()
        {
        return m_constClass;
        }

    /**
     * {@inheritDoc}
     * @return  the class constant for the singleton value
     */
    @Override
    public Constant getValue()
        {
        return m_constClass;
        }


    // ----- run-time support  ---------------------------------------------------------------------

    /**
     * @return an ObjectHandle representing this singleton value
     */
    public ObjectHandle getHandle()
        {
        return m_handle;
        }

    /**
     * Set the handle for this singleton's value.
     *
     * @param handle  the corresponding handle
     */
    public void setHandle(ObjectHandle handle)
        {
        // the only scenarios when the singleton value can be reset are when it turns from
        // INITIALIZING to anything or from a struct to an immutable value
        assert handle != null;

        m_handle        = handle;
        m_fInitializing = false;
        }

    /**
     * Mark this ObjectHandle as being initialized.
     *
     * @return false iff the ObjectHandle has already been marked as "initializing"
     */
    public boolean markInitializing()
        {
        if (m_fInitializing)
            {
            m_handle = new InitializingHandle(this);
            return false;
            }
        return m_fInitializing = true;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return f_fmt;
        }

    @Override
    public boolean containsUnresolved()
        {
        return !isHashCached() && m_constClass.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constClass);
        }

    @Override
    public SingletonConstant resolveTypedefs()
        {
        IdentityConstant constOld = m_constClass;
        IdentityConstant constNew = (IdentityConstant) constOld.resolveTypedefs();
        return constNew == constOld
                ? this
                : (SingletonConstant) getConstantPool().register(
                        new SingletonConstant(getConstantPool(), f_fmt, constNew));
        }

    @Override
    public Object getLocator()
        {
        return getClassConstant();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof SingletonConstant))
            {
            return -1;
            }
        return this.m_constClass.compareTo(((SingletonConstant) that).m_constClass);
        }

    @Override
    public String getValueString()
        {
        return m_constClass.getName();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constClass = (IdentityConstant) pool.register(m_constClass);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constClass.getPosition());
        }

    @Override
    public String getDescription()
        {
        return "singleton-" + (f_fmt == Format.SingletonConst ? "const=" : "service=") +
                m_constClass.getName();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constClass);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The format of the constant; either SingletonConst or SingletonService.
     */
    private final Format f_fmt;

    /**
     * Used during deserialization: holds the index of the class constant.
     */
    private transient int m_iClass;

    /**
     * The IdentityConstant for the class of the singleton value.
     */
    private IdentityConstant m_constClass;

    /**
     * The ObjectHandle representing this singleton's value.
     */
    private transient ObjectHandle m_handle;

    /**
     * Set to true when the handle for this singleton is being initialized.
     */
    private transient boolean m_fInitializing;
    }

package org.xvm.asm.constants;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A NamedConstant is a constant whose purpose is to identify a structure of a specified name that
 * exists within its parent structure.
 */
public abstract class NamedConstant
        extends IdentityConstant
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
    public NamedConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iParent = readMagnitude(in);
        m_iName   = readMagnitude(in);
        }

    /**
     * Construct a constant whose purpose is to identify a structure of a specified name that exists
     * within its parent structure.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the module, package, class, or method that contains this property
     * @param sName        the property name
     */
    public NamedConstant(ConstantPool pool, Constant constParent, String sName)
        {
        super(pool);

        if (constParent == null)
            {
            throw new IllegalArgumentException("parent required");
            }

        if (sName == null)
            {
            throw new IllegalArgumentException("name required");
            }

        m_constParent = constParent;
        m_constName   = pool.ensureCharStringConstant(sName);
        }

    /**
     * Internal constructor, used for temporary (unresolved) constants.
     *
     * @param pool  the ConstantPool
     */
    protected NamedConstant(ConstantPool pool)
        {
        super(pool);
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public IdentityConstant getParentConstant()
        {
        return (IdentityConstant) m_constParent;
        }

    @Override
    public String getName()
        {
        return m_constName.getValue();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public abstract Format getFormat();

    @Override
    protected int compareDetails(Constant that)
        {
        int n = this.m_constParent.compareTo(((NamedConstant) that).m_constParent);
        if (n == 0)
            {
            n = this.m_constName.compareTo(((NamedConstant) that).m_constName);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        String sParent;
        final Constant constParent = m_constParent;
        switch (constParent.getFormat())
            {
            case Module:
                sParent = ((ModuleConstant) constParent).getUnqualifiedName();
                return sParent + ':' + m_constName.getValue();

            case Package:
            case Class:
                sParent = constParent.getValueString();
                return sParent + '.' + m_constName.getValue();

            case Property:
                sParent = ((NamedConstant) constParent).getName();
                return sParent + '#' + m_constName.getValue();

            case Method:
                sParent = ((MethodConstant) constParent).getName() + "(?)";
                return sParent + '#' + m_constName.getValue();

            default:
                throw new IllegalStateException();
            }
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        final ConstantPool pool = getConstantPool();
        m_constParent = pool.getConstant(m_iParent);
        m_constName   = (CharStringConstant) pool.getConstant(m_iName);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constParent = pool.register(m_constParent);
        m_constName   = (CharStringConstant) pool.register(m_constName);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constParent.getPosition());
        writePackedLong(out, m_constName.getPosition());
        }

    @Override
    public abstract String getDescription();


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constParent.hashCode() * 17
                + m_constName.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the parent structure.
     * property.
     */
    private int m_iParent;

    /**
     * During disassembly, this holds the index of the constant that specifies the name of the
     * structure identified by this constant.
     */
    private int m_iName;

    /**
     * The constant that identifies the structure which is the parent of the structure identified by
     * this constant.
     */
    private Constant m_constParent;

    /**
     * The constant that holds the name of the structure identified by this constant.
     */
    private CharStringConstant m_constName;
    }

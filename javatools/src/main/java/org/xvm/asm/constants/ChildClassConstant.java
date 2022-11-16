package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import  org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent an auto-narrowing named child class.
 */
public class ChildClassConstant
        extends PseudoConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant that represents the class of a non-static child whose identity is
     * auto-narrowing.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the parent class, which must be an auto-narrowing identity constant
     * @param sName        the child name
     */
    public ChildClassConstant(ConstantPool pool, PseudoConstant constParent, String sName)
        {
        super(pool);

        if (constParent == null)
            {
            throw new IllegalArgumentException("parent required");
            }

        if (!constParent.isClass())
            {
            throw new IllegalArgumentException("parent does not represent a class: " + constParent);
            }

        if (!constParent.isAutoNarrowing())
            {
            throw new IllegalArgumentException("parent is not auto-narrowing: " + constParent);
            }

        if (sName == null)
            {
            throw new IllegalArgumentException("name required");
            }

        m_constParent = constParent;
        m_constName   = pool.ensureStringConstant(sName);
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
    public ChildClassConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iParent = readMagnitude(in);
        m_iName   = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        ConstantPool pool = getConstantPool();

        m_constParent = (PseudoConstant) pool.getConstant(m_iParent);
        m_constName   = (StringConstant) pool.getConstant(m_iName);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the parent of the child class
     */
    public PseudoConstant getParent()
        {
        return m_constParent;
        }

    /**
     * @return the name of the child class
     */
    public String getName()
        {
        return m_constName.getValue();
        }

    @Override
    public IdentityConstant getDeclarationLevelClass()
        {
        PseudoConstant constParent = m_constParent;
        switch (constParent.getFormat())
            {
            case ParentClass:
            case ThisClass:
            case ChildClass:
                IdentityConstant idParent = constParent.getDeclarationLevelClass();
                return idParent.getComponent().getChild(m_constName.getValue()).getIdentityConstant();

            default:
                throw new IllegalStateException("constParent=" + constParent);
            }
        }

    @Override
    public boolean isCongruentWith(PseudoConstant that)
        {
        if (that instanceof ChildClassConstant)
            {
            ChildClassConstant thatChild = (ChildClassConstant) that;

            return this.m_constParent.isCongruentWith(thatChild.m_constParent)
                && this.m_constName.equals           (thatChild.m_constName);
            }
        return false;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ChildClass;
        }

    @Override
    public TypeConstant getType()
        {
        return getConstantPool().ensureChildTypeConstant(m_constParent.getType(), m_constName.getValue());
        }

    @Override
    public boolean isClass()
        {
        return true;
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return true;
        }

    @Override
    public boolean containsUnresolved()
        {
        return !isHashCached() && (m_constParent.containsUnresolved() || m_constName.containsUnresolved());
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constParent);
        visitor.accept(m_constName);
        }

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constParent,
               Hash.of(m_constName));
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof ChildClassConstant))
            {
            return -1;
            }
        int nResult = m_constParent.compareTo(((ChildClassConstant) that).m_constParent);
        if (nResult == 0)
            {
            nResult = m_constName.compareTo(((ChildClassConstant) that).m_constName);
            }
        return nResult;
        }

    @Override
    public String getValueString()
        {
        return m_constParent.getValueString() + '.' + getName();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constParent = (PseudoConstant) pool.register(m_constParent);
        m_constName   = (StringConstant) pool.register(m_constName);
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
    public String getDescription()
        {
        return "parent=" + m_constParent + ", child=" + getName();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the parent of the
     * auto-narrowing child. (The parent must itself be auto-narrowing.)
     */
    private int m_iParent;

    /**
     * During disassembly, this holds the index of the constant that specifies the name of the
     * child.
     */
    private int m_iName;

    /**
     * The constant that identifies the auto-narrowing parent of the child represented by this
     * constant.
     */
    private PseudoConstant m_constParent;

    /**
     * The constant that holds the name of the child identified by this constant.
     */
    private StringConstant m_constName;
    }

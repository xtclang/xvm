package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent an auto-narrowing class constant for the parent class of a nested non-static
 * ("instance") inner class.
 */
public class ParentClassConstant
        extends PseudoConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is an auto-narrowable parent class identity.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constChild  a TypeConstant that this constant represents the enclosing parent of
     */
    public ParentClassConstant(ConstantPool pool, PseudoConstant constChild)
        {
        super(pool);

        if (constChild == null)
            {
            throw new IllegalArgumentException("child class required");
            }

        if (!(constChild instanceof ParentClassConstant || constChild instanceof ThisClassConstant))
            {
            throw new IllegalArgumentException("child must be an auto-narrowable class identity," +
                    " either \"this:class\" or a parent class thereof (child=" + constChild + ')');
            }

        m_constChild = constChild;
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
    public ParentClassConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iChild = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        m_constChild = (PseudoConstant) getConstantPool().getConstant(m_iChild);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the PseudoConstant that this constant represents the parent class of
     */
    public PseudoConstant getChildClass()
        {
        return m_constChild;
        }

    /**
     * @return the number of {@link ParentClassConstant} (including this one) that wrap an
     *         underlying {@link ThisClassConstant}
     */
    public int getDepth()
        {
        PseudoConstant idClz  = this;
        int            cDepth = 0;
        do
            {
            ++cDepth;
            idClz = ((ParentClassConstant) idClz).getChildClass();
            }
        while (idClz instanceof ParentClassConstant);

        assert idClz instanceof ThisClassConstant;
        return cDepth;
        }

    @Override
    public IdentityConstant getDeclarationLevelClass()
        {
        PseudoConstant constChild = m_constChild;
        switch (constChild.getFormat())
            {
            case ParentClass:
            case ChildClass:
            case ThisClass:
                IdentityConstant idParent = constChild.getDeclarationLevelClass().getParentConstant();
                while (!idParent.isClass())
                    {
                    idParent = idParent.getParentConstant();
                    }
                return idParent;

            default:
                throw new IllegalStateException("constChild=" + constChild);
            }
        }

    @Override
    public boolean isCongruentWith(PseudoConstant that)
        {
        if (that instanceof ParentClassConstant)
            {
            ParentClassConstant thatParent = (ParentClassConstant) that;

            return this.m_constChild.isCongruentWith(thatParent.m_constChild);
            }
        return false;
        }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ParentClass;
        }

    @Override
    public TypeConstant getType()
        {
        return getConstantPool().ensureParentTypeConstant(m_constChild.getType());
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
    protected Object getLocator()
        {
        return m_constChild.getLocator() != null
                ? m_constChild
                : null;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_constChild.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constChild);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof ParentClassConstant))
            {
            return -1;
            }
        return this.m_constChild.compareTo(((ParentClassConstant) that).m_constChild);
        }

    @Override
    public String getValueString()
        {
        // this isn't real syntax, but it at least conveys the information
        return m_constChild.getValueString() + ":parent";
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constChild = (PseudoConstant) pool.register(m_constChild);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constChild.getPosition());
        }

    @Override
    public String getDescription()
        {
        return "child=" + m_constChild;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constChild);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the child class constant.
     */
    private int m_iChild;

    /**
     * The child class that this is a parent class of.
     */
    private PseudoConstant m_constChild;
    }

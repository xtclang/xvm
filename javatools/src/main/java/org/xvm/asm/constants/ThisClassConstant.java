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
 * Represent the auto-narrowing class of <i>this</i>.
 */
public class ThisClassConstant
        extends PseudoConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is the auto-narrowing identifier "this:class".
     *
     * @param pool  the ConstantPool that will contain this Constant
     */
    public ThisClassConstant(ConstantPool pool, IdentityConstant constClass)
        {
        super(pool);

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
    public ThisClassConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iClass = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        m_constClass = (IdentityConstant) getConstantPool().getConstant(m_iClass);
        }


    // ----- Pseudo-constant methods --------------------------------------------------------------

    @Override
    public boolean isCongruentWith(PseudoConstant that)
        {
        // ThisClassConstants are congruent regardless of the declaration level
        return that instanceof ThisClassConstant;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public IdentityConstant getDeclarationLevelClass()
        {
        return m_constClass;
        }

    @Override
    public Format getFormat()
        {
        return Format.ThisClass;
        }

    @Override
    public TypeConstant getType()
        {
        return getConstantPool().ensureThisTypeConstant(this, null);
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
        return m_constClass;
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
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof ThisClassConstant))
            {
            return -1;
            }
        return this.m_constClass.compareTo(((ThisClassConstant) that).m_constClass);
        }

    @Override
    public String getValueString()
        {
        return THIS_CLASS + '(' + m_constClass.getValueString() + ')';
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
        return "name=" + THIS_CLASS
                + ", decl-level=" + m_constClass;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constClass);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The source code identifier of the auto-narrowing "this class".
     */
    public static final String THIS_CLASS = "this:class";

    /**
     * During disassembly, this holds the index of the class constant.
     */
    private int m_iClass;

    /**
     * The declaration-level class that this:class refers to (from which auto-narrowing occurs).
     */
    private IdentityConstant m_constClass;
    }
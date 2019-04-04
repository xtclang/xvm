package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a formal child of a generic property, type parameter or formal child constant.
 */
public class FormalTypeChildConstant
        extends    PseudoConstant
        implements FormalConstant
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
    public FormalTypeChildConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iParent = readMagnitude(in);
        m_iName   = readMagnitude(in);
        }

    /**
     * Construct a constant that represents the class of a non-static child whose identity is
     * auto-narrowing.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the parent constant, which must be a FormalConstant
     * @param sName        the formal child name
     */
    public FormalTypeChildConstant(ConstantPool pool, Constant constParent, String sName)
        {
        super(pool);

        if (constParent == null)
            {
            throw new IllegalArgumentException("parent required");
            }

        switch (constParent.getFormat())
            {
            case FormalTypeChild:
            case TypeParameter:
                break;

            case Property:
                if (((PropertyConstant) constParent).isTypeParameter())
                    {
                    break;
                    }
                // fall through
            default:
                throw new IllegalArgumentException("parent does not represent a formal constant: " + constParent);
            }

        if (sName == null)
            {
            throw new IllegalArgumentException("name required");
            }

        m_constParent = constParent;
        m_constName   = pool.ensureStringConstant(sName);
        }


    // ----- FormalConstant methods ----------------------------------------------------------------

    /**
     * Dereference a property constant that is used for a type parameter, to obtain the constraint
     * type of that type parameter.
     *
     * @return the constraint type of the type parameter
     */
    @Override
    public TypeConstant getConstraintType()
        {
        Constant     constParent = m_constParent;
        TypeConstant typeParent;

        switch (constParent.getFormat())
            {
            case FormalTypeChild: // recurse
            case TypeParameter:
            case Property:
                typeParent = ((FormalConstant) constParent).getConstraintType();
                break;

            default:
                throw new IllegalStateException();
            }

        String sName = m_constName.getValue();

        assert typeParent.containsGenericParam(sName);

        TypeConstant type = typeParent.getSingleUnderlyingClass(true).getFormalType().resolveGenericType(sName);

        assert type.isGenericType();
        PropertyConstant idProp = (PropertyConstant) type.getDefiningConstant();
        return idProp.getConstraintType();
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the parent of this formal child
     */
    public Constant getParent()
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

    /**
     * @return the top formal parent of this formal child
     */
    public Constant getTopParent()
        {
        Constant constParent = m_constParent;
        while (constParent.getFormat() == Format.FormalTypeChild)
            {
            constParent = ((FormalTypeChildConstant) constParent).getParent();
            }
        return constParent;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.FormalTypeChild;
        }

    @Override
    public TypeConstant getType()
        {
        return getConstantPool().ensureTerminalTypeConstant(this);
        }

    @Override
    public boolean isClass()
        {
        return false;
        }

    @Override
    public boolean containsUnresolved()
        {
        return false;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constParent);
        visitor.accept(m_constName);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof FormalTypeChildConstant))
            {
            return -1;
            }
        int nResult = m_constParent.compareTo(((FormalTypeChildConstant) that).m_constParent);
        if (nResult == 0)
            {
            nResult = m_constName.compareTo(((FormalTypeChildConstant) that).m_constName);
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
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constParent = getConstantPool().getConstant(m_iParent);
        m_constName   = (StringConstant) getConstantPool().getConstant(m_iName);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constParent = pool.register(m_constParent);
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
     * The constant that identifies the parent formal property, type parameter or formal child.
     */
    private Constant m_constParent;

    /**
     * The constant that holds the name of the child identified by this constant.
     */
    private StringConstant m_constName;
    }

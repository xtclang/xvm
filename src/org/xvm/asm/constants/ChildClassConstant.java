package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readMagnitude;


/**
 * Represent an auto-narrowing non-static child class constant.
 */
public class ChildClassConstant
        extends PseudoConstant
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
    public ChildClassConstant(ConstantPool pool, Format format, DataInput in)
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
        m_constName   = pool.ensureCharStringConstant(sName);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the ClassTypeConstant for the public interface of this class
     */
    public ClassTypeConstant asTypeConstant()
        {
        return getConstantPool().ensureThisTypeConstant(Access.PUBLIC);
        }

    /**
     * @return TODO
     */
    public String getName()
        {
        return m_constName.getValue();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ChildClass;
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return true;
        }

    @Override
    public Object getLocator()
        {
        return m_constParent instanceof ThisClassConstant      // indicates "this:class"
                ? getName()
                : null;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        int nResult = m_constParent.compareTo(((ChildClassConstant) that).m_constParent);
        if (nResult == 0)
            {
            nResult = m_constName.compareTo(((ChildClassConstant) that).m_constName);
            }
        return nResult;
        }

    @Override
    public String getDescription()
        {
        // TODO need more info, but must first figure out what we want to show and how to show it
        return "child=" + getName();
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

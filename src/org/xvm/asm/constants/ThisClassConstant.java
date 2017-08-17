package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent the auto-narrowing class of <i>this</i></li>.
 */
public class ThisClassConstant
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
    public ThisClassConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        }

    /**
     * Construct a constant whose value is the auto-narrowing identifier "this:class".
     *
     * @param pool  the ConstantPool that will contain this Constant
     */
    public ThisClassConstant(ConstantPool pool)
        {
        super(pool);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the ClassTypeConstant for the public interface of this class
     */
    public ParameterizedTypeConstant asTypeConstant()
        {
        return getConstantPool().ensureThisTypeConstant(Access.PUBLIC);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ThisClass;
        }

    @Override
    public Object getLocator()
        {
        return THIS_CLASS;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return 0;
        }

    @Override
    public String getValueString()
        {
        return THIS_CLASS;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        }

    @Override
    public String getDescription()
        {
        return "name=" + THIS_CLASS;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return -99;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The source code identifier of the auto-narrowing "this class".
     */
    public static final String THIS_CLASS = "this:class";

    /**
     * During disassembly, this holds the index of the constant that specifies the name.
     */
    private int m_iName;

    /**
     * The constant that holds the symbolic name.
     */
    private StringConstant m_constName;
    }

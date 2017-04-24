package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.XvmStructure;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a Class constant.
 */
public class ClassConstant
        extends Constant
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
    public ClassConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iParent = readIndex(in);
        m_iName   = readIndex(in);
        }

    /**
     * Construct a constant whose value is a class identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the module, package, class, or method that
     *                     contains this class
     * @param sName        the unqualified class name
     */
    public ClassConstant(ConstantPool pool, Constant constParent, String sName)
        {
        super(pool);

        if (constParent == null ||
                !( constParent.getType() == Type.Module
                || constParent.getType() == Type.Package
                || constParent.getType() == Type.Class
                || constParent.getType() == Type.Method ))
            {
            throw new IllegalArgumentException("parent module, package, class, or method required");
            }

        if (sName == null)
            {
            throw new IllegalArgumentException("class name required");
            }

        m_constParent = constParent;
        m_constName   = pool.ensureCharStringConstant(sName);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Obtain the module, package, class, method, or property that this class is contained within.
     *
     * @return the containing constant
     */
    public Constant getNamespace()
        {
        return m_constParent;
        }

    /**
     * Get the unqualified name of the class.
     *
     * @return the class's unqualified name
     */
    public String getName()
        {
        return m_constName.getValue();
        }

    /**
     * Determine if this ClassConstant is the "Object" class.
     *
     * @return true iff this ClasConstant represents the Ecstasy root Object class
     */
    public boolean isEcstasyObject()
        {
        return getName().equals(CLASS_OBJECT)
                && getNamespace().getType() == Type.Module
                && ((ModuleConstant) getNamespace()).isEcstasyModule();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Type getType()
        {
        return Type.Class;
        }

    @Override
    public Format getFormat()
        {
        return Format.Class;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        int n = this.m_constParent.compareTo(((ClassConstant) that).m_constParent);
        if (n == 0)
            {
            n = this.m_constName.compareTo(((ClassConstant) that).m_constName);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return m_constParent instanceof ClassConstant
                ? m_constParent.getValueString() + '.' + m_constName.getValue()
                : m_constName.getValue();
        }

    @Override
    protected ClassStructure instantiate(XvmStructure xsParent)
        {
        return new ClassStructure(xsParent, this);
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
        writePackedLong(out, indexOf(m_constParent));
        writePackedLong(out, indexOf(m_constName));
        }

    @Override
    public String getDescription()
        {
        Constant constParent = m_constParent;
        while (constParent instanceof ClassConstant)
            {
            constParent = ((ClassConstant) constParent).getNamespace();
            }

        return "class=" + getValueString() + ", " + constParent.getDescription();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constParent.hashCode() * 17 + m_constName.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the parent of this
     * class.
     */
    private int m_iParent;

    /**
     * During disassembly, this holds the index of the constant that specifies the class name.
     */
    private int m_iName;

    /**
     * The constant that represents the parent of this class.
     */
    private Constant m_constParent;

    /**
     * The constant that holds the unqualified name of the class.
     */
    private CharStringConstant m_constName;
    }

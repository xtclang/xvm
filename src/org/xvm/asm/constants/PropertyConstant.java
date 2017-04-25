package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.XvmStructure;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a property constant.
 */
public class PropertyConstant
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
    public PropertyConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iParent = readMagnitude(in);
        m_iType   = readMagnitude(in);
        m_iName   = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a property identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the module, package, class, or method that contains this property
     * @param sName        the property name
     */
    public PropertyConstant(ConstantPool pool, Constant constParent, String sName)
        {
        super(pool);

        if (constParent == null || !(constParent.getFormat() == Format.Module
                || constParent.getFormat() == Format.Package
                || constParent.getFormat() == Format.Class
                || constParent.getFormat() == Format.Method))
            {
            throw new IllegalArgumentException("parent module, package, class, or method required");
            }

        if (sName == null)
            {
            throw new IllegalArgumentException("property name required");
            }

        m_constParent = constParent;
        m_constName   = pool.ensureCharStringConstant(sName);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Obtain the module, package, class or method that this property is
     * contained within.
     *
     * @return the containing constant
     */
    public Constant getNamespace()
        {
        return m_constParent;
        }

    /**
     * Get the name of the property.
     *
     * @return the property name
     */
    public String getName()
        {
        return m_constName.getValue();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Property;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        int n = this.m_constParent.compareTo(((PropertyConstant) that).m_constParent);
        if (n == 0)
            {
            n = this.m_constName.compareTo(((PropertyConstant) that).m_constName);
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
                break;
            case Package:
                sParent = ((PackageConstant) constParent).getName();
                break;
            case Class:
                sParent = ((ClassConstant) constParent).getName();
                break;
            case Method:
                sParent = ((MethodConstant) constParent).getName() + "(..)";
                break;
            default:
                throw new IllegalStateException();
            }
        return sParent + '.' + m_constName.getValue();
        }

    @Override
    protected PropertyStructure instantiate(XvmStructure xsParent)
        {
        return new PropertyStructure(xsParent, this);
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
    public String getDescription()
        {
        return "property=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constParent.hashCode() * 17
                + m_constName.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the parent of this
     * property.
     */
    private int m_iParent;

    /**
     * During disassembly, this holds the index of the constant that specifies the type of this
     * property.
     */
    private int m_iType;

    /**
     * During disassembly, this holds the index of the constant that specifies the name of this
     * property.
     */
    private int m_iName;

    /**
     * The constant that represents the parent of this property. A Property can be a child of a
     * Module, a Package, a Class, or a Method.
     */
    private Constant m_constParent;

    /**
     * The constant that holds the name of the property.
     */
    private CharStringConstant m_constName;
    }

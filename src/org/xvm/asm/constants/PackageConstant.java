package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.PackageStructure;
import org.xvm.asm.XvmStructure;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a Package constant. A Package constant is composed of a constant identifying the Module
 * or Package which contains this package, and the unqualified name of this Package. A Module can be
 * contained within another Module (either by reference or by embedding), in which case it is
 * represented as a Package; in this case, the Package constant will have a reference to the
 * corresponding Module constant.
 */
public class PackageConstant
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
    public PackageConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iParent = readMagnitude(in);
        m_iName   = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a package identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the module or package that contains this package
     * @param sName        the unqualified package name
     */
    public PackageConstant(ConstantPool pool, Constant constParent, String sName)
        {
        super(pool);

        if (constParent == null || !(constParent.getFormat() == Format.Module ||
                constParent.getFormat() == Format.Package))
            {
            throw new IllegalArgumentException("parent module or package required");
            }

        if (sName == null)
            {
            throw new IllegalArgumentException("package name required");
            }

        m_constParent = constParent;
        m_constName   = pool.ensureCharStringConstant(sName);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Obtain the module or package that this package is contained within.
     *
     * @return the containing module or package constant
     */
    public Constant getNamespace()
        {
        return m_constParent;
        }

    /**
     * Get the unqualified name of the Package.
     *
     * @return the package's unqualified name
     */
    public String getName()
        {
        return m_constName.getValue();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Package;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        int n = this.m_constParent.compareTo(((PackageConstant) that).m_constParent);
        if (n == 0)
            {
            n = this.m_constName.compareTo(((PackageConstant) that).m_constName);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return m_constParent instanceof PackageConstant
                ? m_constParent.getValueString() + '.' + m_constName.getValue()
                : m_constName.getValue();
        }

    @Override
    protected PackageStructure instantiate(XvmStructure xsParent)
        {
        return new PackageStructure(xsParent, this);
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
        Constant constParent = m_constParent;
        while (constParent instanceof PackageConstant)
            {
            constParent = ((PackageConstant) constParent).getNamespace();
            }

        return "package=" + getValueString() + ", " + constParent.getDescription();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constParent.hashCode() * 17 + m_constName.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the parent package or
     * module of this package.
     */
    private int m_iParent;

    /**
     * During disassembly, this holds the index of the constant that specifies the name.
     */
    private int m_iName;

    /**
     * The constant that represents the module or package that contains this package.
     */
    private Constant m_constParent;

    /**
     * The constant that holds the unqualified name of the package.
     */
    private CharStringConstant m_constName;
    }

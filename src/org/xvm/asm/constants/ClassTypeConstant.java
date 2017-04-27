package org.xvm.asm.constants;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the type of a module, package, or class.
 *
 * @author cp 2017.04.25
 */
public class ClassTypeConstant
        extends TypeConstant
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
    public ClassTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iClass = readIndex(in);
        m_access = Access.valueOf(readIndex(in));
        }

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constClass  a ModuleConstant, PackageConstant, or ClassConstant
     */
    public ClassTypeConstant(ConstantPool pool, Constant constClass, Access access)
        {
        super(pool);

        if (constClass == null ||
                !( constClass.getFormat() == Format.Module
                || constClass.getFormat() == Format.Package
                || constClass.getFormat() == Format.Class  ))
            {
            throw new IllegalArgumentException("module, package, or class required");
            }

        if (access == null)
            {
            access = Access.PUBLIC;
            }

        m_constClass = constClass;
        m_access     = access;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return a ModuleConstant, PackageConstant, or ClassConstant
     */
    public Constant getClassConstant()
        {
        return m_constClass;
        }

    /**
     * @return the access modifier for the class (public, private, etc.)
     */
    public Access getAccess()
        {
        return m_access;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ClassType;
        }

    @Override
    protected Object getLocator()
        {
        return m_access == Access.PUBLIC
                ? m_constClass
                : null;
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        ClassTypeConstant that = (ClassTypeConstant) obj;
        int n = this.m_constClass.compareTo(that.m_constClass);
        if (n == 0)
            {
            n = this.m_access.compareTo(that.m_access);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return m_access == Access.PUBLIC
                ? m_constClass.getValueString()
                : m_constClass.getValueString() + ':' + m_access.KEYWORD;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constClass = getConstantPool().getConstant(m_iClass);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constClass = pool.register(m_constClass);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, indexOf(m_constClass));
        writePackedLong(out, m_access.ordinal());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constClass.hashCode() + m_access.ordinal();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the class etc. constant.
     */
    private int m_iClass;

    /**
     * The class referred to. May be a ModuleConstant, PackageConstant, or ClassConstant.
     */
    private Constant m_constClass;

    /**
     * The public/private/etc. modifier for the class referred to.
     */
    private Access m_access;
    }

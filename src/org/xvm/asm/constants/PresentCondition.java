package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;

import org.xvm.util.Handy;

import static org.xvm.util.Handy.compareObjects;
import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Evaluates if a specified VM structure will be available in the container.
 */
public class PresentCondition
        extends ConditionalConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value
     *                from
     *
     * @throws IOException  if an issue occurs reading the Constant
     *                      value
     */
    protected PresentCondition(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iStruct   = readMagnitude(in);
        m_iVer      = readIndex(in);
        m_fExactVer = in.readBoolean();
        }

    /**
     * Construct a PresentCondition.
     *
     * @param pool           the ConstantPool that will contain this Constant
     * @param constVMStruct  the Module, Package, Class, Property or Method
     * @param constVer       the optional specific version of the Module, Package, Class, Property
     *                       or Method
     * @param fExactVer      true if the version has to match exactly
     */
    protected PresentCondition(ConstantPool pool, Constant constVMStruct, VersionConstant constVer, boolean fExactVer)
        {
        super(pool);
        m_constStruct = constVMStruct;
        m_constVer    = constVer;
        m_fExactVer   = fExactVer;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Obtain the constant of the XVM Structure that this conditional
     * constant represents the conditional presence of.
     *
     * @return the constant representing the XVM Structure to be tested
     *         for
     */
    public Constant getPresentConstant()
        {
        return m_constStruct;
        }

    /**
     * Obtain the version of the XVM Structure that must exist, or null
     * if the test does not evaluate the version.
     *
     * @return the version that is required, or null if any version is
     *         acceptable
     */
    public VersionConstant getVersionConstant()
        {
        return m_constVer;
        }

    /**
     * Determine if the exact specified version is required, or if
     * subsequent versions are acceptable.
     *
     * @return true if the exact version specified is required
     */
    public boolean isExactVersion()
        {
        return m_fExactVer;
        }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    public boolean evaluate(LinkerContext ctx)
        {
        final VersionConstant constVer = m_constVer;
        return constVer == null
                ? ctx.isVisible(m_constStruct)
                : ctx.isVisible(m_constStruct, m_constVer, m_fExactVer);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionPresent;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        org.xvm.asm.constants.PresentCondition constThat = (org.xvm.asm.constants.PresentCondition) that;
        int nResult = m_constStruct.compareTo(constThat.m_constStruct);
        if (nResult == 0)
            {
            nResult = compareObjects(m_constVer, constThat.m_constVer);
            if (nResult == 0)
                {
                nResult = Boolean.valueOf(m_fExactVer).compareTo(Boolean.valueOf(constThat.m_fExactVer));
                }
            }

        return nResult;
        }

    @Override
    public String getValueString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("isPresent(")
          .append(m_constStruct);

        final VersionConstant constVer = m_constVer;
        if (constVer != null)
            {
            sb.append(", ")
              .append(m_constVer.getValueString());

            if (m_fExactVer)
                {
                sb.append(", EXACT");
                }
            }

        sb.append(')');

        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        final ConstantPool pool = getConstantPool();

        m_constStruct = pool.getConstant(m_iStruct);
        assert     m_constStruct instanceof ModuleConstant
                || m_constStruct instanceof PropertyConstant
                || m_constStruct instanceof ClassConstant
                || m_constStruct instanceof PropertyConstant
                || m_constStruct instanceof MethodConstant;

        final int iVer = m_iVer;
        if (iVer >= 0)
            {
            m_constVer = (VersionConstant) pool.getConstant(iVer);
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constStruct = pool.register(m_constStruct);
        m_constVer    = (VersionConstant) pool.register(m_constVer);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constStruct.getPosition());
        writePackedLong(out, m_constVer == null ? -1 : m_constVer.getPosition());
        out.writeBoolean(m_fExactVer);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return Handy.hashCode(m_constStruct) ^ Handy.hashCode(m_constVer);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the structure id.
     */
    private transient int m_iStruct;

    /**
     * During disassembly, this holds the index of the version or -1.
     */
    private transient int m_iVer;

    /**
     * A ModuleConstant, PackageConstant, ClassConstant, PropertyConstant, or MethodConstant.
     */
    private Constant m_constStruct;

    /**
     * The optional version identifier for the VMStructure that must be present.
     */
    private VersionConstant m_constVer;

    /**
     * True if the version has to match exactly.
     */
    private boolean m_fExactVer;
    }

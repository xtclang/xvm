package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;

import org.xvm.util.Handy;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Evaluates if the module is of a specified version. The VersionCondition applies to (tests for)
 * the version of the current module only; in other words, the VersionConsant is used to
 * conditionally include or exclude VMStructures within <b>this</b> module based on the version of
 * <b>this</b> module. This allows multiple versions of a module to be colocated within a single
 * FileStructure, for example.
 * <p/>
 * To evaluate if another module (or component thereof) is of a specified version, a {@link
 * PresentCondition} is used.
 */
public class VersionCondition
        extends ConditionalConstant
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
    public VersionCondition(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iVer      = readMagnitude(in);
        m_fExactVer = in.readBoolean();
        }

    /**
     * Construct a VersionCondition.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param constVer   the version of this Module to evaluate
     * @param fExactVer  true if the version has to match exactly
     */
    public VersionCondition(ConstantPool pool, VersionConstant constVer, boolean fExactVer)
        {
        super(pool);
        m_constVer  = constVer;
        m_fExactVer = fExactVer;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Obtain the version of the current module that the condition is predicated on.
     *
     * @return the version that is required
     */
    public VersionConstant getVersionConstant()
        {
        return m_constVer;
        }

    /**
     * Determine if the exact specified version is required, or if subsequent versions are
     * acceptable.
     *
     * @return true iff the exact version specified is required
     */
    public boolean isExactVersion()
        {
        return m_fExactVer;
        }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    public boolean evaluate(LinkerContext ctx)
        {
        return ctx.isVersionMatch(m_constVer, m_fExactVer);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionVersion;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        org.xvm.asm.constants.VersionCondition constThat = (org.xvm.asm.constants.VersionCondition) that;
        int nResult = m_constVer.compareTo(constThat.m_constVer);
        if (nResult == 0)
            {
            nResult = Boolean.valueOf(m_fExactVer).compareTo(Boolean.valueOf(constThat.m_fExactVer));
            }

        return nResult;
        }

    @Override
    public String getValueString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("isVersion(")
          .append(m_constVer.getValueString());

        if (m_fExactVer)
            {
            sb.append(", EXACT");
            }

        return sb.append(')').toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constVer = (VersionConstant) getConstantPool().getConstant(m_iVer);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constVer = (VersionConstant) pool.register(m_constVer);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constVer.getPosition());
        out.writeBoolean(m_fExactVer);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return Handy.hashCode(m_constVer);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the version or -1.
     */
    private transient int m_iVer;

    /**
     * A ModuleConstant, PackageConstant, ClassConstant, PropertyConstant, or MethodConstant.
     */
    private VersionConstant m_constVer;

    /**
     * True if the version has to match exactly.
     */
    private boolean m_fExactVer;
    }

package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;

import org.xvm.asm.Version;
import org.xvm.util.Handy;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Evaluates if a specified VM structure will be available in the container based on the version of
 * a particular module other than the module within which this condition occurs.
 */
public class VersionMatchesCondition
        extends ConditionalConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a VersionMatchesCondition.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constModule  the Module to test the version of
     * @param constVer     the version of the Module to test for
     */
    public VersionMatchesCondition(ConstantPool pool, ModuleConstant constModule, VersionConstant constVer)
        {
        super(pool);
        m_constStruct = constModule;
        m_constVer    = constVer;
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
    public VersionMatchesCondition(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iModule = readMagnitude(in);
        m_iVer    = readIndex(in);
        }

    @Override
    protected void resolveConstants()
        {
        ConstantPool pool = getConstantPool();

        m_constStruct = (ModuleConstant)  pool.getConstant(m_iModule);
        m_constVer    = (VersionConstant) pool.getConstant(m_iVer);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Obtain the module constant that identifies the module that is being tested for a specific
     * version.
     *
     * @return the ModuleConstant
     */
    public ModuleConstant getModuleConstant()
        {
        return m_constStruct;
        }

    /**
     * Obtain the version that the module must match.
     *
     * @return the VersionConstant
     */
    public VersionConstant getVersionConstant()
        {
        return m_constVer;
        }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    public boolean evaluate(LinkerContext ctx)
        {
        return ctx.isVersionMatch(m_constStruct, m_constVer);
        }

    @Override
    public boolean isTerminal()
        {
        return true;
        }

    @Override
    public Relation calcRelation(ConditionalConstant that)
        {
        if (that instanceof VersionMatchesCondition)
            {
            // too bad Java doesn't respect the "if instanceof" we just suffered through
            VersionMatchesCondition thaT = (VersionMatchesCondition) that;
            if (this.m_constStruct.equals(thaT.m_constStruct))
                {
                Version verThis = this.m_constVer.getVersion();
                Version verThat = thaT.m_constVer.getVersion();

                if (verThis.isSameAs(verThat))
                    {
                    return Relation.EQUIV;
                    }

                if (verThis.isSubstitutableFor(verThat))
                    {
                    return Relation.IMPLIES;
                    }

                if (verThat.isSubstitutableFor(verThis))
                    {
                    return Relation.IMPLIED;
                    }

                return Relation.MUTEX;
                }
            }
        else if (that instanceof PresentCondition)
            {
            // these two are potentially related, but instead of duplicating the code, let's keep
            // the logic over on PresentCondition
            return that.calcRelation(this).reverse();
            }

        return Relation.INDEP;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionVersionMatches;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_constStruct.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constStruct);
        visitor.accept(m_constVer);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof VersionMatchesCondition))
            {
            return -1;
            }
        VersionMatchesCondition constThat = (VersionMatchesCondition) that;
        int nResult = m_constStruct.compareTo(constThat.m_constStruct);
        if (nResult == 0)
            {
            nResult = m_constVer.compareTo(constThat.m_constVer);
            }

        return nResult;
        }

    @Override
    public String getValueString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("versionMatches(")
          .append(m_constStruct)
          .append(", ")
          .append(m_constVer.getValueString())
          .append(')');

        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constStruct = (ModuleConstant)  pool.register(m_constStruct);
        m_constVer    = (VersionConstant) pool.register(m_constVer);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constStruct.getPosition());
        writePackedLong(out, m_constVer.getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constStruct, Hash.of(m_constVer));
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the module constant.
     */
    private transient int m_iModule;

    /**
     * During disassembly, this holds the index of the version constant.
     */
    private transient int m_iVer;

    /**
     * The ModuleConstant for the module that the condition is testing the version of.
     */
    private ModuleConstant m_constStruct;

    /**
     * The VersionConstant specifying the version of the specified module to test for.
     */
    private VersionConstant m_constVer;
    }

package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;
import org.xvm.asm.Version;

import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Evaluates if the module is of a specified version. The VersionedCondition applies to (tests for)
 * the version of the current module only; in other words, the VersionConsant is used to
 * conditionally include or exclude VMStructures within <b>this</b> module based on the version of
 * <b>this</b> module. This allows multiple versions of a module to be colocated within a single
 * FileStructure, for example.
 * <p/>
 * To evaluate if another module (or component thereof) is of a specified version, a {@link
 * PresentCondition} is used.
 */
public class VersionedCondition
        extends ConditionalConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a VersionedCondition.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param constVer   the version of this Module to evaluate
     */
    public VersionedCondition(ConstantPool pool, VersionConstant constVer)
        {
        super(pool);
        m_constVer  = constVer;
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
    public VersionedCondition(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iVer = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        m_constVer = (VersionConstant) getConstantPool().getConstant(m_iVer);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Obtain the version of the current module that the condition is predicated on.
     *
     * @return the version that is required
     */
    public Version getVersion()
        {
        return m_constVer.getVersion();
        }

    /**
     * Obtain the version of the current module that the condition is predicated on.
     *
     * @return the constant for the version that is required
     */
    public VersionConstant getVersionConstant()
        {
        return m_constVer;
        }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    public boolean evaluate(LinkerContext ctx)
        {
        return ctx.isVersion(m_constVer);
        }

    @Override
    public boolean isTerminal()
        {
        return true;
        }

    @Override
    public Set<Version> versions()
        {
        return Collections.singleton(m_constVer.getVersion());
        }

    @Override
    public ConditionalConstant addVersion(Version ver)
        {
        if (ver.equals(getVersion()))
            {
            return this;
            }

        ConstantPool pool = getConstantPool();
        return new AnyCondition(pool, this, pool.ensureVersionedCondition(ver));
        }

    @Override
    public ConditionalConstant removeVersion(Version ver)
        {
        return ver.equals(getVersion())
                ? null
                : this;
        }

    @Override
    public Relation calcRelation(ConditionalConstant that)
        {
        if (that instanceof VersionedCondition)
            {
            Version verThis = this.m_constVer.getVersion();
            Version verThat = ((VersionedCondition) that).m_constVer.getVersion();

            return verThis.isSameAs(verThat)
                    ? Relation.EQUIV
                    : Relation.MUTEX;
            }

        return Relation.INDEP;
        }

    @Override
    protected boolean isTerminalInfluenceFinessable(boolean fInNot,
            Set<ConditionalConstant> setSimple, Set<ConditionalConstant> setComplex)
        {
        // versions are only finessed when they are in simple ANDs/ORs; no attempt is made to
        // finesse them under a NOT
        return !fInNot && super.isTerminalInfluenceFinessable(fInNot, setSimple, setComplex);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionVersioned;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constVer);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof VersionedCondition constThat))
            {
            return -1;
            }
        return m_constVer.compareTo(constThat.m_constVer);
        }

    @Override
    public String getValueString()
        {
        return "isVersion(" + m_constVer.getValueString() + ')';
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

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
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constVer);
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
    }
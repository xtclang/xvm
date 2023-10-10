package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;

import org.xvm.util.Hash;

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
     * Construct a PresentCondition.
     *
     * @param pool           the ConstantPool that will contain this Constant
     * @param constVMStruct  the Module, Package, Class, Property or Method
     */
    public PresentCondition(ConstantPool pool, Constant constVMStruct)
        {
        super(pool);
        assert constVMStruct instanceof IdentityConstant ||
               constVMStruct instanceof UnresolvedNameConstant;
        m_constStruct = constVMStruct;
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
    public PresentCondition(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iStruct = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        m_constStruct = (IdentityConstant) getConstantPool().getConstant(m_iStruct);
        assert     m_constStruct instanceof ModuleConstant
                || m_constStruct instanceof PackageConstant
                || m_constStruct instanceof ClassConstant
                || m_constStruct instanceof PropertyConstant
                || m_constStruct instanceof MethodConstant;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Obtain the constant of the XVM Structure that this conditional constant represents the
     * conditional presence of.
     *
     * @return the constant representing the XVM Structure to be tested for
     */
    public IdentityConstant getPresentConstant()
        {
        return (IdentityConstant) m_constStruct;
        }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    public boolean evaluate(LinkerContext ctx)
        {
        return ctx.isPresent((IdentityConstant) m_constStruct);
        }

    @Override
    public boolean isTerminal()
        {
        return true;
        }

    @Override
    public Relation calcRelation(ConditionalConstant constant)
        {
        if (constant instanceof PresentCondition that)
            {
            IdentityConstant constThis = (IdentityConstant) this.m_constStruct;
            IdentityConstant constThat = (IdentityConstant) that.m_constStruct;
            if (constThis.equals(constThat))
                {
                // they're testing the same thing
                return Relation.EQUIV;
                }

            if (constThis.getModuleConstant().equals(constThat.getModuleConstant()))
                {
                // they're testing two things from the same module, so they could be related
                List<IdentityConstant> listThis = constThis.getPath();
                List<IdentityConstant> listThat = constThat.getPath();
                int cThis = listThis.size();
                int cThat = listThat.size();
                for (int i = 0, c = Math.min(cThis, cThat); i < c; ++i)
                    {
                    if (!listThis.get(i).equals(listThat.get(i)))
                        {
                        return Relation.INDEP;
                        }
                    }

                // we already checked if they are equal, and they weren't, so they had better have
                // different length paths
                assert cThis != cThat;

                return cThis > cThat
                    ? Relation.IMPLIES
                    : Relation.IMPLIED;
                }
            }
        else if (constant instanceof VersionMatchesCondition thatCond &&
                 this.m_constStruct instanceof ModuleConstant constThisModule)
            {
            ModuleConstant constThatModule = thatCond.getModuleConstant();
            if (constThisModule.equals(constThatModule))
                {
                // so "this" is checking that the module is present at all, and "that" is checking
                // the version of the same module, so they are related, because if "that" version
                // passes, then it's implied that "this" presence check will always pass
                return Relation.IMPLIED;
                }
            }

        return Relation.INDEP;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionPresent;
        }

    @Override
    public boolean containsUnresolved()
        {
        return !isHashCached() && m_constStruct.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constStruct);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        if (!(obj instanceof PresentCondition that))
            {
            return -1;
            }
        return m_constStruct.compareTo(that.m_constStruct);
        }

    @Override
    public String getValueString()
        {
        return "isPresent(" + m_constStruct.getValueString() + ')';
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constStruct = (IdentityConstant) pool.register(m_constStruct);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constStruct.getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constStruct);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the structure id.
     */
    private transient int m_iStruct;

    /**
     * A ModuleConstant, PackageConstant, ClassConstant, PropertyConstant, or MethodConstant.
     */
    private Constant m_constStruct;
    }
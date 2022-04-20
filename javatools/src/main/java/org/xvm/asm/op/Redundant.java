package org.xvm.asm.op;


import org.xvm.asm.Op;


/**
 * A "label" of sorts that allows a previously real op to still know its relative location in the
 * code, yet not emit any actual assembly.
 */
public class Redundant
        extends Op.Prefix
    {
    /**
     * Construct an op that hides a previously-determined-to-be-redundant op, so that it can act as
     * if it's just a label.
     */
    public Redundant(Op op)
        {
        m_opDiscarded = op;
        }

    @Override
    public void initInfo(int nAddress, int nDepth, int nGuardDepth, int nGuardAllDepth)
        {
        m_opDiscarded.initInfo(nAddress, nDepth, nGuardDepth, nGuardAllDepth);
        super.initInfo(nAddress, nDepth, nGuardDepth, nGuardAllDepth);
        }

    @Override
    public void markRedundant()
        {
        m_opDiscarded.markRedundant();
        super.markRedundant();
        }

    @Override
    public boolean contains(Op that)
        {
        return that == m_opDiscarded || super.contains(that);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder("_: ");

        Op op = getNextOp();
        if (op != null)
            {
            sb.append(op);
            }

        return sb.toString();
        }

    /**
     * The redundant op.
     */
    private final Op m_opDiscarded;
    }
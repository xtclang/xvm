package org.xvm.asm;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.xvm.asm.op.Label;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for JUMP, GUARD_E, CATCH_E and GUARDALL op-codes.
 */
public abstract class OpJump
        extends Op {
    /**
     * Construct an op.
     *
     * @param op the op to jump to
     */
    protected OpJump(Op op) {
        m_opDest = op;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpJump(DataInput in, Constant[] aconst)
            throws IOException {
        m_ofJmp = readPackedInt(in);
        assert m_ofJmp > 0;
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        out.writeByte(getOpCode());
        writePackedLong(out, m_ofJmp);
    }

    @Override
    public void resolveAddresses(Op[] aop) {
        if (m_opDest == null) {
            m_ofJmp  = adjustRelativeAddress(aop, m_ofJmp);
            m_opDest = aop[getAddress() + m_ofJmp];
        } else {
            m_ofJmp = calcRelativeAddress(m_opDest);
        }
        assert m_ofJmp > 0 || !isReachable() && m_ofJmp == 0;
        m_cExits = calcExits(m_opDest);
    }

    @Override
    public void assertReadyForRuntime() {
        if (m_opDest == null) {
            throw new IllegalStateException(toName(getOpCode())
                    + " reached runtime before jump destination resolution");
        }
    }

    @Override
    public int process(Frame frame, int iPC) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void markReachable(Op[] aop) {
        super.markReachable(aop);

        m_opDest = findDestinationOp(aop, m_ofJmp);
        m_ofJmp  = calcRelativeAddress(m_opDest);
        assert m_ofJmp > 0;
    }

    @Override
    public boolean branches(Op[] aop, List<Integer> list) {
        list.add(m_ofJmp);
        return true;
    }

    @Override
    public boolean advances() {
        return false;
    }

    /**
     * @return the number of instructions to jump (may be negative)
     */
    public int getRelativeAddress() {
        return m_ofJmp;
    }

    /**
     * @return a String to use for debugging to denote the destination of the jump
     */
    protected String getLabelDesc() {
        return getLabelDesc(m_opDest, m_ofJmp);
    }

    public static String getLabelDesc(Op opDest, int ofJmp) {
        if (opDest instanceof Label) {
            return ((Label) opDest).getName();
        } else if (ofJmp != 0) {
            return (ofJmp > 0 ? "+" : "") + ofJmp;
        } else if (opDest != null) {
            // The DESTINATION'S NAME, not the destination. This used to render `"-> " + opDest`,
            // which calls the target's toString() - and when the target is itself a jump, that
            // renders ITS target, and so on. A jump whose chain loops back, which is what a loop
            // compiles to, therefore overflowed the stack on toString().
            //
            // Only reachable once ops are READ back from a compiled module: while compiling, a
            // destination is a Label and takes the first branch. So nothing hit it until something
            // disassembled a .xtc - and then it is any debugger, log line or diagnostic that renders
            // an op, which is the worst place to find a StackOverflowError.
            return "-> " + toName(opDest.getOpCode());
        } else {
            return "???";
        }
    }

    @Override
    public String toString() {
        return toName(getOpCode()) + ' ' + getLabelDesc();
    }

    @Override
    public Optional<List<OpField>> fields() {
        // an unconditional jump encodes exactly one thing: where it goes
        return Optional.of(List.of(OpField.branch("target", getRelativeAddress())));
    }


    // ----- fields --------------------------------------------------------------------------------

    protected int m_ofJmp;

    private Op m_opDest;

    // number of exits to simulate on the jump
    protected transient int m_cExits;
}

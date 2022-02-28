package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * MOV_THIS #, lvalue-dest          ; # (an inline unsigned byte) specifies the count of this-to-outer-this
 *                                  ; steps (0=this, 1=ImmediatelyOuter.this, etc.)
 * MOV_THIS_A #, lvalue-dest, A_*   ; same as above with an additional access modifier
 *                                    (A_TARGET, A_PUBLIC, A_PROTECTED, A_PRIVATE, A_STRUCT)
 */
public class MoveThis
        extends Op
    {
    /**
     * Construct a MOV_THIS op for the passed arguments.
     *
     * @param cSteps   the count of this-to-outer-this steps (0=this, 1=ImmediatelyOuter.this, etc.)
     * @param argDest  the destination Argument
     */
    public MoveThis(int cSteps, Argument argDest)
        {
        assert cSteps >= 0;

        m_cSteps  = cSteps;
        m_argTo   = argDest;
        m_nAccess = A_PUBLIC;
        }

    /**
     * Construct a MOV_THIS_A op for the passed arguments.
     *
     * @param cSteps   the count of this-to-outer-this steps (0=this, 1=ImmediatelyOuter.this, etc.)
     * @param argDest  the destination Argument
     * @param access   the access modifier
     */
    public MoveThis(int cSteps, Argument argDest, Access access)
        {
        assert cSteps >= 0 && access != null;

        m_cSteps = cSteps;
        m_argTo  = argDest;

        switch (access)
            {
            case PUBLIC:
                m_nAccess = A_PUBLIC;
                break;
            case PROTECTED:
                m_nAccess = A_PROTECTED;
                break;
            case PRIVATE:
                m_nAccess = A_PRIVATE;
                break;
            case STRUCT:
                m_nAccess = A_STRUCT;
                break;
            }
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     * @param nOp     the op-code (OP_MOV_THIS or OP_MOV_THIS_A)
     */
    public MoveThis(DataInput in, Constant[] aconst, int nOp)
            throws IOException
        {
        m_cSteps   = in.readUnsignedByte();
        m_nToValue = readPackedInt(in);

        if (nOp == OP_MOV_THIS_A)
            {
            m_nAccess = readPackedInt(in);
            }
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argTo != null)
            {
            m_nToValue = encodeArgument(m_argTo, registry);
            }

        out.writeByte(m_cSteps);
        writePackedLong(out, m_nToValue);

        if (m_nAccess != 0)
            {
            writePackedLong(out, m_nAccess);
            }
        }

    @Override
    public int getOpCode()
        {
        return m_nAccess == 0 ? OP_MOV_THIS : OP_MOV_THIS_A;
        }

    @Override
    public void resetSimulation()
        {
        resetRegister(m_argTo);
        }

    @Override
    public void simulate(Scope scope)
        {
        checkNextRegister(scope, m_argTo, m_nToValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argTo = registerArgument(m_argTo, registry);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hOuter = frame.getThis();
            for (int c = m_cSteps; c > 0; c--)
                {
                hOuter = ((GenericHandle) hOuter).getField(frame, GenericHandle.OUTER);
                }

            if (m_nAccess != 0)
                {
                Access access = switch (m_nAccess)
                    {
                    case A_PUBLIC    -> Access.PUBLIC;
                    case A_PROTECTED -> Access.PROTECTED;
                    case A_PRIVATE   -> Access.PRIVATE;
                    case A_STRUCT    -> Access.STRUCT;
                    default          -> throw new IllegalStateException();
                    };
                hOuter = hOuter.ensureAccess(access);
                }

            int nToValue = m_nToValue;
            if (frame.isNextRegister(nToValue))
                {
                frame.introduceResolvedVar(nToValue, hOuter.getType());
                }

            return frame.assignValue(nToValue, hOuter);
            }
        catch (ClassCastException | NullPointerException e)
            {
            return frame.raiseException("Unknown outer at " +
                    frame.getThis().getType().getValueString());
            }
        }

    @Override
    public String toString()
        {
        return super.toString()
                + " #" + m_cSteps
                + ", " + Argument.toIdString(m_argTo, m_nToValue)
                + (m_nAccess == 0 ? "" : " " + Register.getIdString(m_nAccess)) ;
        }

    protected int m_cSteps;
    protected int m_nToValue;
    protected int m_nAccess;

    private Argument m_argTo;
    }

package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Label;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for conditional jump (JMP_*) op-codes.
 */
public abstract class OpCondJump
        extends Op
    {
    /**
     * Construct a unary conditional JMP_ op.
     *
     * @param arg  a value Argument
     * @param op   the op to jump to
     */
    protected OpCondJump(Argument arg, Op op)
        {
        assert !isBinaryOp();

        m_argVal = arg;
        m_opDest = op;
        }

    /**
     * Construct a binary conditional JMP_ op.
     *
     * @param arg   a value Argument
     * @param arg2  a second value Argument
     * @param op    the op to jump to
     */
    protected OpCondJump(Argument arg, Argument arg2, Op op)
        {
        assert isBinaryOp();

        m_argVal  = arg;
        m_argVal2 = arg2;
        m_opDest  = op;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpCondJump(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nArg = readPackedInt(in);
        if (isBinaryOp())
            {
            m_nArg2 = readPackedInt(in);
            }
        m_ofJmp = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argVal != null)
            {
            m_nArg = encodeArgument(m_argVal, registry);
            if (isBinaryOp())
                {
                m_nArg2 = encodeArgument(m_argVal2, registry);
                }
            }

        out.writeByte(getOpCode());

        writePackedLong(out, m_nArg);
        writePackedLong(out, m_ofJmp);
        }

    @Override
    public void resolveAddress(MethodStructure.Code code, int iPC)
        {
        if (m_opDest != null && m_ofJmp == 0)
            {
            m_ofJmp = resolveAddress(code, iPC, m_opDest);
            }
        }

    /**
     * A "virtual constant" indicating whether or not this op is a binary one (has two arguments).
     *
     * @return true iff the op has two arguments
     */
    protected boolean isBinaryOp()
        {
        return false;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return isBinaryOp() ? processBinaryOp(frame, iPC) : processUnaryOp(frame, iPC);
        }

    protected int processUnaryOp(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(m_nArg);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            if (isDeferred(hValue))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hValue};
                Frame.Continuation stepNext = frameCaller ->
                    completeUnaryOp(frame, iPC, ahValue[0]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return completeUnaryOp(frame, iPC, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int processBinaryOp(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue1 = frame.getArgument(m_nArg);
            ObjectHandle hValue2 = frame.getArgument(m_nArg2);
            if (hValue1 == null || hValue2 == null)
                {
                return R_REPEAT;
                }

            TypeConstant type1;
            TypeConstant type2;
            boolean fAnyProp = false;

            if (isDeferred(hValue1))
                {
                type1 = frame.getLocalType(m_nArg);
                fAnyProp = true;
                }
            else
                {
                type1 = frame.getArgumentType(m_nArg);
                }

            if (isDeferred(hValue2))
                {
                type2 = frame.getLocalType(m_nArg2);
                fAnyProp = true;
                }
            else
                {
                type2 = frame.getArgumentType(m_nArg2);
                }

            if (type1 != type2)
                {
                // this shouldn't have compiled
                throw new IllegalStateException();
                }

            if (fAnyProp)
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hValue1, hValue2};
                Frame.Continuation stepNext = frameCaller ->
                    completeBinaryOp(frame, iPC, type1, ahValue[0], ahValue[1]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return completeBinaryOp(frame, iPC, type1, hValue1, hValue2);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    /**
     * A completion of a unary op; must me overridden by all binary ops.
     */
    protected int completeUnaryOp(Frame frame, int iPC, ObjectHandle hValue)
        {
        throw new UnsupportedOperationException();
        }

    /**
     * A completion of a binary op; must me overridden by all binary ops.
     */
    protected int completeBinaryOp(Frame frame, int iPC, TypeConstant type,
                                   ObjectHandle hValue1, ObjectHandle hValue2)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argVal = registerArgument(m_argVal, registry);

        if (isBinaryOp())
            {
            m_argVal2 = registerArgument(m_argVal2, registry);
            }
        }

    /**
     * @return a String that identifies the argument, for debugging purposes
     */
    protected String getArgDesc()
        {
        return Argument.toIdString(m_argVal, m_nArg);
        }

    /**
     * @return a String that identifies the second argument, for debugging purposes
     */
    protected String getArg2Desc()
        {
        assert isBinaryOp();
        return Argument.toIdString(m_argVal2, m_nArg2);
        }

    /**
     * @return a String to use for debugging to denote the destination of the jump
     */
    protected String getLabelDesc()
        {
        if (m_opDest instanceof Label)
            {
            return ((Label) m_opDest).getName();
            }
        else if (m_ofJmp != 0)
            {
            return (m_ofJmp > 0 ? "+" : "") + m_ofJmp;
            }
        else if (m_opDest != null)
            {
            return "-> " + m_opDest;
            }
        else
            {
            return "???";
            }
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(toName(getOpCode()))
          .append(' ')
          .append(getArgDesc());

        if (isBinaryOp())
            {
            sb.append(' ')
              .append(getArg2Desc());
            }

        sb.append(' ')
          .append(getLabelDesc());

        return sb.toString();
        }

    protected int m_nArg;
    protected int m_nArg2;
    protected int m_ofJmp;

    private Argument m_argVal;
    private Argument m_argVal2;
    private Op       m_opDest;
    }

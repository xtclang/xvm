package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpCondJump;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;


/**
 * JMP_TYPE rvalue, rvalue-type, addr ; jump if type of the value is "instanceof" specified type
 */
public class JumpType
        extends OpCondJump
    {
    /**
     * Construct a JMP_TYPE op.
     *
     * @param arg1  the first argument to compare
     * @param arg2  the second argument to compare
     * @param op    the op to conditionally jump to
     */
    public JumpType(Argument arg1, Argument arg2, Op op)
        {
        super(arg1, arg2, op);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpType(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_TYPE;
        }

    @Override
    protected boolean isBinaryOp()
        {
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // while this Op has two arguments and is marked as a BinaryOp, the processing
        // is identical to the UnaryOp
        return processUnaryOp(frame, iPC);
        }

    @Override
    protected int completeUnaryOp(Frame frame, int iPC, ObjectHandle hValue)
        {
        TypeConstant typeTest;
        if (m_nArg2 <= CONSTANT_OFFSET)
            {
            typeTest = frame.resolveType(m_nArg2);
            }
        else
            {
            try
                {
                TypeHandle hType = (TypeHandle) frame.getArgument(m_nArg2);
                typeTest = hType.getUnsafeDataType();
                }
            catch (ClassCastException e)
                {
                // should not happen; treat as "no match"
                return iPC + 1;
                }
            catch (ExceptionHandle.WrapperException e)
                {
                return frame.raiseException(e);
                }
            }

        return hValue.getUnsafeType().isA(typeTest)
                ? jump(frame, iPC + m_ofJmp, m_cExits)
                : iPC + 1;
        }
    }

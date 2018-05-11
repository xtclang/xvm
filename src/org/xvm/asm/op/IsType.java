package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpTest;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xBoolean;


/**
 * IS_TYPE  rvalue, rvalue-type, lvalue-return ; T instanceof Type -> Boolean
 */
public class IsType
        extends OpTest
    {
    /**
     * Construct an IS_TYPE op based on the specified arguments.
     *
     * @param arg1       the value Argument
     * @param arg2       the type Argument
     * @param argReturn  the location to store the Boolean result
     */
    public IsType(Argument arg1, Argument arg2, Argument argReturn)
        {
        super(arg1, arg2, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IsType(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_TYPE;
        }

    @Override
    protected boolean isBinaryOp()
        {
        // while technically this op is not binary, we could re-use all the base logic
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // while this Op has two arguments and is marked as a BinaryOp, the processing
        // is identical to the UnaryOp
        return processUnaryOp(frame);
        }

    @Override
    protected int completeUnaryOp(Frame frame, ObjectHandle hValue)
        {
        TypeConstant type     = hValue.getType();
        TypeConstant typeTest = frame.resolveType(m_nValue2);

        return frame.assignValue(m_nRetValue, xBoolean.makeHandle(type.isA(typeTest)));
        }
    }

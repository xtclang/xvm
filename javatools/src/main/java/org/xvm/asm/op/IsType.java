package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpTest;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;


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
        TypeConstant typeTest;
        if (m_nValue2 <= CONSTANT_OFFSET)
            {
            typeTest = frame.resolveType(m_nValue2);
            }
        else
            {
            try
                {
                TypeHandle hType = (TypeHandle) frame.getArgument(m_nValue2);
                typeTest = hType.getUnsafeDataType();
                }
            catch (ClassCastException e)
                {
                // should not happen
                return frame.assignValue(m_nRetValue, xBoolean.FALSE);
                }
            catch (ExceptionHandle.WrapperException e)
                {
                return frame.raiseException(e);
                }
            }

        return frame.assignValue(m_nRetValue,
                xBoolean.makeHandle(hValue.getUnsafeType().isA(typeTest)));
        }
    }

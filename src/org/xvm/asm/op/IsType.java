package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpTest;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;


/**
 * IS_TYPE  rvalue, rvalue-type, lvalue-return ; T instanceof Type -> Boolean
 */
public class IsType
        extends OpTest
    {
    /**
     * Construct an IS_TYPE op.
     *
     * @param nValue  the value to test
     * @param nType   the type to test for
     * @param nRet    the location to store the Boolean result
     *
     * @deprecated
     */
    public IsType(int nValue, int nType, int nRet)
        {
        super(null, null, null);

        m_nValue1   = nValue;
        m_nValue2   = nType;
        m_nRetValue = nRet;
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
    protected int completeBinaryOp(Frame frame, TypeComposition clz,
                                   ObjectHandle hValue1, ObjectHandle hValue2)
        {
        // TODO: Note that hValue2 represents the type
        return R_NEXT;
        }
    }

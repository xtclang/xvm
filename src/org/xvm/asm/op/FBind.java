package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.Function.FunctionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * FBIND rvalue-fn, #params:(param-index, rvalue-param), lvalue-fn-result
 */
public class FBind
        extends OpInvocable
    {
    /**
     * Construct an FBIND op.
     *
     * @param nFunction    identifies the function to bind
     * @param nParamIx     identifies the parameter(s) to bind
     * @param nParamValue  identifies the values to use corresponding to those parameters
     * @param nRet         identifies where to place the bound function
     */
    public FBind(int nFunction, int[] nParamIx, int[] nParamValue, int nRet)
        {
        f_nFunctionValue = nFunction;
        f_anParamIx      = nParamIx;
        f_anParamValue   = nParamValue;
        f_nResultValue   = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public FBind(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nFunctionValue = readPackedInt(in);

        int c = readPackedInt(in);
        f_anParamIx = new int[c];
        f_anParamValue = new int[c];
        for (int i = 0; i < c; i++)
            {
            f_anParamIx[i]    = readPackedInt(in);
            f_anParamValue[i] = readPackedInt(in);
            }
        f_nResultValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
    throws IOException
        {
        out.writeByte(OP_FBIND);
        writePackedLong(out, f_nFunctionValue);

        int c = f_anParamIx.length;
        writePackedLong(out, c);
        for (int i = 0; i < c; i++)
            {
            writePackedLong(out, f_anParamIx[i]);
            writePackedLong(out, f_anParamValue[i]);
            }
        writePackedLong(out, f_nResultValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_FBIND;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            for (int i = 0, c = f_anParamIx.length; i < c; i++)
                {
                ObjectHandle hArg = frame.getArgument(f_anParamValue[i]);
                if (hArg == null)
                    {
                    return R_REPEAT;
                    }
                hFunction = hFunction.bind(f_anParamIx[i], hArg);
                }

            return frame.assignValue(f_nResultValue, hFunction);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int   f_nFunctionValue;
    private final int[] f_anParamIx;
    private final int[] f_anParamValue;
    private final int   f_nResultValue;
    }

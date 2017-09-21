package org.xvm.asm.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.asm.OpInvocable;

import org.xvm.proto.template.Function.FunctionHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * FBIND rvalue-fn, #params:(param-index, rvalue-param), lvalue-fn-result
 *
 * @author gg 2017.03.08
 */
public class FBind extends OpInvocable
    {
    private final int f_nFunctionValue;
    private final int[] f_anParamIx;
    private final int[] f_anParamValue;
    private final int f_nResultValue;

    public FBind(int nFunction, int[] nParamIx, int[] nParamValue, int nRet)
        {
        f_nFunctionValue = nFunction;
        f_anParamIx = nParamIx;
        f_anParamValue = nParamValue;
        f_nResultValue = nRet;
        }

    public FBind(DataInput in)
            throws IOException
        {
        f_nFunctionValue = in.readInt();

        int c = in.readUnsignedByte();
        f_anParamIx = new int[c];
        f_anParamValue = new int[c];
        for (int i = 0; i < c; i++)
            {
            f_anParamIx[i] = in.readInt();
            f_anParamValue[i] = in.readInt();
            }
        f_nResultValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_MBIND);
        out.writeInt(f_nFunctionValue);

        int c = f_anParamIx.length;
        out.write(c);
        for (int i = 0; i < c; i++)
            {
            out.writeInt(f_anParamIx[i]);
            out.writeInt(f_anParamValue[i]);
            }
        out.writeInt(f_nResultValue);
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
    }

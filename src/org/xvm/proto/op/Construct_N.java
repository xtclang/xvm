package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * CONSTR_N CONST-CONSTRUCT, #params:(rvalue)
 *
 * @author gg 2017.03.08
 */
public class Construct_N extends OpCallable
    {
    private final int f_nConstructId;
    private final int[] f_anArgValue;

    public Construct_N(int nConstructorId, int[] anArg)
        {
        f_nConstructId = nConstructorId;
        f_anArgValue = anArg;
        }

    public Construct_N(DataInput in)
            throws IOException
        {
        f_nConstructId = in.readInt();
        f_anArgValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_CONSTR_N);
        out.writeInt(f_nConstructId);
        writeIntArray(out, f_anArgValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            MethodStructure constructor = getMethodStructure(frame, f_nConstructId);

            ObjectHandle hStruct = frame.getThis();
            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, frame.f_adapter.getVarCount(constructor));
            if (ahVar == null)
                {
                return R_REPEAT;
                }
            frame.chainFinalizer(hStruct.f_clazz.f_template.makeFinalizer(constructor, hStruct, ahVar));

            return frame.call1(constructor, hStruct, ahVar, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
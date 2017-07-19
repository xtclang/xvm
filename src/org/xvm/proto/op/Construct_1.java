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
 * CONSTR_1 CONST-CONSTRUCT, rvalue
 *
 * @author gg 2017.03.08
 */
public class Construct_1 extends OpCallable
    {
    private final int f_nConstructId;
    private final int f_nArgValue;

    public Construct_1(int nConstructorId, int anArg)
        {
        f_nConstructId = nConstructorId;
        f_nArgValue = anArg;
        }

    public Construct_1(DataInput in)
            throws IOException
        {
        f_nConstructId = in.readInt();
        f_nArgValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_CONSTR_1);
        out.writeInt(f_nConstructId);
        out.writeInt(f_nArgValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            MethodStructure constructor = getMethodStructure(frame, f_nConstructId);

            ObjectHandle hStruct = frame.getThis();
            ObjectHandle hArg = frame.getArgument(f_nArgValue);
            if (hArg == null)
                {
                return R_REPEAT;
                }

            ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(constructor)];
            ahVar[0] = hArg;

            frame.chainFinalizer(hStruct.f_clazz.f_template.makeFinalizer(constructor, hStruct, ahVar));

            return frame.call1(constructor, hStruct, ahVar, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
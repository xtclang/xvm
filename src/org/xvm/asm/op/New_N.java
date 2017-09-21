package org.xvm.asm.op;

import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IdentityConstant;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.asm.OpCallable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * NEW_N CONST-CONSTRUCT, #params:(rvalue), lvalue-return
 *
 * @author gg 2017.03.08
 */
public class New_N extends OpCallable
    {
    private final int f_nConstructId;
    private final int[] f_anArgValue;
    private final int f_nRetValue;

    public New_N(int nConstructorId, int[] anArg, int nRet)
        {
        f_nConstructId = nConstructorId;
        f_anArgValue = anArg;
        f_nRetValue = nRet;
        }

    public New_N(DataInput in)
            throws IOException
        {
        f_nConstructId = in.readInt();
        f_anArgValue = readIntArray(in);
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_NEW_N);
        out.writeInt(f_nConstructId);
        writeIntArray(out, f_anArgValue);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        MethodStructure constructor = getMethodStructure(frame, f_nConstructId);
        IdentityConstant constClass = constructor.getParent().getParent().getIdentityConstant();

        try
            {
            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue,
                    constructor.getMaxVars());
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

            return template.construct(frame, constructor,
                    template.f_clazzCanonical, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
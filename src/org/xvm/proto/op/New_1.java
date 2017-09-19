package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.IdentityConstant;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * NEW_1 CONST-CONSTRUCT, rvalue-param, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class New_1 extends OpCallable
    {
    private final int f_nConstructId;
    private final int f_nArgValue;
    private final int f_nRetValue;

    public New_1(int nConstructorId, int nArg, int nRet)
        {
        f_nConstructId = nConstructorId;
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    public New_1(DataInput in)
            throws IOException
        {
        f_nConstructId = in.readInt();
        f_nArgValue = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_NEW_1);
        out.writeInt(f_nConstructId);
        out.writeInt(f_nArgValue);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        MethodStructure constructor = getMethodStructure(frame, f_nConstructId);
        IdentityConstant constClass = constructor.getParent().getParent().getIdentityConstant();

        try
            {
            ObjectHandle[] ahVar = frame.getArguments(
                    new int[]{f_nArgValue}, constructor.getVarCount());
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

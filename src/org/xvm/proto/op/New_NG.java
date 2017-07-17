package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IdentityConstant;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.xClass.ClassHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 *  NEW_NG CONST-CONSTRUCT, rvalue-type, #params:(rvalue), lvalue-return
 *
 * @author gg 2017.03.08
 */
public class New_NG extends OpCallable
    {
    private final int f_nConstructId;
    private final int f_nTypeValue;
    private final int[] f_anArgValue;
    private final int f_nRetValue;

    public New_NG(int nConstructorId, int nType, int[] anArg, int nRet)
        {
        f_nConstructId = nConstructorId;
        f_nTypeValue = nType;
        f_anArgValue = anArg;
        f_nRetValue = nRet;
        }

    public New_NG(DataInput in)
            throws IOException
        {
        f_nConstructId = in.readInt();
        f_nTypeValue = in.readInt();
        f_anArgValue = readIntArray(in);
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_NEW_1G);
        out.writeInt(f_nConstructId);
        out.writeInt(f_nTypeValue);
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
            TypeComposition clzTarget;
            if (f_nTypeValue >= 0)
                {
                ClassHandle hClass = (ClassHandle) frame.getArgument(f_nTypeValue);
                if (hClass == null)
                    {
                    return R_REPEAT;
                    }
                clzTarget = hClass.f_clazz;
                }
            else
                {
                clzTarget = frame.f_context.f_types.ensureComposition(-f_nTypeValue);
                }

            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, frame.f_adapter.getVarCount(constructor));
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

            return template.construct(frame, constructor, clzTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }

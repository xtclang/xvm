package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.IdentityConstant;

import org.xvm.proto.ClassTemplate;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeComposition;


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

    @Override
    public int process(Frame frame, int iPC)
        {
        MethodStructure constructor = getMethodStructure(frame, f_nConstructId);
        IdentityConstant constClass = (IdentityConstant) constructor.getParent().getIdentityConstant();
        ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

        ExceptionHandle hException;
        try
            {
            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, frame.f_adapter.getVarCount(constructor), 1);
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            TypeComposition clzTarget = template.f_clazzCanonical;

            return template.construct(frame, constructor, clzTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        if (hException == null)
            {
            return iPC + 1;
            }

        frame.m_hException = hException;
        return R_EXCEPTION;
        }
    }
package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.ClassConstant;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.ConstantPoolAdapter;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeComposition;


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

    @Override
    public int process(Frame frame, int iPC)
        {
        MethodStructure constructor = getMethodStructure(frame, f_nConstructId);
        ClassConstant constClass = (ClassConstant) constructor.getParent().getIdentityConstant();
        ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

        try
            {
            ObjectHandle[] ahVar = frame.getArguments(
                    new int[] {f_nArgValue}, ConstantPoolAdapter.getVarCount(constructor), 1);
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            TypeComposition clzTarget = template.f_clazzCanonical;

            return template.construct(frame, constructor, clzTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }

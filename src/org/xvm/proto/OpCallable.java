package org.xvm.proto;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;

import org.xvm.proto.ObjectHandle.ExceptionHandle;

/**
 * Common base for CALL_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpCallable extends Op
    {
    private int m_nFunctionId;           // cached function id
    private MethodStructure m_function; // cached function

    // get the template for the function constant
    protected MethodStructure getMethodStructure(Frame frame, int nFunctionConstantId)
        {
        assert nFunctionConstantId >= 0;

        if (m_function != null && m_nFunctionId == nFunctionConstantId)
            {
            return m_function;
            }

        MethodConstant constFunction = (MethodConstant)
                frame.f_context.f_pool.getConstant(nFunctionConstantId);

        m_nFunctionId = nFunctionConstantId;
        return m_function = (MethodStructure) constFunction.getComponent();
        }

    // call super() method or "getProperty", placing the return value into the specified frame slot
    protected int callSuper01(Frame frame, int iRet)
        {
        MethodStructure methodSuper = Adapter.getSuper(frame.f_function);

        ObjectHandle hThis = frame.getThis();

        if (methodSuper.getParent() instanceof PropertyStructure)
            {
            PropertyStructure property = (PropertyStructure) methodSuper.getParent();
            IdentityConstant constClass = (IdentityConstant) property.getParent().getIdentityConstant();
            ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

            return template.getFieldValue(frame, hThis, property, iRet);
            }

        ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(methodSuper)];
        ahVar[0] = hThis;

        return frame.call1(methodSuper, hThis, ahVar, iRet);
        }

    // call super() method or "setProperty"
    protected int callSuper10(Frame frame, int nArgValue)
        {
        MethodStructure methodSuper = Adapter.getSuper(frame.f_function);

        ObjectHandle hThis = frame.getThis();
        ObjectHandle hArg;
        try
            {
            hArg = frame.getArgument(nArgValue);
            if (hArg == null)
                {
                return R_REPEAT;
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }

        if (methodSuper.getParent() instanceof PropertyStructure)
            {
            PropertyStructure property = (PropertyStructure) methodSuper.getParent();
            IdentityConstant constClass = (IdentityConstant) property.getParent().getIdentityConstant();
            ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

            ExceptionHandle hException = template.setFieldValue(hThis, property, hArg);
            if (hException != null)
                {
                frame.m_hException = hException;
                return R_EXCEPTION;
                }
            return R_NEXT;
            }

        ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(methodSuper)];
        ahVar[1] = hArg;

        return frame.call1(methodSuper, hThis, ahVar, Frame.RET_UNUSED);
        }

    // call super() methods with multiple arguments and no more than one return
    // (cannot be properties)
    protected int callSuperN1(Frame frame, int[] anArgValue, int iReturn)
        {
        MethodStructure methodSuper = Adapter.getSuper(frame.f_function);

        try
            {
            ObjectHandle[] ahVar = frame.getArguments(anArgValue, frame.f_adapter.getVarCount(methodSuper), 1);
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            return frame.call1(methodSuper, frame.getThis(), ahVar, iReturn);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }

    // call super() methods with multiple arguments and multiple returns
    protected int callSuperNN(Frame frame, int[] anArgValue, int[] aiReturn)
        {
        MethodStructure methodSuper = Adapter.getSuper(frame.f_function);

        try
            {
            ObjectHandle[] ahVar = frame.getArguments(anArgValue, frame.f_adapter.getVarCount(methodSuper), 1);
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            return frame.callN(methodSuper, frame.getThis(), ahVar, aiReturn);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }

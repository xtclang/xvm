package org.xvm.proto;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyAccessTemplate;

/**
 * Common base for CALL_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpCallable extends Op
    {
    private int m_nFunctionId;           // cached function id
    private FunctionTemplate m_function; // cached function

    // get the template for the function constant
    protected FunctionTemplate getFunctionTemplate(Frame frame, int nFunctionConstantId)
        {
        assert nFunctionConstantId >= 0;

        if (m_function != null && m_nFunctionId == nFunctionConstantId)
            {
            return m_function;
            }

        MethodConstant constFunction =
                frame.f_context.f_constantPool.getMethodConstant(nFunctionConstantId);

        String sClass = ConstantPoolAdapter.getClassName(constFunction);

        TypeCompositionTemplate template = frame.f_context.f_types.getTemplate(sClass);

        FunctionTemplate function = template.getFunctionTemplate(constFunction);
        if (function == null)
            {
            System.out.println("Missing function " + constFunction + " on " + template);
            }

        m_nFunctionId = nFunctionConstantId;
        m_function = function;
        return function;
        }

    // call super() method or "getProperty", placing the return value into the specified frame slot
    protected int callSuper01(Frame frame, int iRet)
        {
        MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

        ObjectHandle hThis = frame.getThis();

        if (methodSuper instanceof PropertyAccessTemplate)
            {
            PropertyAccessTemplate propertyAccess = (PropertyAccessTemplate) methodSuper;
            TypeCompositionTemplate template = propertyAccess.getClazzTemplate();

            return template.getFieldValue(frame, hThis, propertyAccess.f_property, iRet);
            }

        ObjectHandle[] ahVar = new ObjectHandle[methodSuper.m_cVars];
        ahVar[0] = hThis;

        return frame.call1(methodSuper, hThis, ahVar, iRet);
        }

    // call super() method or "setProperty"
    protected int callSuper10(Frame frame, int nArgValue)
        {
        MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

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

        if (methodSuper instanceof PropertyAccessTemplate)
            {
            PropertyAccessTemplate propertyAccess = (PropertyAccessTemplate) methodSuper;
            TypeCompositionTemplate template = propertyAccess.getClazzTemplate();

            ExceptionHandle hException = template.setFieldValue(hThis, propertyAccess.f_property, hArg);
            if (hException != null)
                {
                frame.m_hException = hException;
                return R_EXCEPTION;
                }
            return R_NEXT;
            }

        ObjectHandle[] ahVar = new ObjectHandle[methodSuper.m_cVars];
        ahVar[1] = hArg;

        return frame.call1(methodSuper, hThis, ahVar, Frame.R_UNUSED);
        }

    // call super() methods with multiple arguments and no more than one return
    // (cannot be properties)
    protected int callSuperN(Frame frame, int[] anArgValue, int iReturn)
        {
        MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

        try
            {
            ObjectHandle[] ahVar = frame.getArguments(anArgValue, methodSuper.m_cVars, 1);
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
    }

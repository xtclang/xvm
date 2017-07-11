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
    // function caching
    private MethodStructure m_function;   // cached function

    // super call caching
    TypeComposition m_clz;                // cached class
    MethodStructure m_methodSuper;        // cached "super" method
    PropertyStructure m_propertySuper;    // cached "super" property
    ClassTemplate     m_propertyTemplate; // cached "super" property template

    // get the structure for the function constant
    protected MethodStructure getMethodStructure(Frame frame, int nFunctionConstantId)
        {
        assert nFunctionConstantId >= 0;

        // there is no need to cache the id, since it's a constant for a give op-code
        if (m_function != null)
            {
            return m_function;
            }

        MethodConstant constFunction = (MethodConstant)
                frame.f_context.f_pool.getConstant(nFunctionConstantId);

        return m_function = (MethodStructure) constFunction.getComponent();
        }

    // call super() method or "getProperty", placing the return value into the specified frame slot
    protected int callSuper01(Frame frame, int iReturn)
        {
        ObjectHandle hThis = frame.getThis();
        TypeComposition clzThis = hThis.f_clazz;

        if (clzThis == m_clz)
            {
            MethodStructure methodSuper = m_methodSuper;
            if (methodSuper == null)
                {
                return m_propertyTemplate.getFieldValue(frame, hThis, m_propertySuper, iReturn);
                }
            else
                {
                ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(methodSuper)];
                ahVar[0] = hThis;

                return frame.call1(methodSuper, hThis, ahVar, iReturn);
                }
            }

        m_clz = clzThis;

        MethodStructure methodSuper = m_methodSuper = clzThis.resolveSuper(frame.f_function);
        if (methodSuper == null)
            {
            // this can only be a case of the property access
            PropertyStructure property = (PropertyStructure) frame.f_function.getParent().getParent();
            IdentityConstant constClass = property.getParent().getIdentityConstant();
            ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

            m_propertySuper = property;
            m_propertyTemplate = template;

            return template.getFieldValue(frame, hThis, property, iReturn);
            }

        ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(methodSuper)];
        ahVar[0] = hThis;

        return frame.call1(methodSuper, hThis, ahVar, iReturn);
        }

    // call super() method or "setProperty"
    protected int callSuper10(Frame frame, int nArgValue)
        {
        ObjectHandle hThis = frame.getThis();
        TypeComposition clzThis = hThis.f_clazz;

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

        if (clzThis == m_clz)
            {
            MethodStructure methodSuper = m_methodSuper;
            if (methodSuper == null)
                {
                ExceptionHandle hException = m_propertyTemplate.setFieldValue(hThis, m_propertySuper, hArg);
                if (hException != null)
                    {
                    frame.m_hException = hException;
                    return R_EXCEPTION;
                    }
                return R_NEXT;
                }
            else
                {
                ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(methodSuper)];
                ahVar[0] = hThis;

                return frame.call1(methodSuper, hThis, ahVar, Frame.RET_UNUSED);
                }
            }

        m_clz = clzThis;

        MethodStructure methodSuper = m_methodSuper = clzThis.resolveSuper(frame.f_function);
        if (methodSuper == null)
            {
            // this can only be a case of the property access
            PropertyStructure property = (PropertyStructure) frame.f_function.getParent().getParent();
            IdentityConstant constClass = property.getParent().getIdentityConstant();
            ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

            m_propertySuper = property;
            m_propertyTemplate = template;

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
        ObjectHandle hThis = frame.getThis();
        TypeComposition clzThis = hThis.f_clazz;

        MethodStructure methodSuper;
        if (clzThis == m_clz)
            {
            methodSuper = m_methodSuper;
            }
        else
            {
            m_clz = clzThis;

            methodSuper = m_methodSuper = clzThis.resolveSuper(frame.f_function);
            }

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
    // (cannot be properties)
    protected int callSuperNN(Frame frame, int[] anArgValue, int[] aiReturn)
        {
        ObjectHandle hThis = frame.getThis();
        TypeComposition clzThis = hThis.f_clazz;

        MethodStructure methodSuper;
        if (clzThis == m_clz)
            {
            methodSuper = m_methodSuper;
            }
        else
            {
            m_clz = clzThis;

            methodSuper = m_methodSuper = clzThis.resolveSuper(frame.f_function);
            }

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

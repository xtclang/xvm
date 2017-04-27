package org.xvm.proto;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;

import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.TypeCompositionTemplate.Access;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyAccessTemplate;

import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xService;
import org.xvm.proto.template.xService.ServiceHandle;

/**
 * Common base for CALL_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpCallable extends Op
    {
    // get the template for the function constant
    protected FunctionTemplate getFunctionTemplate(Frame frame, int nFunctionConstantId)
        {
        assert nFunctionConstantId >= 0;

        MethodConstant constFunction =
                frame.f_context.f_constantPool.getMethodConstant(nFunctionConstantId);

        String sClass = ConstantPoolAdapter.getClassName((ClassConstant) constFunction.getNamespace());

        TypeCompositionTemplate template = frame.f_context.f_types.getTemplate(sClass);

        return template.getFunctionTemplate(constFunction);
        }

    // call super() method or "getProperty", placing the return value into the specified frame slot
    protected ExceptionHandle callSuper01(Frame frame, int iRet)
        {
        MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

        ObjectHandle hThis = frame.getThis();

        if (methodSuper instanceof PropertyAccessTemplate)
            {
            PropertyAccessTemplate propertyAccess = (PropertyAccessTemplate) methodSuper;
            TypeCompositionTemplate template = propertyAccess.getClazzTemplate();

            return template.getField(frame, hThis, propertyAccess.f_property, iRet);
            }

        ObjectHandle[] ahVar = new ObjectHandle[methodSuper.m_cVars];
        ahVar[0] = hThis;

        Frame frameNew = frame.f_context.createFrame1(frame, methodSuper, hThis, ahVar, iRet);

        return frameNew.execute();
        }

    // call super() method or "setProperty"
    protected ExceptionHandle callSuper10(Frame frame, int nArgValue)
        {
        MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

        ObjectHandle hThis = frame.getThis();
        ObjectHandle hArg;
        try
            {
            hArg = frame.getArgument(nArgValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return e.getExceptionHandle();
            }

        if (methodSuper instanceof PropertyAccessTemplate)
            {
            PropertyAccessTemplate propertyAccess = (PropertyAccessTemplate) methodSuper;
            TypeCompositionTemplate template = propertyAccess.getClazzTemplate();
            return template.setField(hThis, propertyAccess.f_property, hArg);
            }

        ObjectHandle[] ahVar = new ObjectHandle[methodSuper.m_cVars];
        ahVar[1] = hArg;

        return frame.f_context.createFrame1(frame, methodSuper, hThis, ahVar, -1).execute();
        }

    // call super() methods with multiple arguments and no more than one return
    // (cannot be properties)
    protected ExceptionHandle callSuperN(Frame frame, int[] anArgValue, int iReturn)
        {
        MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

        ObjectHandle[] ahVar = new ObjectHandle[methodSuper.m_cVars];

        ObjectHandle hThis = frame.getThis();

        try
            {
            for (int i = 0, c = anArgValue.length; i < c; i++)
                {
                ahVar[i + 1] = frame.getArgument(anArgValue[i]);
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return e.getExceptionHandle();
            }

        return frame.f_context.createFrame1(frame, methodSuper, hThis, ahVar, iReturn).execute();
        }

    // call the constructor; then potentially the finalizer; change this:struct handle to this:public
    protected ExceptionHandle callConstructor(Frame frame, FunctionTemplate constructor,
                                              FunctionTemplate finalizer, ObjectHandle[] ahVar, int iReturn)
        {
        ObjectHandle hNew = ahVar[0];

        TypeComposition clazzTarget = hNew.f_clazz;
        TypeCompositionTemplate template = clazzTarget.f_template;

        if (template.isService())
            {
            // TODO: validate the immutability
            }

        // ahVar[0] == this:struct
        ExceptionHandle hException =
                frame.f_context.createFrame1(frame, constructor, null, ahVar, -1).execute();

        if (hException == null)
            {
            ServiceHandle hService = null;

            if (template.isService())
                {
                ((xService) template).start(hService = (ServiceHandle) ahVar[0]);
                }

            if (finalizer != null)
                {
                hNew = ahVar[0] = clazzTarget.ensureAccess(hNew, Access.Private); // this:struct -> this:private

                // TODO: replace the vars
                if (hService == null)
                    {
                    hException = frame.f_context.createFrame1(frame, constructor, null, ahVar, -1).execute();
                    }
                else
                    {
                    hException = ((xService) template).
                            asyncInvoke1(frame, hService, xFunction.makeAsyncHandle(finalizer), ahVar, -1);
                    }
                }

            frame.assignValue(iReturn, clazzTarget.ensureAccess(hNew, Access.Public));
            }
        return hException;
        }
    }

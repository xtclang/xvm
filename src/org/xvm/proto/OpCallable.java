package org.xvm.proto;

import org.xvm.asm.ConstantPool.ClassConstant;
import org.xvm.asm.ConstantPool.MethodConstant;

import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.TypeCompositionTemplate.Access;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyAccessTemplate;

import org.xvm.proto.template.xFunction.FunctionHandle;
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

    // call super() method or "getProperty", placing the return value into the specified slot
    protected ExceptionHandle callSuper01(Frame frame, ObjectHandle[] ahReturn, int iRet)
        {
        MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

        ObjectHandle hThis = frame.f_ahVar[0];

        if (methodSuper instanceof PropertyAccessTemplate)
            {
            PropertyAccessTemplate propertyAccess = (PropertyAccessTemplate) methodSuper;
            TypeCompositionTemplate template = propertyAccess.getClazzTemplate();

            return template.getProperty(propertyAccess.f_property, null,
                    frame, hThis, ahReturn, iRet);
            }

        ObjectHandle[] ahVar = new ObjectHandle[methodSuper.m_cVars];
        ahVar[0] = hThis;

        Frame frameNew = frame.f_context.createFrame(frame, methodSuper, hThis, ahVar);

        ExceptionHandle hException = frameNew.execute();
        if (hException == null)
            {
            ahReturn[iRet] = frameNew.f_ahReturn[0];
            }
        return hException;
        }

    // call super() method or "setProperty"
    protected ExceptionHandle callSuper10(Frame frame, int nArgValue)
        {
        MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

        ObjectHandle hThis = frame.f_ahVar[0];
        ObjectHandle hArg  = nArgValue >= 0 ? frame.f_ahVar[nArgValue] :
                Utils.resolveConst(frame, nArgValue);

        if (methodSuper instanceof PropertyAccessTemplate)
            {
            PropertyAccessTemplate propertyAccess = (PropertyAccessTemplate) methodSuper;
            TypeCompositionTemplate template = propertyAccess.getClazzTemplate();
            return template.setProperty(propertyAccess.f_property, null,
                    frame, hThis, hArg);
            }

        ObjectHandle[] ahVar = new ObjectHandle[methodSuper.m_cVars];
        ahVar[0] = hThis;
        ahVar[1] = hArg;

        return frame.f_context.createFrame(frame, methodSuper, hThis, ahVar).execute();
        }

    // create a "super" call frame for methods that cannot be properties
    protected Frame createSuperCall(Frame frame, int[] anArgValue)
        {
        MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

        ObjectHandle[] ahVar = new ObjectHandle[methodSuper.m_cVars];

        ObjectHandle hThis = frame.f_ahVar[0];

        ahVar[0] = hThis;

        for (int i = 0, c = anArgValue.length; i < c; i++)
            {
            int nArg = anArgValue[i];

            ahVar[i + 1] = nArg >= 0 ? frame.f_ahVar[nArg] :
                    Utils.resolveConst(frame, nArg);
            }

        return frame.f_context.createFrame(frame, methodSuper, hThis, ahVar);
        }

    // call the constructor; then potentially the finalizer; change this:struct handle to this:public
    protected ExceptionHandle callConstructor(Frame frame, FunctionTemplate constructor, ObjectHandle[] ahVar)
        {
        ObjectHandle hTarget = ahVar[0];

        TypeComposition clazzTarget = hTarget.f_clazz;
        TypeCompositionTemplate template = clazzTarget.f_template;

        if (template.isService())
            {
            // TODO: validate the immutability
            }

        Frame frameNew = frame.f_context.createFrame(frame, constructor, null, ahVar);

        ExceptionHandle hException = frameNew.execute();

        if (hException == null)
            {
            ServiceHandle hService = null;

            if (template.isService())
                {
                // TODO: validate the immutability
                hService = (ServiceHandle) ahVar[0];
                ((xService) template).start(hService);
                }

            if (constructor.m_cReturns > 0)
                {
                FunctionHandle hFinally;
                try
                    {
                    hFinally = frameNew.f_ahReturn[0].as(FunctionHandle.class);
                    }
                catch (ExceptionHandle.WrapperException e )
                    {
                    return e.getExceptionHandle();
                    }

                hTarget = ahVar[0] = clazzTarget.ensureAccess(hTarget, Access.Private); // this:struct -> this:private

                if (hService == null)
                    {
                    hException = hFinally.call(frame, ahVar, Utils.OBJECTS_NONE);
                    }
                else
                    {
                    hException = ((xService) template).
                            asyncInvoke(frame, hService, hFinally, ahVar, Utils.OBJECTS_NONE);
                    }
                }

            ahVar[0] = clazzTarget.ensureAccess(hTarget, Access.Public);
            }
        return hException;
        }
    }

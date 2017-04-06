package org.xvm.proto;

import org.xvm.asm.ConstantPool.ClassConstant;
import org.xvm.asm.ConstantPool.MethodConstant;

import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.TypeCompositionTemplate.Access;

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

    protected Frame createSuperCall(Frame frame, int nArgValue)
        {
        MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

        ObjectHandle[] ahVar = new ObjectHandle[methodSuper.m_cVars];

        ObjectHandle hThis = frame.f_ahVar[0];

        ahVar[0] = hThis;
        ahVar[1] = nArgValue >= 0 ? frame.f_ahVar[nArgValue] :
                Utils.resolveConst(frame, methodSuper.m_argTypeName[1], nArgValue);

        return frame.f_context.createFrame(frame, methodSuper, hThis, ahVar);
        }

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
                    Utils.resolveConst(frame, methodSuper.m_argTypeName[i + 1], nArg);
            }

        return frame.f_context.createFrame(frame, methodSuper, hThis, ahVar);
        }

    // call the constructor; then potentially the finalizer; change this:struct handle to this:public
    protected ExceptionHandle callConstructor(Frame frame, FunctionTemplate constructor, ObjectHandle[] ahVar)
        {
        Frame frameNew = frame.f_context.createFrame(frame, constructor, null, ahVar);

        ExceptionHandle hException = frameNew.execute();

        if (hException == null)
            {
            ObjectHandle hTarget = ahVar[0];

            TypeComposition clazzTarget = hTarget.f_clazz;
            TypeCompositionTemplate template = clazzTarget.f_template;
            ServiceHandle hService = null;

            if (template.isService())
                {
                try
                    {
                    hService = hTarget.as(ServiceHandle.class);
                    ((xService) template).start(hService);
                    }
                catch (ExceptionHandle.WrapperException e)
                    {
                    return e.getExceptionHandle();
                    }
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
                    hException = hFinally.invoke(frame, ahVar, Utils.OBJECTS_NONE);
                    }
                else
                    {
                    hException = ((xService) template).
                            invokeAsync(frame, hService, hFinally, ahVar, Utils.OBJECTS_NONE);
                    }

                ahVar[0] = clazzTarget.ensureAccess(hTarget, Access.Public);
                }
            }
        return hException;
        }
    }

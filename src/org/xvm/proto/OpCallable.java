package org.xvm.proto;

import org.xvm.asm.ConstantPool.ClassConstant;
import org.xvm.asm.ConstantPool.MethodConstant;

import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.TypeCompositionTemplate.Access;

import org.xvm.proto.template.xFunction.FunctionHandle;
import org.xvm.proto.template.xService;

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

        ObjectHandle[] ahVars = new ObjectHandle[methodSuper.m_cVars];

        ObjectHandle hThis = frame.f_ahVar[0];

        ahVars[0] = hThis;
        ahVars[1] = nArgValue >= 0 ? frame.f_ahVar[nArgValue] :
                Utils.resolveConst(frame, methodSuper.m_argTypeName[1], nArgValue);

        return frame.f_context.createFrame(frame, methodSuper, hThis, ahVars);
        }

    protected Frame createSuperCall(Frame frame, int[] anArgValue)
        {
        MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

        ObjectHandle[] ahVars = new ObjectHandle[methodSuper.m_cVars];

        ObjectHandle hThis = frame.f_ahVar[0];

        ahVars[0] = hThis;

        for (int i = 0, c = anArgValue.length; i < c; i++)
            {
            int nArg = anArgValue[i];

            ahVars[i + 1] = nArg >= 0 ? frame.f_ahVar[nArg] :
                    Utils.resolveConst(frame, methodSuper.m_argTypeName[i + 1], nArg);
            }

        return frame.f_context.createFrame(frame, methodSuper, hThis, ahVars);
        }

    // call the constructor; then potentially the finalizer; change this:struct handle to this:public
    protected ObjectHandle callConstructor(Frame frame, FunctionTemplate constructor, ObjectHandle[] ahVars)
        {
        Frame frameNew = frame.f_context.createFrame(frame, constructor, null, ahVars);

        ObjectHandle hException = frameNew.execute();

        if (hException == null)
            {
            ObjectHandle hTarget = ahVars[0];
            if (constructor.m_cReturns > 0)
                {
                TypeComposition clazzTarget = hTarget.f_clazz;
                hTarget = ahVars[0] = clazzTarget.ensureAccess(hTarget, Access.Private); // this:struct -> this:private

                FunctionHandle hFinally = (FunctionHandle) frameNew.f_ahReturn[0];

                if (clazzTarget.isService())
                    {
                    ((xService) clazzTarget.f_template).invokeAsync(frame, ahVars, hFinally);

                    // create a FutureRef
                    ahVars[0] = clazzTarget.ensureAccess(hTarget, Access.Public);
                    }
                else
                    {
                    hException = hFinally.invoke(frame, ahVars, Utils.OBJECTS_NONE);

                    ahVars[0] = clazzTarget.ensureAccess(hTarget, Access.Public);
                    }
                }
            }
        return hException;
        }
    }

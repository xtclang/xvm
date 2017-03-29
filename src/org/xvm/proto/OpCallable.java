package org.xvm.proto;

import org.xvm.asm.ConstantPool.ClassConstant;
import org.xvm.asm.ConstantPool.MethodConstant;

import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.TypeCompositionTemplate.Access;

import org.xvm.proto.template.xFunction.FunctionHandle;

/**
 * Common base for CALL_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpCallable extends Op
    {
    protected FunctionTemplate getFunctionTemplate(Frame frame, int nFnValue)
        {
        if (nFnValue >= 0)
            {
            FunctionHandle hFunction = (FunctionHandle) frame.f_ahVars[nFnValue];

            // TODO: same as New; how to do it right?
            return (FunctionTemplate) hFunction.m_invoke;
            }
        else
            {
            MethodConstant constFunction =
                    frame.f_context.f_constantPool.getMethodConstant(-nFnValue);

            String sClass = ConstantPoolAdapter.getClassName((ClassConstant) constFunction.getNamespace());

            TypeCompositionTemplate template = frame.f_context.f_types.getTemplate(sClass);

            return template.getFunctionTemplate(constFunction);
            }
        }

    // call the constructor; then potentially the finalizer; change this:struct handle to this:public
    protected ObjectHandle callConstructor(Frame frame, FunctionTemplate constructor, ObjectHandle[] ahVars)
        {
        Frame frameNew = new Frame(frame.f_context, frame, null, constructor, ahVars);
        ObjectHandle hException = frameNew.execute();

        if (hException == null)
            {
            ObjectHandle hTarget = ahVars[0];
            if (constructor.m_cReturns > 0)
                {
                hTarget = ahVars[0] = hTarget.f_clazz.ensureAccess(hTarget, Access.Private); // this:struct -> this:private

                FunctionHandle hFinally = (FunctionHandle) frameNew.f_ahReturns[0];

                // get the "finally" method from the handle; TODO: how to do it right?
                FunctionTemplate methFinally = (FunctionTemplate) hFinally.m_invoke;

                // call the finally method
                hException = new Frame(frame.f_context, frame, null, methFinally, ahVars).execute();
                }
            ahVars[0] = hTarget.f_clazz.ensureAccess(hTarget, Access.Public);
            }
        return hException;
        }
    }

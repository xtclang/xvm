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
            // TODO parameters, returns
            return template.getFunctionTemplate(constFunction.getName(), "");
            }
        }

    // call the constructor; then potentially the finalizer; change this:struct handle to this:public
    protected ObjectHandle callConstructor(Frame frame, FunctionTemplate constructor, ObjectHandle[] ahVars)
        {
        int cReturns = constructor.m_cReturns;

        ObjectHandle[] ahRet = cReturns == 0 ? Utils.OBJECTS_NONE : new ObjectHandle[cReturns];

        ObjectHandle hException = new Frame(frame.f_context, frame, null, constructor, ahVars, ahRet).execute();

        if (hException == null)
            {
            if (cReturns > 0)
                {
                ahVars[0] = constructor.getClazzTemplate().changeType(ahVars[0], Access.Private); // this:struct -> this:private

                FunctionHandle hFinally = (FunctionHandle) ahRet[0];

                // get the "finally" method from the handle; TODO: how to do it right?
                FunctionTemplate methFinally = (FunctionTemplate) hFinally.m_invoke;

                // call the finally method
                hException = new Frame(frame.f_context, frame, null, methFinally, ahVars, Utils.OBJECTS_NONE).execute();
                }
            ahVars[0] = constructor.getClazzTemplate().changeType(ahVars[0], Access.Public);
            }
        return hException;
        }
    }

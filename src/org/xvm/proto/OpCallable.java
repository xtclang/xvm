package org.xvm.proto;

import org.xvm.asm.ConstantPool;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;

/**
 * Common base for CALL_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpCallable extends Op
    {
    protected final int f_nFunctionValue;

    protected OpCallable(int nFunction)
        {
        f_nFunctionValue = nFunction;
        }

    protected FunctionTemplate getFunctionTemplate(Frame frame)
        {
        if (f_nFunctionValue >= 0)
            {
            ObjectHandle hFunction = frame.f_ahVars[f_nFunctionValue]; // xFunction instance
            return null; // TODO: hFunction -> function template
            }
        else
            {
            ConstantPool.MethodConstant constFunction =
                    frame.f_context.f_constantPool.getMethodConstant(-f_nFunctionValue);

            String sClass = ConstantPoolAdapter.getClassName((ConstantPool.ClassConstant) constFunction.getNamespace());

            TypeCompositionTemplate template = frame.f_context.f_container.m_types.getTemplate(sClass);
            // TODO parameters, returns
            return template.getFunctionTemplate(constFunction.getName(), "");
            }
        }

    }

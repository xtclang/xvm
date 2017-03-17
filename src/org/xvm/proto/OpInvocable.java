package org.xvm.proto;

import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

import org.xvm.asm.ConstantPool.MethodConstant;

/**
 * Common base for INVOKE_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpInvocable extends Op
    {
    protected final int f_nMethodValue;

    protected OpInvocable(int nMethod)
        {
        f_nMethodValue = nMethod;
        }

    protected MethodTemplate getMethodTemplate(Frame frame, TypeCompositionTemplate template)
        {
        MethodTemplate method;

        if (f_nMethodValue >= 0)
            {
            ObjectHandle hMethod = frame.f_ahVars[f_nMethodValue]; // xMethod instance
            method = null; // TODO
            }
        else
            {
            MethodConstant constMethod =
                    frame.f_context.f_constantPool.getMethodConstant(-f_nMethodValue);
            // TODO parameters, returns
            method = template.getMethodTemplate(constMethod.getName(), "");
            }
        return method;
        }

    }

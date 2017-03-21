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
    protected MethodTemplate getMethodTemplate(Frame frame, TypeCompositionTemplate template, int nMethodValue)
        {
        MethodTemplate method;

        if (nMethodValue >= 0)
            {
            ObjectHandle hMethod = frame.f_ahVars[nMethodValue]; // xMethod instance
            method = null; // TODO
            }
        else
            {
            MethodConstant constMethod =
                    frame.f_context.f_constantPool.getMethodConstant(-nMethodValue);
            // TODO parameters, returns
            method = template.getMethodTemplate(constMethod.getName(), "");
            }
        return method;
        }
    }

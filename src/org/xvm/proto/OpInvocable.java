package org.xvm.proto;

import org.xvm.asm.ConstantPool.MethodConstant;
import org.xvm.asm.ConstantPool.PropertyConstant;

import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;

import org.xvm.proto.template.xMethod.MethodHandle;

/**
 * Common base for INVOKE_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpInvocable extends Op
    {
    protected MethodTemplate getMethodTemplate(Frame frame, TypeCompositionTemplate template, int nMethodValue)
        {
        if (nMethodValue >= 0)
            {
            MethodHandle hMethod = (MethodHandle) frame.f_ahVars[nMethodValue];

            // TODO: same as Function; how to do it right?
            return hMethod.m_method;
            }
        else
            {
            MethodConstant constMethod =
                    frame.f_context.f_constantPool.getMethodConstant(-nMethodValue);
            // TODO parameters, returns
            return template.getMethodTemplate(constMethod.getName(), "");
            }
        }

    protected PropertyTemplate getPropertyTemplate(Frame frame, TypeCompositionTemplate template, int nPropValue)
        {
        if (nPropValue >= 0)
            {
            // PropertyHandle hProperty = (PropertyHandle) frame.f_ahVars[nPropValue];

            // TODO:
            return null;
            }
        else
            {
            PropertyConstant constProperty =
                    frame.f_context.f_constantPool.getPropertyConstant(-nPropValue);
            // TODO parameters, returns
            return template.getPropertyTemplate(constProperty.getName());
            }
        }
    }

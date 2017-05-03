package org.xvm.proto;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;

import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;

/**
 * Common base for INVOKE_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpInvocable extends Op
    {
    protected MethodTemplate getMethodTemplate(Frame frame, TypeCompositionTemplate template, int nMethodConstId)
        {
        assert(nMethodConstId >= 0);

        MethodConstant constMethod =
                frame.f_context.f_constantPool.getMethodConstant(nMethodConstId);
        // TODO parameters, returns
        return template.getMethodTemplate(constMethod);
        }

    protected PropertyTemplate getPropertyTemplate(Frame frame, TypeCompositionTemplate template, int nPropValue)
        {
        assert (nPropValue >= 0);

        PropertyConstant constProperty =
                frame.f_context.f_constantPool.getPropertyConstant(nPropValue);
        return template.getPropertyTemplate(constProperty.getName());
        }
    }

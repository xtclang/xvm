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
    private int m_nMethodId;         // cached method id
    private MethodTemplate m_method; // cached method

    protected MethodTemplate getMethodTemplate(Frame frame,
                                               TypeCompositionTemplate template, int nMethodConstId)
        {
        assert(nMethodConstId >= 0);

        if (m_method != null && nMethodConstId == m_nMethodId)
            {
            return m_method;
            }

        MethodConstant constMethod =
                frame.f_context.f_constantPool.getMethodConstant(nMethodConstId);

        MethodTemplate method = template.getMethodTemplate(constMethod);
        if (method == null)
            {
            throw new IllegalStateException("Missing method " + constMethod + " on " + template);
            }

        m_nMethodId = nMethodConstId;
        m_method = method;
        return method;
        }

    protected PropertyTemplate getPropertyTemplate(Frame frame,
                                                   TypeCompositionTemplate template, int nPropValue)
        {
        assert (nPropValue >= 0);

        PropertyConstant constProperty =
                frame.f_context.f_constantPool.getPropertyConstant(nPropValue);
        return template.getPropertyTemplate(constProperty.getName());
        }
    }

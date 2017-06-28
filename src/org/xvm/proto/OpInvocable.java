package org.xvm.proto;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;


/**
 * Common base for INVOKE_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpInvocable extends Op
    {
    private int m_nMethodId;          // cached method id
    private MethodStructure m_method; // cached method

    private int m_nPropId;                // cached property id
    private PropertyStructure m_property; // cached property

    protected MethodStructure getMethodStructure(Frame frame,
                                                 ClassTemplate template, int nMethodConstId)
        {
        assert(nMethodConstId >= 0);

        if (m_method != null && nMethodConstId == m_nMethodId)
            {
            return m_method;
            }

        MethodConstant constMethod = (MethodConstant)
                frame.f_context.f_pool.getConstant(nMethodConstId);

        m_nMethodId = nMethodConstId;
        return m_method = (MethodStructure) constMethod.getComponent();
        }

    protected PropertyStructure getPropertyStructure(Frame frame,
                                                     ClassTemplate template, int nPropertyId)
        {
        assert (nPropertyId >= 0);

        if (m_property != null && nPropertyId == m_nPropId)
            {
            return m_property;
            }

        PropertyConstant constProperty = (PropertyConstant)
                frame.f_context.f_pool.getConstant(nPropertyId);

        m_nPropId = nPropertyId;
        return m_property = (PropertyStructure) constProperty.getComponent();
        }
    }

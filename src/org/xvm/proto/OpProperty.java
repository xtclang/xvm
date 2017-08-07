package org.xvm.proto;

import org.xvm.asm.PropertyStructure;
import org.xvm.asm.constants.PropertyConstant;


/**
 * Common base for property related (P_) ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpProperty extends Op
    {
    private TypeComposition m_clazz;      // cached class
    private int m_nPropId;                // cached property id
    private PropertyStructure m_property; // cached property

    protected PropertyStructure getPropertyStructure(Frame frame,
                                                     TypeComposition clazz, int nPropertyId)
        {
        assert (nPropertyId >= 0);

        if (m_property != null && nPropertyId == m_nPropId && m_clazz == clazz)
            {
            return m_property;
            }

        PropertyConstant constProperty = (PropertyConstant)
                frame.f_context.f_pool.getConstant(nPropertyId);

        m_nPropId = nPropertyId;
        m_clazz = clazz;
        return m_property = clazz.getProperty(constProperty.getName());
        }
    }

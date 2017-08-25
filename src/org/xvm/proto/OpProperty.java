package org.xvm.proto;

import org.xvm.asm.constants.PropertyConstant;


/**
 * Common base for property related (P_) ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpProperty extends Op
    {
    private TypeComposition m_clazz;             // cached class
    private int m_nPropId;                       // cached property id
    private CallChain.PropertyCallChain m_chain; // cached call chain

    protected CallChain.PropertyCallChain getPropertyCallChain(Frame frame, TypeComposition clazz,
                                                               int nPropertyId, boolean fGet)
        {
        assert (nPropertyId >= 0);

        if (m_chain != null && nPropertyId == m_nPropId && m_clazz == clazz)
            {
            return m_chain;
            }

        PropertyConstant constProperty = (PropertyConstant)
                frame.f_context.f_pool.getConstant(nPropertyId);

        m_nPropId = nPropertyId;
        m_clazz = clazz;
        return m_chain = fGet ?
                clazz.getPropertyGetterChain(constProperty.getName()) :
                clazz.getPropertySetterChain(constProperty.getName());
        }
    }

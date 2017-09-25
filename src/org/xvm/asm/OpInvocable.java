package org.xvm.asm;


import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;


/**
 * Common base for INVOKE_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpInvocable extends Op
    {
    private TypeComposition m_clazz;        // cached class
    private int             m_nMethodId;    // cached method id
    private CallChain       m_chain;        // cached call chain

    protected CallChain getCallChain(Frame frame, TypeComposition clazz, int nMethodConstId)
        {
        assert (nMethodConstId >= 0);

        if (m_chain != null && nMethodConstId == m_nMethodId && m_clazz == clazz)
            {
            return m_chain;
            }

        MethodConstant constMethod = (MethodConstant) frame.f_context.f_pool.getConstant(nMethodConstId);

        m_nMethodId = nMethodConstId;
        m_clazz     = clazz;
        m_chain     = clazz.getMethodCallChain(constMethod.getSignature());

        return m_chain;
        }
    }

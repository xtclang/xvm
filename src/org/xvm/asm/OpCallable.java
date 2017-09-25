package org.xvm.asm;


import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.Frame;


/**
 * Common base for CALL_ ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpCallable extends Op
    {
    // function caching
    private MethodStructure m_function;   // cached function

    // get the structure for the function constant
    protected MethodStructure getMethodStructure(Frame frame, int nFunctionConstantId)
        {
        assert nFunctionConstantId >= 0;

        // there is no need to cache the id, since it's a constant for a given op-code
        if (m_function != null)
            {
            return m_function;
            }

        MethodConstant constFunction = (MethodConstant)
                frame.f_context.f_pool.getConstant(nFunctionConstantId);

        return m_function = (MethodStructure) constFunction.getComponent();
        }
    }

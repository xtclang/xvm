package org.xvm.javajit;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import static java.lang.constant.ConstantDescs.CD_void;

/**
 * JIT specific information for a method.
 */
public class JitMethodDesc {
    public JitMethodDesc(JitParamDesc[] standardReturns,  JitParamDesc[] standardParams,
                         JitParamDesc[] optimizedReturns, JitParamDesc[] optimizedParams) {
        this.standardReturns  = standardReturns;
        this.standardParams   = standardParams;
        this.optimizedReturns = optimizedReturns;
        this.optimizedParams  = optimizedParams;

        standardMD  = computeMethodDesc(standardReturns, standardParams);
        optimizedMD = optimizedParams == null || optimizedReturns == null
            ? null
            : computeMethodDesc(optimizedReturns, optimizedParams);
    }

    /**
     * @return an optimized JitParamDesc for the specified standard argument index.
     */
    public JitParamDesc getOptimizedParam(int argIndex) {
        return optimizedParams[getOptimizedParamIndex(argIndex)];
    }

    /**
     * @return an index of the optimized JitParamDesc for the specified standard argument index.
     */
    public int getOptimizedParamIndex(int argIndex) {
        for (int i = 0, c = optimizedParams.length; i < c; i++) {
            if (optimizedParams[i].index == argIndex) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid arg index");
    }

    /**
     * @return an optimized JitParamDesc for the specified standard return index.
     */
    public JitParamDesc getOptimizedReturn(int retIndex) {
        return optimizedReturns[getOptimizedReturnIndex(retIndex)];
    }

    /**
     * @return an index of the optimized JitParamDesc for the specified standard return index.
     */
    public int getOptimizedReturnIndex(int retIndex) {
        for (int i = 0, c = optimizedReturns.length; i < c; i++) {
            if (optimizedReturns[i].index == retIndex) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid return index");
    }

    public static MethodTypeDesc computeMethodDesc(JitParamDesc[] returns, JitParamDesc[] params) {
        int         paramCount = params.length;
        ClassDesc[] paramCDs   = new ClassDesc[paramCount + 1];

        // the argument zero is **always** the Ctx
        paramCDs[0] = ClassDesc.of(Ctx.class.getName());

        for (int i = 0; i < paramCount; i++)
            {
            paramCDs[i+1] = params[i].cd;
            }
        return MethodTypeDesc.of(returns.length == 0 ? CD_void : returns[0].cd, paramCDs);
        }

    public final JitParamDesc[] standardReturns;
    public final JitParamDesc[] standardParams;
    public final JitParamDesc[] optimizedReturns;
    public final JitParamDesc[] optimizedParams;

    public final MethodTypeDesc standardMD;  // the generic "xObj" flavor
    public final MethodTypeDesc optimizedMD; // (optional) optimized primitive

}

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
        this.optimizedReturns = optimizedReturns;
        this.standardParams   = standardParams;
        this.optimizedParams  = optimizedParams;

        standardMD  = computeMethodDesc(standardReturns, standardParams);
        optimizedMD = optimizedParams == null || optimizedReturns == null
            ? null
            : computeMethodDesc(optimizedReturns, optimizedParams);
    }

    public static MethodTypeDesc computeMethodDesc(JitParamDesc[] returns, JitParamDesc[] params) {
        int         cParams   = params.length;
        ClassDesc[] acdParams = new ClassDesc[cParams + 1];

        acdParams[0] = ClassDesc.of(org.xvm.javajit.Ctx.class.getName());

        for (int i = 0; i < cParams; i++)
            {
            acdParams[i+1] = params[i].cd;
            }
        return MethodTypeDesc.of(returns.length == 0 ? CD_void : returns[0].cd, acdParams);
        }

    public final JitParamDesc[] standardReturns;
    public final JitParamDesc[] optimizedReturns;
    public final JitParamDesc[] standardParams;
    public final JitParamDesc[] optimizedParams;

    public final MethodTypeDesc standardMD;  // the generic "xObj" flavor
    public final MethodTypeDesc optimizedMD; // (optional) optimized primitive

}

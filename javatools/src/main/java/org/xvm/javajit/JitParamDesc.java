package org.xvm.javajit;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_boolean;

import static org.xvm.javajit.JitFlavor.NullablePrimitive;
import static org.xvm.javajit.JitFlavor.NullableXvmPrimitive;
import static org.xvm.javajit.JitFlavor.Primitive;
import static org.xvm.javajit.JitFlavor.Specific;
import static org.xvm.javajit.JitFlavor.Widened;
import static org.xvm.javajit.JitFlavor.XvmPrimitive;

/**
 * JIT specific information for a method parameter or return value
 */
public class JitParamDesc extends JitTypeDesc {
    public JitParamDesc(TypeConstant type, JitFlavor flavor, ClassDesc cd,
                        int index, int altIndex, boolean extension) {
        super(type, flavor, cd);

        this.index     = index;
        this.altIndex  = altIndex;
        this.extension = extension;
    }

    public final int index;         // for both parameter and return: the corresponding index in the
                                    // Ecstasy method signature
    public final int altIndex;      // for parameter: the corresponding index in the Java method;
                                    // for return: an index of the "long" or "Object" return value
                                    // in the Ctx object; -1 indicates a natural return
    public final boolean extension; // is this an additional Java parameter

    public static final JitParamDesc[] NONE = new JitParamDesc[0];

    /**
     * The combination of standard and optimized parameter descriptors.
     */
    public record JitParams(JitParamDesc[] apdStdParam, JitParamDesc[] apdOptParam) {
        public boolean isOptimized() {
            return apdOptParam != null;
        }
    }

    /**
     * @return the JitParams record for the specified parameter type.
     */
    public static JitParams computeJitParams(TypeSystem ts, TypeConstant type) {
        JitParamDesc[] apdOptParam = null;
        JitParamDesc[] apdStdParam;
        ClassDesc cd;

        if ((cd = JitTypeDesc.getPrimitiveClass(type)) != null) {
            ClassDesc cdStd = type.ensureClassDesc(ts);

            apdStdParam = new JitParamDesc[] {
                new JitParamDesc(type, Specific, cdStd, 0, 0, false)};
            apdOptParam = new JitParamDesc[] {
                new JitParamDesc(type, Primitive, cd, 0, 0, false)};
        } else if ((cd = JitTypeDesc.getNullablePrimitiveClass(type)) != null) {
            ClassDesc cdStd = type.ensureClassDesc(ts);
            apdStdParam = new JitParamDesc[] {
                new JitParamDesc(type, Widened, cdStd, 0, 0, false)};
            apdOptParam = new JitParamDesc[] {
                new JitParamDesc(type, NullablePrimitive, cd, 0, 0, false),
                new JitParamDesc(type, NullablePrimitive, CD_boolean, 0, 1, true)
            };
        } else if ((cd = JitTypeDesc.getXvmPrimitiveClass(type)) != null) {
            ClassDesc cdStd = type.ensureClassDesc(ts);
            apdStdParam = new JitParamDesc[] {
                    new JitParamDesc(type, Specific, cdStd, 0, 0, false)};
            ClassDesc[] cds = JitTypeDesc.getXvmPrimitiveClasses(type);
            apdOptParam = new JitParamDesc[cds.length];
            for (int i = 0; i < cds.length; i++) {
                apdOptParam[i] = new JitParamDesc(type, XvmPrimitive, cds[i], 0, i, false);
            }
        } else if ((cd = JitTypeDesc.getNullableXvmPrimitiveClass(type)) != null) {
            ClassDesc cdStd = type.ensureClassDesc(ts);
            apdStdParam = new JitParamDesc[] {
                    new JitParamDesc(type, Widened, cdStd, 0, 0, false)};
            ClassDesc[] cds = JitTypeDesc.getXvmPrimitiveClasses(type);
            apdOptParam = new JitParamDesc[cds.length + 1];
            for (int i = 0; i < cds.length; i++) {
                apdOptParam[i] = new JitParamDesc(type, NullableXvmPrimitive, cds[i], 0, i, false);
            }
            apdOptParam[cds.length] =
                    new JitParamDesc(type, NullableXvmPrimitive, CD_boolean, 0, cds.length, true);
        } else if ((cd = JitTypeDesc.getWidenedClass(type)) != null) {
            apdStdParam = new JitParamDesc[] {
                new JitParamDesc(type, Widened, cd, 0, 0, false)};
        } else {
            assert type.isSingleUnderlyingClass(true);

            cd = type.ensureClassDesc(ts);
            apdStdParam = new JitParamDesc[] {
                new JitParamDesc(type, Specific, cd, 0, 0, false)};
        }
        return new JitParams(apdStdParam, apdOptParam);
    }
}

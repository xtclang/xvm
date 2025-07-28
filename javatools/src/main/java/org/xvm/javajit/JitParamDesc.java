package org.xvm.javajit;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.TypeConstant;

/**
 * JIT specific information for a method parameter or return value
 */
public class JitParamDesc extends JitTypeDesc {
    public JitParamDesc(TypeConstant type, JitFlavor flavor, ClassDesc cd,
                        int index, boolean extension) {
        super(type, flavor, cd);

        this.index     = index;
        this.extension = extension;
    }

    public final int index;         // for parameter: the corresponding index in the Ecstasy method
                                    // signature; for return: an index of the "long" or "Object"
                                    // return value in the Ctx object; -1 indicates a natural return
    public final boolean extension; // is this an additional Java parameter

    public static final JitParamDesc[] NONE = new JitParamDesc[0];
}

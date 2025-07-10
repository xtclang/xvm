package org.xvm.javajit;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.TypeConstant;

/**
 * TODO:
 */
public class JitParamDesc extends JitTypeDesc {
    public JitParamDesc(TypeConstant type, JitFlavor flavor, ClassDesc cd,
                        int origin, boolean extension) {
        super(type, flavor, cd);

        this.origin    = origin;
        this.extension = extension;
    }

    public final int origin;        // an index of the parameter/return in the method signature
    public final boolean extension; // is this an additional Java parameter

    public static final JitParamDesc[] NONE = new JitParamDesc[0];
}

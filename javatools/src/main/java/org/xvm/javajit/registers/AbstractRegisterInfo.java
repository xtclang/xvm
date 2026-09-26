package org.xvm.javajit.registers;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.RegisterInfo;

/**
 * Common register metadata and scope-start label management.
 */
public abstract class AbstractRegisterInfo
        implements RegisterInfo {
    /**
     * @param regId   the register id
     * @param flavor  the {@link JitFlavor} of the value this register represents
     * @param type    the {@link TypeConstant} of the value this register represents
     * @param cd      the {@link ClassDesc} of the value this register represents
     * @param name    the name of the value represented by this register
     */
    protected AbstractRegisterInfo(int regId, JitFlavor flavor, TypeConstant type,
                                   ClassDesc cd, String name) {
        this.regId  = regId;
        this.flavor = flavor;
        this.type   = type;
        this.cd     = cd;
        this.name   = name;
    }

    @Override
    public int regId() {
        return regId;
    }

    @Override
    public JitFlavor flavor() {
        return flavor;
    }

    @Override
    public TypeConstant type() {
        return type;
    }

    @Override
    public ClassDesc cd() {
        return cd;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isAssignmentPending() {
        return startLabel != null;
    }

    @Override
    public void addStartLabel(Label label) {
        assert startLabel == null && label != null;
        startLabel = label;
    }

    @Override
    public boolean bindStartLabel(CodeBuilder code) {
        if (startLabel == null) {
            return false;
        }

        code.labelBinding(startLabel);
        startLabel = null;
        return true;
    }

    @Override
    public String toString() {
        return "regId="   + regId
            + ", flavor=" + flavor
            + ", type="   + type.getValueString()
            + ", cd="     + cd
            + ", name="   + name;
    }

    protected final int          regId;
    protected final JitFlavor    flavor;
    protected final TypeConstant type;
    protected final ClassDesc    cd;
    protected final String       name;

    private Label startLabel;
}

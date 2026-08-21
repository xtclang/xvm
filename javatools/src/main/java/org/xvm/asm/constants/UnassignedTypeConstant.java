package org.xvm.asm.constants;


import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * A synthetic type used by the JIT type matrix to mark a declared but unassigned register.
 * This type is transient and cannot be stored in a {@link ConstantPool}.
 */
public class UnassignedTypeConstant
        extends TypeConstant {

    public UnassignedTypeConstant(TypeConstant type) {
        super(type.getConstantPool());

        this.type = type;
    }

    @Override
    public boolean isModifyingType() {
        return true;
    }

    @Override
    public TypeConstant getUnderlyingType() {
        return type;
    }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type) {
        return new UnassignedTypeConstant(type);
    }

    @Override
    public Format getFormat() {
        return type.getFormat();
    }

    @Override
    protected int compareDetails(Constant that) {
        return that instanceof UnassignedTypeConstant unassigned
                ? type.compareTo(unassigned.type)
                : -1;
    }

    @Override
    protected void registerConstants(ConstantPool pool) {
        throw new IllegalStateException();
    }

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public String getValueString() {
        return "unassigned " + type.getValueString();
    }

    @Override
    protected int computeHashCode() {
        return type.hashCode();
    }

    private final TypeConstant type;
}

package org.xvm.asm;

import java.io.DataOutput;

import org.xvm.asm.constants.MethodConstant;

/**
 * A generated executable owned by a runtime descriptor context, outside the image's declarations.
 *
 * <p>The parent module supplies definition lookup, but does not contain this method as a child.
 * Build its code and call {@link #forceAssembly} with its descriptor pool before execution. Ops
 * use method-local constant indices; neither this method nor its descriptors can be serialized
 * as part of the source image. Keep the method in its owning runtime composition.
 */
public final class RuntimeMethodStructure extends MethodStructure {
    /**
     * Create an unattached, synthetic, zero-argument function with no return values.
     *
     * @param identity  the method identity in a runtime descriptor pool
     */
    public RuntimeMethodStructure(MethodConstant identity) {
        super(identity.getFileStructure().getModule(),
                Format.METHOD.ordinal() | Access.PUBLIC.FLAGS | STATIC_BIT | SYNTHETIC_BIT,
                identity, null, Annotation.NO_ANNOTATIONS,
                Parameter.NO_PARAMS, Parameter.NO_PARAMS, true, false);
        if (identity.getConstantPool().hasSerializedIndices()) {
            throw new IllegalArgumentException("Runtime method requires a descriptor pool");
        }
        if (!identity.getSignature().getParams().isEmpty()
                || !identity.getSignature().getReturns().isEmpty()) {
            throw new IllegalArgumentException("Initializer must have no parameters or returns");
        }
    }

    @Override
    public ConstantPool getConstantPool() {
        return getIdentityConstant().getConstantPool();
    }

    @Override
    public void forceAssembly(ConstantPool pool) {
        if (pool != getConstantPool()) {
            throw new IllegalArgumentException("Generated code must use its owning descriptor pool");
        }
        super.forceAssembly(pool);
    }

    @Override
    protected void assemble(DataOutput out) {
        throw new UnsupportedOperationException("Runtime methods are not image declarations");
    }
}

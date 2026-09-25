package org.xvm.asm;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Nop;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

/**
 * A generated executable owned by a runtime descriptor context, outside the image's declarations.
 *
 * <p>The parent namespace supplies definition lookup, but does not contain this method as a child.
 * Build its code and call {@link #forceAssembly} with its descriptor pool before execution. Ops
 * use method-local constant indices; neither this method nor its descriptors can be serialized
 * as part of the source image. Retain the method with its owning runtime context.
 */
public final class RuntimeMethodStructure extends MethodStructure {
    /**
     * Create an unattached, synthetic, zero-argument function with no return values.
     *
     * @param identity  the method identity in a runtime descriptor pool
     */
    public RuntimeMethodStructure(MethodConstant identity) {
        this(identity.getNamespace().getComponent(), identity, Access.PUBLIC, true,
                Annotation.NO_ANNOTATIONS, Parameter.NO_PARAMS, Parameter.NO_PARAMS);
        if (!identity.getSignature().getParams().isEmpty()
                || !identity.getSignature().getReturns().isEmpty()) {
            throw new IllegalArgumentException("Initializer must have no parameters or returns");
        }
    }

    /**
     * Build an unattached method with independent parameter definitions. The namespace may be
     * an image declaration or an accessor namespace owned by this executable; it is never edited.
     * Parameter types are adopted into the descriptor pool that owns the method identity.
     */
    RuntimeMethodStructure(Component namespace, MethodConstant identity, Access access,
                           boolean function, Annotation[] annotations,
                           Parameter[] returns, Parameter[] parameters) {
        super(parent(namespace, identity),
                Format.METHOD.ordinal() | access.FLAGS | SYNTHETIC_BIT | (function ? STATIC_BIT : 0),
                identity, null, Constant.registerConstants(identity.getConstantPool(), annotations.clone()),
                copyParameters(identity.getConstantPool(), returns),
                copyParameters(identity.getConstantPool(), parameters), true, false);
    }

    private static MultiMethodStructure parent(Component namespace, MethodConstant identity) {
        var pool = identity.getConstantPool();
        if (pool.hasSerializedIndices()) {
            throw new IllegalArgumentException("Runtime method requires a descriptor pool");
        }
        pool.register(namespace.getIdentityConstant());
        return new MultiMethodStructure(namespace, Format.MULTIMETHOD.ordinal() | Access.PUBLIC.FLAGS,
                identity.getParentConstant(), null) {
            @Override
            public ConstantPool getConstantPool() {
                return pool;
            }
        };
    }

    private static Parameter[] copyParameters(ConstantPool pool, Parameter[] parameters) {
        return Arrays.stream(parameters).map(parameter -> {
            var copy = parameter.cloneBody();
            copy.registerConstants(pool);
            return copy;
        }).toArray(Parameter[]::new);
    }

    /**
     * Supply the lexical parent for an accessor whose host has no declared property. This is
     * executable-local scaffolding, not a new semantic property or a child of the image class.
     */
    static PropertyStructure accessorNamespace(ClassStructure host, PropertyConstant identity,
                                              PropertyStructure source, TypeConstant type) {
        return new PropertyStructure(host,
                Format.PROPERTY.ordinal() | source.getAccess().FLAGS | SYNTHETIC_BIT,
                identity, null, source.getVarAccess(), type) {
            @Override
            public ConstantPool getConstantPool() {
                return identity.getConstantPool();
            }
        };
    }

    @Override
    protected Component getEldestSibling() {
        return this;
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
        var initializers = new HashMap<Integer, PropertyConstant>();
        var ops = getOps();
        for (int index = 0; index < ops.length; index++) {
            if (ops[index].ensureOp() instanceof InitRef init) {
                initializers.put(index, init.field);
            }
        }
        refInitializers = Map.copyOf(initializers);
    }

    @Override
    protected Op[] readCodeOps(DataInput in, Constant[] constants) throws IOException {
        var ops = super.readCodeOps(in, constants);
        refInitializers.forEach((index, field) -> {
            if (!(ops[index] instanceof Nop nop) || nop.getLineCount() != 0) {
                throw new IllegalStateException("Missing initializer placeholder at " + index);
            }
            ops[index] = new InitRef(field);
        });
        return ops;
    }

    @Override
    protected void assemble(DataOutput out) {
        throw new UnsupportedOperationException("Runtime methods are not image declarations");
    }

    /**
     * Non-serializable operands at their assembled addresses. Set during preparation, before
     * this executable is published; execution reads it to recreate callbacks for each service.
     */
    private Map<Integer, PropertyConstant> refInitializers = Map.of();

    /**
     * Assign an inflated field's outer reference at its original initializer position.
     * A NOP occupies that position in the private encoding; readCodeOps restores this callback
     * before scope simulation. Runtime method encodings never enter an XTC image.
     */
    static final class InitRef extends Op {
        InitRef(PropertyConstant field) {
            this.field = field;
        }

        @Override
        public int process(Frame frame, int pc) {
            var target = (GenericHandle) frame.getThis();
            var ref = (RefHandle) target.getField(frame, field);
            ref.setField(frame, GenericHandle.OUTER, target);
            return pc + 1;
        }

        @Override
        public void write(DataOutput out, ConstantRegistry registry) throws IOException {
            out.writeByte(OP_NOP);
        }

        @Override
        public String toString() {
            return "initRef: " + field;
        }

        private final PropertyConstant field;
    }
}

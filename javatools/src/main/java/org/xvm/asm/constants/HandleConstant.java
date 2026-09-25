package org.xvm.asm.constants;

import java.util.Objects;

import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

/**
 * An opaque token for a runtime annotation argument.
 *
 * <p>The owning container's constant heap retains the captured value. Descriptors and semantic
 * tables retain only this token, and cannot resolve it without the exact execution owner.
 * Tokens are neither serializable definitions nor transferable metadata.
 */
public final class HandleConstant
        extends FrameDependentConstant {
    /**
     * Allocate a token in a runtime descriptor context. The caller must retain its value in
     * that context's container heap before publishing the annotated descriptor.
     *
     * @param pool  the capturing container's descriptor pool
     */
    public HandleConstant(ConstantPool pool) {
        super(Objects.requireNonNull(pool, "pool"));
        if (pool.hasSerializedIndices()) {
            throw new IllegalArgumentException("A captured value requires a runtime descriptor owner");
        }
    }

    /**
     * @return true only for the descriptor context that allocated this token
     */
    public boolean isShared(ConstantPool pool) {
        return pool == getConstantPool();
    }

    @Override
    public ObjectHandle getHandle(Frame frame) {
        return frame.f_context.f_container.f_heap.resolveCapture(this);
    }

    @Override
    protected HandleConstant adoptedBy(ConstantPool pool) {
        if (!isShared(pool)) {
            throw new IllegalArgumentException("A capture token cannot be adopted by another pool");
        }
        return this;
    }

    @Override
    public Format getFormat() {
        // Runtime tokens have no serialized representation; reuse the contextual format.
        return Format.Register;
    }

    @Override
    protected int compareDetails(Constant constant) {
        return constant instanceof HandleConstant that ? Long.compare(identity, that.identity) : -1;
    }

    @Override
    public int computeHashCode() {
        return Long.hashCode(identity);
    }

    @Override
    public String getValueString() {
        return "capture(" + identity + ')';
    }

    @Override
    public String getDescription() {
        return getValueString();
    }

    // Only identity allocation is classloader-wide; no type, container or captured value is retained.
    private static final AtomicLong nextIdentity = new AtomicLong();
    private final long identity = nextIdentity.getAndIncrement();
}

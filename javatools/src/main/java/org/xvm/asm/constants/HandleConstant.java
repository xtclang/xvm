package org.xvm.asm.constants;

import java.util.Objects;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.util.Hash;

/**
 * Constant whose purpose is to represent an object handle (run-time only).
 *
 * <p>The handle is the captured value, not a disposable cache of a serialized definition.
 * Copying the constant must preserve that value. Its capture pool therefore remains explicit:
 * an annotated type containing it cannot be promoted into another pool's metadata caches merely
 * because the annotation class and underlying type are known there.
 */
public class HandleConstant
        extends FrameDependentConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param pool    the request/container pool in which the value was captured
     * @param hValue  the handle
     */
    public HandleConstant(ConstantPool pool, ObjectHandle hValue) {
        super(Objects.requireNonNull(pool, "pool"));

        capturePool = pool;
        m_hValue    = Objects.requireNonNull(hValue, "hValue");
    }

    /**
     * Whether this captured runtime value can participate in the pool's local type caches.
     * Explicitly passing a handle across a service boundary does not make its metadata portable.
     *
     * @param pool  the proposed metadata owner
     *
     * @return true only for the pool in which this value was captured
     */
    public boolean isShared(ConstantPool pool) {
        return pool == capturePool;
    }

    // ----- FrameDependentConstant methods --------------------------------------------------------

    @Override
    public ObjectHandle getHandle(Frame frame) {
        return m_hValue;
    }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        // no need to introduce a new format; reuse Register
        return Format.Register;
    }

    @Override
    protected int compareDetails(Constant constant) {
        return -1;
    }

    @Override
    public int computeHashCode() {
        return Hash.of(m_hValue);
    }

    @Override
    public String getValueString() {
        return m_hValue.toString();
    }

    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription() {
        return getValueString();
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The handle.
     */
    private final ObjectHandle m_hValue;

    /** The capture owner, preserved even when an annotation description is copied. */
    private final ConstantPool capturePool;
}

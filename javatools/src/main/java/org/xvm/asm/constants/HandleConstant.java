package org.xvm.asm.constants;


import java.io.DataOutput;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.util.Hash;


/**
 * Constant whose purpose is to represent an object handle (run-time only).
 */
public class HandleConstant
        extends FrameDependentConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param hValue  the handle
     */
    public HandleConstant(ObjectHandle hValue) {
        this(null, hValue);
    }

    private HandleConstant(ConstantPool pool, ObjectHandle hValue) {
        super(pool);

        m_hValue = hValue;
    }


    // ----- FrameDependentConstant methods --------------------------------------------------------

    @Override
    public ObjectHandle getHandle(Frame frame) {
        return getHandleFor(frame.f_context.f_container);
    }

    /**
     * Serve the wrapped live handle to the specified container.
     *
     * <p>The handle is owner-specific runtime state. Serving it raw to any container whose pool
     * happens to resolve this constant would hand one container's live object to a sibling,
     * bypassing the maskAs/proxy isolation machinery entirely - two containers loaded over one
     * module share the module's pool, which is exactly the same-JVM reuse scenario. The owner may
     * always retrieve its own handle; any other container may only receive what could legally
     * cross a service boundary anyway: a non-service, pass-through (immutable, type-shared) value.
     *
     * @param container  the requesting container
     *
     * @return the handle
     *
     * @throws IllegalStateException if serving the handle would leak owner-specific runtime state
     *         across containers
     */
    public ObjectHandle getHandleFor(Container container) {
        ObjectHandle hValue = m_hValue;
        Container    owner  = hValue instanceof ObjectHandle.GenericHandle hGeneric
                ? hGeneric.getOwner()
                : null;
        if (owner != container && (hValue.isService() || !hValue.isPassThrough(container))) {
            throw new IllegalStateException("live handle owned by " + owner
                    + " cannot be served raw to container " + container
                    + ": " + hValue);
        }
        return hValue;
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
    protected Constant copyForAdoption(AdoptionContext context) {
        if (getContaining() == null) {
            // Runtime annotation construction creates a fresh, unowned HandleConstant and then
            // registers it in the current pool. Moving an already-owned live ObjectHandle to a
            // different pool would leak frame/container-owned runtime state.
            return new HandleConstant(context.pool(), m_hValue);
        }

        throw new IllegalStateException(
                "HandleConstant wraps a live ObjectHandle and cannot be adopted into "
                        + context.pool());
    }

    @Override
    protected void assemble(DataOutput out) {
        // the inherited implementation would write only the borrowed Register format byte,
        // producing a structurally corrupt module if a runtime-only handle constant were ever
        // referenced at serialization time; failing loudly beats persisting garbage
        throw new IllegalStateException(
                "HandleConstant is runtime-only and cannot be persisted: " + getValueString());
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
}

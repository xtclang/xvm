package org.xvm.asm.constants;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * Constant whose purpose is to represent a run-time action based on the current frame.
 */
public abstract class FrameDependentConstant
        extends Constant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param pool  the ConstantPool that will contain this Constant
     */
    public FrameDependentConstant(ConstantPool pool) {
        super(pool);
    }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Obtain the ObjectHandle for this FrameDependentConstant.
     *
     * @return the ObjectHandle (can be a DeferredCallHandle)
     */
    public abstract ObjectHandle getHandle(Frame frame);

    @Override
    protected boolean allowsDefaultAdoptionClone() {
        // Transitional policy: MethodBindingConstant is a serialized frame-dependent descriptor.
        // RegisterConstant has a hook because it can carry a transient compiler Register, and
        // HandleConstant has a guard because it wraps a live ObjectHandle.
        return true;
    }
}

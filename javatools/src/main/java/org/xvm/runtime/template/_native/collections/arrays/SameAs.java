package org.xvm.runtime.template._native.collections.arrays;

/**
 * Identity comparison against a handle of the implementor's own type.
 *
 * <p>Array identity is a binary operation: it depends on the runtime type of BOTH operands, and
 * Java cannot state "these two values have the same type" in a signature. The previous shape -
 * {@code compareIdentity(ObjectHandle, ObjectHandle)} on the template - promised to accept any two
 * handles and then cast both, which threw {@link ClassCastException} for most pairs and produced
 * master bugs 37 and 38.
 *
 * <p>Implementing this interface with the implementor's own type as {@code SELF} moves the type
 * into the signature, so {@link #sameAs} receives an operand it can use directly. The single
 * unchecked crossing lives in {@link xRTDelegate.DelegateHandle#isIdenticalTo}, immediately after a
 * {@code getClass()} equality test that proves it, and the compiler emits a bridge that checkcasts
 * anyway.
 *
 * @param <SELF> the implementing handle's own type
 */
public interface SameAs<SELF> {
    /**
     * @param that another handle, guaranteed by the caller to be of this handle's exact class
     *
     * @return true iff the two handles have the same identity
     */
    boolean sameAs(SELF that);
}

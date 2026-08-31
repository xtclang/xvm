package org.xvm.runtime;


/**
 * Typed implementations of the {@link OpSupport} operator protocol, bound per template.
 *
 * <p>{@code OpSupport} declares twenty operators, every one taking {@code ObjectHandle} for its
 * receiver and argument, and the twelve templates implementing them all begin by casting those
 * back to the type they already know:</p>
 *
 * <pre>
 * long l1 = ((JavaLong) hTarget).getValue();
 * long l2 = ((JavaLong) hArg).getValue();
 * </pre>
 *
 * <p>Java cannot narrow an override's parameter type, so the check has to live somewhere. The
 * alternatives each cost something the codebase should not pay: moving the operation onto the
 * handle cannot work where one handle class serves many templates - {@code JavaLong} backs a dozen
 * integer types with different overflow rules - and making the protocol generic pushes unchecked
 * casts into the call sites, which hold their template as an unparameterized {@code OpSupport}.</p>
 *
 * <p>Binding a typed handler instead keeps the operation on its template, leaves the call sites
 * untouched, needs no unchecked cast anywhere, and works for shared handles because the table is
 * per template rather than per handle class. It costs one registration per operator, and an
 * indirection measured to be within noise of the virtual call it replaces.</p>
 */
public final class OperatorBinding {
    private OperatorBinding() {}

    /**
     * The operators, used as a table index. Not every one is bindable yet; an operator with no
     * binding falls through to the behaviour it has today.
     */
    public enum Op {
        ADD, SUB, MUL, DIV, MOD, SHL, SHR, SHR_ALL, AND, OR, XOR, NEG, COMPL, NEXT, PREV
    }

    /**
     * An operator taking a receiver and one argument.
     *
     * @param <T>  the receiver's handle type
     * @param <A>  the argument's handle type
     */
    @FunctionalInterface
    public interface Binary<T, A> {
        int invoke(Frame frame, T hTarget, A hArg, int iReturn);
    }

    /**
     * An operator taking only a receiver.
     *
     * @param <T>  the receiver's handle type
     */
    @FunctionalInterface
    public interface Unary<T> {
        int invoke(Frame frame, T hTarget, int iReturn);
    }

    /** One bound binary operator, with the conversion its handler's types imply. */
    interface BoundBinary {
        int dispatch(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);
    }

    /** One bound unary operator. */
    interface BoundUnary {
        int dispatch(Frame frame, ObjectHandle hTarget, int iReturn);
    }

    static <T, A> BoundBinary bind(NativeType<T> self, NativeType<A> arg, Binary<T, A> handler) {
        return (frame, hTarget, hArg, iReturn) ->
                handler.invoke(frame, self.cast(hTarget), arg.cast(hArg), iReturn);
    }

    static <T> BoundUnary bind(NativeType<T> self, Unary<T> handler) {
        return (frame, hTarget, iReturn) -> handler.invoke(frame, self.cast(hTarget), iReturn);
    }
}

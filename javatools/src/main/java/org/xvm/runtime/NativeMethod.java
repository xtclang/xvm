package org.xvm.runtime;


import org.xvm.asm.MethodStructure;


/**
 * Typed implementations of native methods, bound at link time.
 *
 * <p>The runtime knows which native method it is looking at twice over, and throws both away.
 * {@code markNativeMethod} locates the {@link MethodStructure} by name and parameter types and
 * records only {@code m_fNative = true}; the invocation path then recovers the identity with
 * {@code switch (method.getName())} - 76 such switches carrying 542 string labels - and recovers
 * the parameter types with hand-written casts.</p>
 *
 * <p>Binding one of these at declaration keeps both. The handler's parameters are the handle types
 * the declaration already named, so its body needs no cast at all, and the compiler checks it.</p>
 */
public final class NativeMethod {
    private NativeMethod() {}

    /**
     * A native method taking one argument.
     *
     * @param <T>  the receiver's handle type
     * @param <A>  the argument's handle type
     */
    @FunctionalInterface
    public interface One<T extends ObjectHandle, A extends ObjectHandle> {
        int invoke(Frame frame, T hTarget, A hArg, int iReturn);
    }

    /**
     * A native property getter. Keyed by property name rather than by {@link MethodStructure},
     * because that is what {@code invokeNativeGet} dispatches on.
     *
     * @param <T>  the receiver's handle type
     */
    @FunctionalInterface
    public interface Getter<T extends ObjectHandle> {
        int get(Frame frame, T hTarget, int iReturn);
    }

    /**
     * One bound native: the typed handler plus the handle types to convert through.
     *
     * <p>Package-private because only {@link ClassTemplate} binds and dispatches these.</p>
     */
    record Bound1<T extends ObjectHandle, A extends ObjectHandle>(
            NativeType<T> self, NativeType<A> arg, One<T, A> handler) {
        int dispatch(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
            return handler.invoke(frame, self.cast(hTarget), arg.cast(hArg), iReturn);
        }
    }

    /** One bound property getter. */
    record BoundGet<T extends ObjectHandle>(NativeType<T> self, Getter<T> getter) {
        int dispatch(Frame frame, ObjectHandle hTarget, int iReturn) {
            return getter.get(frame, self.cast(hTarget), iReturn);
        }
    }

    /**
     * A native method taking no arguments.
     *
     * @param <T>  the receiver's handle type
     */
    @FunctionalInterface
    public interface Zero<T extends ObjectHandle> {
        int invoke(Frame frame, T hTarget, int iReturn);
    }

    /**
     * A native method taking two arguments.
     *
     * @param <T>  the receiver's handle type
     * @param <A>  the first argument's handle type
     * @param <B>  the second argument's handle type
     */
    @FunctionalInterface
    public interface Two<T extends ObjectHandle, A extends ObjectHandle, B extends ObjectHandle> {
        int invoke(Frame frame, T hTarget, A hArg1, B hArg2, int iReturn);
    }

    /**
     * A native method taking two arguments and producing several return values.
     *
     * @param <T>  the receiver's handle type
     * @param <A>  the first argument's handle type
     * @param <B>  the second argument's handle type
     */
    @FunctionalInterface
    public interface TwoToMany<T extends ObjectHandle, A extends ObjectHandle, B extends ObjectHandle> {
        int invoke(Frame frame, T hTarget, A hArg1, B hArg2, int[] aiReturn);
    }

    /**
     * One bound native on the {@code invokeNativeN} protocol: any arity, one return value. The
     * arity-specific typing happens in the {@code bind} factories, so a single stored type serves
     * every arity.
     */
    interface BoundN {
        int dispatch(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn);
    }

    /** One bound native on the {@code invokeNativeNN} protocol: any arity, several returns. */
    interface BoundNN {
        int dispatch(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn);
    }

    /**
     * Bind a zero-argument native. The receiver may legitimately be null on this protocol, and
     * {@link Class#cast} passes null through, so that stays working.
     */
    static <T extends ObjectHandle> BoundN bind(NativeType<T> self, Zero<T> handler) {
        return (frame, hTarget, ahArg, iReturn) ->
                handler.invoke(frame, self.cast(hTarget), iReturn);
    }

    /** Bind a two-argument native. */
    static <T extends ObjectHandle, A extends ObjectHandle, B extends ObjectHandle> BoundN bind(
            NativeType<T> self, NativeType<A> arg1, NativeType<B> arg2, Two<T, A, B> handler) {
        return (frame, hTarget, ahArg, iReturn) ->
                handler.invoke(frame, self.cast(hTarget), arg1.cast(ahArg[0]), arg2.cast(ahArg[1]),
                        iReturn);
    }

    /** Bind a two-argument native that produces several return values. */
    static <T extends ObjectHandle, A extends ObjectHandle, B extends ObjectHandle> BoundNN bindNN(
            NativeType<T> self, NativeType<A> arg1, NativeType<B> arg2, TwoToMany<T, A, B> handler) {
        return (frame, hTarget, ahArg, aiReturn) ->
                handler.invoke(frame, self.cast(hTarget), arg1.cast(ahArg[0]), arg2.cast(ahArg[1]),
                        aiReturn);
    }
}

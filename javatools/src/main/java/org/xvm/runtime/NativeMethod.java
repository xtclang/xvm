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
     * A native method taking no arguments.
     *
     * @param <T>  the receiver's handle type
     */
    @FunctionalInterface
    public interface Zero<T extends ObjectHandle> {
        int invoke(Frame frame, T hTarget, int iReturn);
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
}

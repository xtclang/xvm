package org.xvm.runtime;


/**
 * Binds an Ecstasy type name to the Java handle class that represents it.
 *
 * <p>Templates already declare the Ecstasy types of a native method's parameters, at link time, in
 * {@code markNativeMethod("match", STRING, null)} - where {@code STRING} is
 * {@code {"text.String"}}. That declaration says the argument is a {@code text.String}, and the
 * corresponding Java type is {@code StringHandle}, but nothing recorded the correspondence. So
 * every invocation re-asserted it by hand, with {@code ((StringHandle) hArg)}, at each of the 542
 * {@code case "…"} labels the runtime dispatches natives through.</p>
 *
 * <p>Carrying the handle class alongside the type name is what lets a native method body be typed
 * by the compiler instead: the conversion happens once, here, as a checked {@link Class#cast},
 * and the handler receives handles that are already the right Java type.</p>
 *
 * @param <H>  the handle class representing this Ecstasy type
 */
public record NativeType<H extends ObjectHandle>(String typeName, Class<H> handleClass) {
    /**
     * @param typeName     the Ecstasy type name, as it appears in {@code markNativeMethod}
     * @param handleClass  the handle class representing that type
     */
    public static <H extends ObjectHandle> NativeType<H> of(String typeName, Class<H> handleClass) {
        return new NativeType<>(typeName, handleClass);
    }

    /**
     * @return this type name in the single-element array form {@code markNativeMethod} takes
     */
    public String[] asParamTypes() {
        return new String[] {typeName};
    }

    /**
     * Convert a handle to this type's Java representation.
     *
     * @param handle  the handle to convert
     *
     * @return the handle, typed
     *
     * @throws ClassCastException if the handle is not of the declared type, which means the
     *         Ecstasy declaration and the Java binding disagree
     */
    public H cast(ObjectHandle handle) {
        return handleClass.cast(handle);
    }
}

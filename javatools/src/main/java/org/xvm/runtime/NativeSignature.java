package org.xvm.runtime;


/**
 * Which part of a native method's signature is needed to identify it.
 *
 * <p>{@code markNativeMethod(sName, asParamType, asRetType)} treats both arrays as optional
 * filters, and expresses "no filter" as {@code null}. Of 392 declarations in the tree, 140 pass
 * {@code null} for both - the name alone identifies the method - 58 pass it for the parameters
 * only, and 38 for the returns only. So the majority of arguments at those call sites are not
 * values but absences.</p>
 *
 * <p>Overloads cannot fix that directly, because both filters are {@code String[]} and a
 * parameters-only call would be indistinguishable from a returns-only one. Naming the combination
 * does fix it: each factory below says which filters apply, callers pass no nulls, and the
 * {@code null} the underlying lookup still expects is produced in one place.</p>
 */
public record NativeSignature(String[] paramTypes, String[] returnTypes) {
    /**
     * Note that BOTH components are deliberately nullable here, and only here: this record exists
     * to be the one place the underlying lookup's "no filter means null" convention is expressed,
     * so that no caller has to.
     */
    /** No filter: the method name alone identifies the native. */
    public static final NativeSignature BY_NAME = new NativeSignature(null, null);

    /**
     * @param asParamType  the parameter types that identify the intended overload
     */
    public static NativeSignature params(String... asParamType) {
        return new NativeSignature(asParamType, null);
    }

    /**
     * @param asRetType  the return types that identify the intended overload
     */
    public static NativeSignature returns(String... asRetType) {
        return new NativeSignature(null, asRetType);
    }

    /**
     * @param asParamType  the parameter types
     * @param asRetType    the return types
     */
    public static NativeSignature of(String[] asParamType, String[] asRetType) {
        return new NativeSignature(asParamType, asRetType);
    }
}

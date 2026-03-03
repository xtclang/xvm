package org.xvm.javajit;

/**
 * Describes a JIT representation of a given Ecstasy type.
 */
public enum JitFlavor {
    /**
     * A non-widened Java type (nObj or below) representing a specific xtc type (e.g. Person); most
     * probably {@link org.xvm.asm.constants.TypeConstant#isSingleUnderlyingClass}
     * <p>
     * Implication is that it uses a single slot and no casts or transformations are required.
     */
    Specific(false, null),

    /**
     * A parameter of the {@link #Specific} flavor with a default value.
     * <p>
     * Implication is that a Java `null` value is allowed to passed in, indicating that the default
     * value should be used.
     * <p>
     * Note: this flavor can only be used by the {@link JitMethodDesc}
     */
    SpecificWithDefault(false, Specific),

    /**
     * A widened Java type (nObj or below); most probably a
     * {@link org.xvm.asm.constants.RelationalTypeConstant} (e.g. Person|String)
     * <p>
     * Implication is that it uses a single slot and casts are required.
     */
    Widened(false, null),

    /**
     * A parameter of the {@link #Widened} flavor with a default value.
     * <p>
     * Implication is that a `null` value is allowed to passed in, indicating that the default
     * value should be used.
     * <p>
     * Note: this flavor can only be used by the {@link JitMethodDesc}
     */
    WidenedWithDefault(false, Widened),

    /**
     * A Java primitive type (e.g. boolean, int, long) that represents an xtc value type (e.g.
     * Boolean, Char, Int)
     * <p>
     * Implication is that it uses a single slot and transformations (boxing/unboxing) may be
     * required.
     */
    Primitive(true, null),

    /**
     * A parameter of the {@link #Primitive} flavor with a default value.
     * <p>
     * Implication is that an additional `boolean` value is used, indicating that the default
     * value should be used.
     * <p>
     * Note: this flavor can only be used by the {@link JitMethodDesc}
     */
    PrimitiveWithDefault(true, Primitive),

    /**
     * Double-slot Java primitives (e.g. (boolean, boolean), (int, boolean), (long, boolean)) that
     * represents a `Nullable` xtc value type (e.g. Boolean?, Char?, Int?)
     * <p>
     * Implication is that an additional `boolean` value is used, indicating that the value is Null.
     */
    NullablePrimitive(true, null),

    /**
     * A parameter of the {@link #NullablePrimitive} flavor with a default value.
     *
     * Implication is that additional `int` values is used, -1 indicating that the default value
     * should be used and 1 indicating Null.
     * <p>
     * Note: this flavor can only be used by the {@link JitMethodDesc}
     */
    NullablePrimitiveWithDefault(true, NullablePrimitive),

    /**
     * Multi-slot Java primitives that represent a custom XVM primitive. For example, Int128,
     * UInt128, or any other type that could be decomposed into multiple Java primitives.
     * <p>
     * The implication is that it uses multiple slots and transformations (boxing/unboxing) may be
     * required.
     */
    XvmPrimitive(true, null),

    /**
     * Multi-slot Java primitives that represent a custom XVM primitive with a default value. For
     * example, Int128, UInt128, or any other type that could be decomposed into multiple Java
     * primitives.
     * <p>
     * Implication is that an additional `boolean` value is used, indicating that the default
     * value should be used.
     * <p>
     * Note: this flavor can only be used by the {@link JitMethodDesc}
     */
    XvmPrimitiveWithDefault(true, XvmPrimitive),

    /**
     * Multi-slot Java primitives that represent a `Nullable` custom XVM primitive. For example,
     * Int128, UInt128, or any other type that could be decomposed into multiple Java primitives.
     * <p>
     * Implication is that an additional `boolean` value is used, indicating that the value is Null.
     */
    NullableXvmPrimitive(true, null),

    /**
     * Multi-slot Java primitives that represent a custom XVM primitive with a default value. For
     * example, Int128, UInt128, or any other type that could be decomposed into multiple Java
     * primitives.
     * <p>
     * Implication is that additional `int` values is used, -1 indicating that the default value
     * should be used and 1 indicating Null.
     * <p>
     * Note: this flavor can only be used by the {@link JitMethodDesc}
     */
    NullableXvmPrimitiveWithDefault(true, NullableXvmPrimitive),

    /**
     * Nullable.Null value.
     */
    AlwaysNull(false, null)
    ;

    JitFlavor(boolean canOptimize, JitFlavor baseFlavor) {
        this.canOptimize = canOptimize;
        this.baseFlavor  = baseFlavor;
    }

    public final boolean   canOptimize;
    public final JitFlavor baseFlavor;
}

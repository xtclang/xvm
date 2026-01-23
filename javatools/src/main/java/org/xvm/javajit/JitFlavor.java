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
    Specific(false, 1),

    /**
     * A widened Java type (nObj or below); most probably a
     * {@link org.xvm.asm.constants.RelationalTypeConstant} (e.g. Person|String)
     * <p>
     * Implication is that it uses a single slot and casts are required.
     */
    Widened(false, 1),

    /**
     * A Java primitive type (e.g. boolean, int, long) that represents an xtc value type (e.g.
     * Boolean, Char, Int)
     * <p>
     * Implication is that it uses a single slot and transformations (boxing/unboxing) may be
     * required.
     */
    Primitive(true, 1),

    /**
     * Double-slot Java primitives (e.g. (boolean, boolean), (int, boolean), (long, boolean)) that
     * represents a `Nullable` xtc value type (e.g. Boolean?, Char?, Int?)
     * <p>
     * Implication is that it uses multiple slots and transformations (boxing/unboxing) may be
     * required.
     *
     * TODO: we don't need two slots to represent an Int16? or Boolean?
     */
    NullablePrimitive(true, 2),

    /**
     * Double-slot Java primitives that represent Int128, UInt128 or any other type that could be
     * decomposed into two longs.
     */
    DoubleLong(true, 2),

    /**
     * A parameter of the {@link #Specific} flavor with a default value.
     *
     * Implication is that a Java `null` value is allowed to passed in, indicating that the default
     * value should be used.
     */
    SpecificWithDefault(false, 1),

    /**
     * A parameter of the {@link #Widened} flavor with a default value.
     *
     * Implication is that a `null` value is allowed to passed in, indicating that the default
     * value should be used.
     */
    WidenedWithDefault(false, 1),

    /**
     * A parameter of the {@link #Primitive} flavor with a default value.
     *
     * Implication is that an additional `boolean` value is used, indicating that the default
     * value should be used.
     */
    PrimitiveWithDefault(true, 2),

    AlwaysNull(false, 0)           // Null TODO: do we need it?
    ;

    JitFlavor(boolean canOptimize, int slotCount) {
        this.canOptimize = canOptimize;
        this.slotCount   = slotCount;
    }

    public final boolean canOptimize;
    public final int slotCount;
}

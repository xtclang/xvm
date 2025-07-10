package org.xvm.javajit;

/**
 * Describes a JIT representation of a given Ecstasy type.
 */
public enum JitFlavor {
    /**
     * A non-widened Java type (xObj or below) representing a specific xtc type (e.g. Person); most
     * probably {@link org.xvm.asm.constants.TypeConstant#isSingleUnderlyingClass}
     * <p>
     * Implication is that it uses a single slot and no casts or transformations are required.
     */
    Specific(false, false),

    /**
     * A widened Java type (xObj or below); most probably a
     * {@link org.xvm.asm.constants.RelationalTypeConstant} (e.g. Person|String)
     * <p>
     * Implication is that it uses a single slot and casts are required.
     */
    Widened(false, false),

    /**
     * A Java primitive type (e.g. boolean, int, long) that represents an xtc value type (e.g.
     * Boolean, Char, Int)
     * <p>
     * Implication is that it uses a single slot and transformations (boxing/unboxing) may be
     * required.
     */
    Primitive(true, false),

    /**
     * Multi-slot Java primitives (e.g. (boolean, boolean), (int, boolean), (long, boolean)) that
     * represents a `Nullable` xtc value type (e.g. Boolean?, Char?, Int?)
     * <p>
     * Implication is that it uses multiple slots and transformations (boxing/unboxing) may be
     * required.
     */
    MultiSlotPrimitive(true, true),

    /**
     * A parameter of the {@link #Specific} flavor with a default value.
     *
     * Implication is that a `null` value is allowed to passed in, indicating that the default
     * value should be used.
     */
    SpecificWithDefault(false, false),

    /**
     * A parameter of the {@link #Widened} flavor with a default value.
     *
     * Implication is that a `null` value is allowed to passed in, indicating that the default
     * value should be used.
     */
    WidenedWithDefault(false, false),

    /**
     * A parameter of the {@link #Primitive} flavor with a default value.
     *
     * Implication is that an additional `boolean` value is used, indicating that the default
     * value should be used.
     */
    PrimitiveWithDefault(true, true),

    AlwaysNull(false, false)           // Null TODO: do we need it?
    ;

    JitFlavor(boolean canOptimize, boolean multiSlot) {
        this.canOptimize = canOptimize;
        this.multiSlot = multiSlot;
    }

    public final boolean canOptimize;
    public final boolean multiSlot;
}

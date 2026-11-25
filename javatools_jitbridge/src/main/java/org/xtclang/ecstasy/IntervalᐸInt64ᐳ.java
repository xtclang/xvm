package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitFromNatural;
import org.xvm.javajit.annotations.JitGenerated;

/**
 * Native interface for the specialized {@code ecstasy.Interval<Int64>}.
 */
@JitGenerated
public interface IntervalᐸInt64ᐳ {
    /**
     * The primitive declaration for the property:
     * {@code Element effectiveFirst.get()}.
     */
    @JitFromNatural
    long effectiveFirst$get$p(Ctx ctx);

    /**
     * The primitive declaration for the property:
     * {@code Element effectiveLast.get()}.
     */
    @JitFromNatural
    long effectiveLast$get$p(Ctx ctx);

    /**
     * The primitive declaration for the property:
     * {@code Element effectiveLowerBound.get()}.
     */
    @JitFromNatural
    long effectiveLowerBound$get$p(Ctx ctx);

    /**
     * The primitive declaration for the property:
     * {@code Element effectiveUpperBound.get()}.
     */
    @JitFromNatural
    long effectiveUpperBound$get$p(Ctx ctx);

    /**
     * The primitive declaration for the property:
     * {@code Boolean empty.get()}.
     */
    @JitFromNatural
    boolean empty$get$p(Ctx ctx);

    /**
     * The primitive declaration for the property:
     * {@code Int size.get()}.
     */
    @JitFromNatural
    long size$get$p(Ctx ctx);

    /**
     * The primitive declaration for the method:
     * {@code @Op("[]") Element getElement(Int index)}.
     */
    @JitFromNatural
    long getElement$p(Ctx ctx, long index);
}

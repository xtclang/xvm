package org.xvm.asm;


import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.InlineCache;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OpInfoKey;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;


/**
 * The equality-dispatch path, with the selected composition memoized per op site.
 *
 * <p>{@link TypeConstant#callEquals} re-derives which composition a comparison dispatches through
 * on every call, and the derivation is two structural {@code equals} comparisons. Profiling put
 * that selection at the dominant share of a hot equality path, and a measurement of the miss
 * population ({@code docs/reentrancy/iseq-canonicalization-measurement.md}) ruled out the cheaper
 * alternative: canonicalizing the op's type into the container's pool cannot help there, because
 * the misses are comparisons through a SUPERTYPE or an enum type rather than pool artifacts. A
 * cache covers both cases, since it skips the selection whichever branch would have run.</p>
 *
 * <p>Lives here, rather than on {@code OpTest} where it started, because the equality ops do not
 * share a base: {@code IsEq}/{@code IsNotEq} extend {@code OpTest} and {@code JumpEq}/
 * {@code JumpNotEq} extend {@code OpCondJump}. One implementation serving both beats the same
 * fifteen lines in two places, which is how a fix ends up applied to only one of them.</p>
 */
public final class EqualsDispatch {
    private EqualsDispatch() {}

    /**
     * Compare two handles for equality, memoizing the composition the comparison dispatches
     * through.
     *
     * <p>The key is (resolved type, composition, composition) and all three parts are load-bearing.
     * {@code Frame.resolveType} depends on the frame's generics and {@code this}, so the same op
     * yields different types in different frames; a composition-pair key alone was measured
     * returning a STALE composition 0.045% of the time, which is a wrong-template dispatch rather
     * than a slow path. The container is implicit - the cache lives in the executing service.</p>
     *
     * @param op       the comparing op, which identifies the cache site
     * @param frame    the current frame
     * @param type     the resolved comparison type
     * @param hValue1  the first value
     * @param hValue2  the second value
     * @param iReturn  where to put the Boolean result
     *
     * @return one of {@code Op.R_NEXT}, {@code Op.R_CALL} or {@code Op.R_EXCEPTION}
     */
    public static int callEquals(Op op, Frame frame, TypeConstant type,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        if (hValue1 == hValue2) {
            return frame.assignValue(iReturn, xBoolean.trueHandle(frame));
        }

        // the six TypeConstant subclasses that override callEquals select no single composition,
        // so they opt out rather than have this try to detect them
        if (!type.isEqualsSelectionStable()) {
            return type.callEquals(frame, hValue1, hValue2, iReturn);
        }

        ServiceContext  context = frame.f_context;
        TypeComposition clz1    = hValue1.getComposition();
        TypeComposition clz2    = hValue2.getComposition();

        InlineCache<TypeComposition> cache = context.getOpInfo(op, INFO_EQUALS_CACHE);
        if (cache != null) {
            TypeComposition cached = cache.match(type, clz1, clz2);
            if (cached != null) {
                InlineCache.recordHit(context, op, INFO_EQUALS_CACHE, cache);
                return cached.getTemplate().callEquals(frame, cached, hValue1, hValue2, iReturn);
            }
            if (cache.isMegamorphic()) {
                return type.callEquals(frame, hValue1, hValue2, iReturn);
            }
        }

        TypeComposition clz = type.selectEqualsComposition(frame, clz1, clz2);
        if (clz == null) {
            return frame.raiseException(TypeConstant.noCommonEqualsType(clz1, clz2));
        }

        InlineCache.recordMiss(context, op, INFO_EQUALS_CACHE, cache, type, clz1, clz2, clz);
        return clz.getTemplate().callEquals(frame, clz, hValue1, hValue2, iReturn);
    }

    /**
     * The single category these sites cache under. One category serves every equality op, because
     * the op-info cache is keyed by the OP as well - so two sites never collide.
     */
    private enum Category {EqualsComposition}

    private static final OpInfoKey<InlineCache<TypeComposition>> INFO_EQUALS_CACHE =
            OpInfoKey.ofGeneric(Category.EqualsComposition, InlineCache.class);
}

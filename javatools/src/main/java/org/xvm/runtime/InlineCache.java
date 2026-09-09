package org.xvm.runtime;


import org.xvm.asm.Op;


/**
 * A monomorphic inline cache for a single {@link Op}, held in the executing service's op-info
 * cache rather than on the op itself - decoded ops are shared across containers, so a runtime
 * write on an op would publish one container's resolution to all of them.
 *
 * <p>An entry records the <em>shape</em> the op last resolved for, the value it resolved, and how
 * many times in a row the shape has since differed. Shapes are compared, never dereferenced, so
 * they are typed as {@code Object}; up to three participate, which covers the sites that need a
 * composite key. Unused slots are null, and a match requires the unused slots to still be null so
 * that a one-part key can never be satisfied by a three-part entry.</p>
 *
 * <h2>Why the miss count exists</h2>
 *
 * <p>A monomorphic cache that rewrites unconditionally on every miss is worse than no cache at
 * all: at a site alternating between two shapes it never once hits, yet still pays a map write per
 * call and re-resolves regardless. Past {@link #MEGAMORPHIC_THRESHOLD} consecutive misses a site
 * stops writing back, so the degenerate case costs no more than not caching.</p>
 *
 * <p>The count is of <em>consecutive</em> misses and is cleared by a hit. Counting total misses
 * would let a site that is overwhelmingly one shape accumulate its way to the threshold over a
 * long enough run and deoptimize itself permanently - a regression for exactly the sites the
 * cache serves best. A steady monomorphic site has a zero count and writes nothing.</p>
 *
 * @param shape1  the first part of the shape this entry resolved for
 * @param shape2  the second part, or null
 * @param shape3  the third part, or null
 * @param value   the resolved value; never null
 * @param misses  consecutive misses since the last hit
 *
 * @param <V> the resolved value's type
 */
public record InlineCache<V>(Object shape1, Object shape2, Object shape3, V value, int misses) {
    /**
     * The number of consecutive misses after which a site stops writing back.
     */
    public static final int MEGAMORPHIC_THRESHOLD = 8;

    /**
     * @return the cached value if this entry resolved for exactly this shape, else null
     */
    public V match(Object s1) {
        return shape1 == s1 && shape2 == null && shape3 == null ? value : null;
    }

    /**
     * @return the cached value if this entry resolved for exactly this pair of shapes, else null
     */
    public V match(Object s1, Object s2) {
        return shape1 == s1 && shape2 == s2 && shape3 == null ? value : null;
    }

    /**
     * @return the cached value if this entry resolved for exactly this triple of shapes, else null
     */
    public V match(Object s1, Object s2, Object s3) {
        return shape1 == s1 && shape2 == s2 && shape3 == s3 ? value : null;
    }

    /**
     * As {@link #match(Object)}, but comparing by equality. For value-like shapes such as a
     * TypeConstant, which can be distinct but equal instances across pools, identity would miss
     * where the resolution is in fact reusable.
     *
     * @return the cached value if this entry resolved for an equal shape, else null
     */
    public V matchEqual(Object s1) {
        return shape2 == null && shape3 == null && shape1.equals(s1) ? value : null;
    }

    /**
     * @return true iff this site has missed often enough in a row to stop writing back
     */
    public boolean isMegamorphic() {
        return misses >= MEGAMORPHIC_THRESHOLD;
    }

    /**
     * Record that {@code entry} was just hit, clearing any accumulated miss count. Writes only
     * when the count is non-zero, so the steady monomorphic path stays read-only.
     */
    public static <V> void recordHit(ServiceContext ctx, Op op, OpInfoKey<InlineCache<V>> key,
                                     InlineCache<V> entry) {
        if (entry.misses != 0) {
            ctx.setOpInfo(op, key,
                    new InlineCache<>(entry.shape1, entry.shape2, entry.shape3, entry.value, 0));
        }
    }

    /**
     * Record a freshly resolved value against the shape it was resolved for, advancing the
     * consecutive-miss count carried by {@code prev} (which may be null on first use). Callers
     * must not call this once {@link #isMegamorphic} holds - that is what stops the write-back.
     *
     * <p>The moment a site deoptimizes would be worth tracing, and it is a natural single-shot
     * event: the entry is written with the count at the threshold, after which callers take the
     * megamorphic path and never reach here again for that op. It is deliberately not wired yet,
     * because neither sink available at this point is right. A static slf4j Logger would be
     * ambient state, which is what reaching the diagnostic sink by ownership exists to avoid; and
     * the owning {@code ErrorListener}, which is reachable through the context's container, would
     * be worse - a root container defaults to {@code ErrorListener.RUNTIME}, whose {@code log}
     * prints anything below ERROR to {@code System.out}, so an INFO per megamorphic site would
     * spam stdout on every run with no way to silence it. Tracing this wants a level the runtime
     * listener discards by default.</p>
     */
    public static <V> void recordMiss(ServiceContext ctx, Op op, OpInfoKey<InlineCache<V>> key,
                                      InlineCache<V> prev, Object s1, Object s2, Object s3,
                                      V value) {
        ctx.setOpInfo(op, key,
                new InlineCache<>(s1, s2, s3, value, prev == null ? 0 : prev.misses + 1));
    }
}

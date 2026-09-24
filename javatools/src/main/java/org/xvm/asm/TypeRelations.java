package org.xvm.asm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.function.Supplier;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeConstant.Relation;

import org.xvm.util.Hasher;
import org.xvm.util.TransientThreadLocal;

import org.xvm.util.concurrent.ConcurrentHasherMap;

import static java.util.Objects.requireNonNull;

/**
 * Completed assignability results for one constant owner, separate from descriptor identity and
 * the recursion state of a calculation. Image pools use this during compilation; a runtime
 * descriptor context owns an independent instance through its factory adapter.
 *
 * <p>Operands must be canonicalized into the selected owner before querying this table. The owner
 * fixes the definition graph; access modifiers and type arguments are part of the operand keys.
 * Context-sensitive calculations bypass memoization. This class does not make concurrent mutation
 * of compiler declarations safe: callers must still serialize mutation and invalidate metadata.
 */
public final class TypeRelations {
    public TypeRelations(ConstantPool owner) {
        this.owner = requireNonNull(owner);
    }

    /**
     * Calculate a relation, optionally reusing a completed result.
     *
     * <p>A recursive query returns the type algebra's conservative {@code INCOMPATIBLE} answer
     * only to its caller. A nested result that depends on an active ancestor is not published.
     * Once that ancestor finishes, its own result has no unresolved recursive assumption and
     * can be cached. In-progress state is local to this thread's calculation and removed on success
     * or failure. Other threads can calculate independently; they cannot observe provisional values.
     *
     * @param right        the canonical source type, owned by this table's pool
     * @param left         the canonical destination type, owned by this table's pool
     * @param cacheable    false when an external resolution context affects the answer
     * @param calculation the uncached type algebra; it must return a completed relation
     * @return the completed relation, or the conservative answer for a recursive dependency
     */
    public Relation calculate(TypeConstant right, TypeConstant left, boolean cacheable,
                              Supplier<Relation> calculation) {
        if (right.getConstantPool() != owner || left.getConstantPool() != owner) {
            throw new IllegalArgumentException("Relation operands must belong to the selected owner");
        }
        return calculateImpl(right, left, cacheable, calculation);
    }

    /**
     * Calculate a compiler-only relation containing unresolved operands, which registration cannot
     * adopt. Retain those operands only for this calculation, never in completed results. The
     * image owner's guard still participates so recursion through resolved queries is detected.
     *
     * @param right        the source type, owned by this table's image pool
     * @param left         the required type, possibly an unadoptable type in another compiler pool
     * @param calculation the uncached type algebra
     * @return the provisional compiler relation
     */
    public Relation calculateUnresolved(TypeConstant right, TypeConstant left,
                                        Supplier<Relation> calculation) {
        if (!owner.hasSerializedIndices() || right.getConstantPool() != owner
                || !(right.containsUnresolved() || left.containsUnresolved())) {
            throw new IllegalArgumentException("Expected unresolved compiler relation operands");
        }
        return calculateImpl(right, left, false, calculation);
    }

    private Relation calculateImpl(TypeConstant right, TypeConstant left, boolean cacheable,
                                   Supplier<Relation> calculation) {

        var key = new Key(right, left);
        Map<TypeConstant, Relation> results = cacheable
                ? completed.computeIfAbsent(right, _ -> new ConcurrentHasherMap<>(Hasher.identity()))
                : Map.of();
        if (cacheable) {
            Relation result = results.get(left);
            if (result != null) {
                return result;
            }
        }

        Calculation state = active.get();
        if (state == null) {
            active.set(state = new Calculation());
        }
        if (!state.inProgress.add(key)) {
            state.calls.getFirst().dependencies.add(key);
            return Relation.INCOMPATIBLE;
        }
        var query = new Query(!cacheable);
        state.calls.push(query);

        boolean finished = false;
        try {
            Relation result = requireNonNull(calculation.get(), "Incomplete relation calculation");
            query.dependencies.remove(key);
            if (query.dependencies.isEmpty() && !query.contextDependent) {
                // clear() swaps the table. A calculation started before invalidation can only
                // publish to its old table, never repopulate the replacement with a stale result.
                results.putIfAbsent(left, result);
            }
            finished = true;
            return result;
        } finally {
            state.calls.pop();
            state.inProgress.remove(key);
            Query parent = state.calls.peek();
            if (parent == null) {
                active.remove();
            } else {
                parent.dependencies.addAll(query.dependencies);
                parent.contextDependent |= query.contextDependent || !finished;
            }
        }
    }

    /**
     * Discard completed results without changing descriptors or active recursion guards.
     *
     * <p>Use this after compiler metadata invalidation, or to verify cache-clear equivalence in
     * a runtime context with fixed definitions. This is not an interner reset and must not be
     * used to make another image generation acceptable to the current descriptor context.
     */
    public void clear() {
        completed = new ConcurrentHasherMap<>(Hasher.identity());
    }

    /**
     * Invalidate the source type's results when its metadata is rebuilt. This preserves the
     * compiler's existing per-type invalidation boundary; full dependency invalidation remains
     * the responsibility of the metadata owner. Active calculations retain the detached bucket.
     *
     * @param right  the source type whose metadata was invalidated
     */
    public void clear(TypeConstant right) {
        if (right.getConstantPool() != owner) {
            throw new IllegalArgumentException("Invalidated type must belong to the selected owner");
        }
        completed.remove(right);
    }

    /** Canonical identity also avoids structural comparison of compiler register placeholders. */
    private record Key(TypeConstant right, TypeConstant left) {
        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(right) + System.identityHashCode(left);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && right == key.right && left == key.left;
        }
    }

    private static final class Calculation {
        private final Set<Key> inProgress = new HashSet<>();
        private final Deque<Query> calls = new ArrayDeque<>();
    }

    private static final class Query {
        Query(boolean contextDependent) {
            this.contextDependent = contextDependent;
        }

        private final Set<Key> dependencies = new HashSet<>();
        private boolean contextDependent;
    }

    private final ConstantPool owner;
    private final TransientThreadLocal<Calculation> active = new TransientThreadLocal<>();

    // NOTE: ConcurrentHasherMap wraps the JDK ConcurrentHashMap with identity key equality.
    // Both levels use already-canonical type objects; structural equals/hashCode is unnecessary
    // and can be invalid for compiler register placeholders. It must not select cache identity.
    private volatile Map<TypeConstant, Map<TypeConstant, Relation>> completed =
            new ConcurrentHasherMap<>(Hasher.identity());
}

package org.xvm.asm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Supplier;

import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeConstant.Usage;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.Progress;

import org.xvm.util.Hasher;
import org.xvm.util.TransientThreadLocal;

import org.xvm.util.concurrent.ConcurrentHasherMap;

import static java.util.Objects.requireNonNull;

/**
 * Disposable semantic results for one definition graph and descriptor owner. Compiler pools and
 * runtime descriptor contexts have separate instances; interning and serialized indices are not
 * affected by clearing this table. Import operands into the selected owner before querying it.
 *
 * <p>Completed results never contain a request listener or an in-progress marker. Recursion state
 * belongs to the current calculation, and failures release it. Compiler mutation remains serialized
 * by the compiler; its metadata invalidation must also invalidate these derived answers.
 */
public final class TypeMetadata {
    public TypeMetadata(ConstantPool owner) {
        this.owner = requireNonNull(owner);
    }

    /** @return whether constant validation succeeded in this owner's current metadata generation */
    public boolean isValidated(TypeConstant type) {
        checkOwner(type);
        return validated.containsKey(type);
    }

    /** Remember successful constant validation; failed or unresolved validation is never retained. */
    public void markValidated(TypeConstant type) {
        checkOwner(type);
        validated.put(type, Boolean.TRUE);
    }

    /**
     * Normalize a type in its owner. Unresolved and context-dependent answers are not retained.
     *
     * @param type         the input type, including its actual type arguments
     * @param calculation the existing normalization algebra
     * @return the normalized type
     */
    public TypeConstant normalize(TypeConstant type, Supplier<TypeConstant> calculation) {
        checkOwner(type);
        var results = normalized;
        boolean cacheable = TypeConstant.getContext() == null && !type.containsUnresolved();
        TypeConstant result = cacheable ? results.get(type) : null;
        if (result == null) {
            result = requireNonNull(calculation.get());
            checkRuntimeResult(result);
            if (cacheable && !result.containsUnresolved() && result.getConstantPool() == owner) {
                results.putIfAbsent(type, result);
            }
        }
        return result;
    }

    /**
     * Resolve a parameterized type against a constant resolver in the same owner. The full resolver,
     * including access, is a query input. Frame and other mutable resolvers bypass this API.
     *
     * @param type         the type containing formal arguments
     * @param resolver     the owner-local constant resolver
     * @param calculation the generic substitution
     * @return the resolved type
     */
    public TypeConstant resolve(TypeConstant type, TypeConstant resolver,
                                Supplier<TypeConstant> calculation) {
        checkOwner(type);
        checkOwner(resolver);
        var results = resolutions;
        boolean cacheable = TypeConstant.getContext() == null
                && !type.containsUnresolved() && !resolver.containsUnresolved();
        Resolution previous = cacheable ? results.get(type) : null;
        if (previous != null && previous.resolver == resolver) {
            return previous.result;
        }
        TypeConstant result = requireNonNull(calculation.get());
        checkRuntimeResult(result);
        if (cacheable && !result.containsUnresolved() && result.getConstantPool() == owner) {
            results.put(type, new Resolution(resolver, result));
        }
        return result;
    }

    /** Kinds of declaration-derived types; the member identity includes its declaring context. */
    public enum MemberType { Value, Constraint, Annotation }

    /**
     * Look up a declaration-derived type in this exact owner. The caller's early/incomplete compiler
     * cases bypass publication; this table never infers that a missing declaration is complete.
     */
    public TypeConstant getMemberType(Constant member, MemberType query) {
        checkOwner(member);
        var values = memberTypes.get(member);
        return values == null || TypeConstant.getContext() != null ? null : values.get(query);
    }

    /**
     * Remember a completed declaration-derived type and return it. Unresolved compiler operands and
     * upstream values are returned without retention; runtime descriptor results must be local.
     */
    public TypeConstant cacheMemberType(Constant member, MemberType query, TypeConstant result) {
        checkOwner(member);
        if (result.getConstantPool() != owner) {
            if (!owner.hasSerializedIndices()) {
                result = owner.register(result);
            } else {
                return result;
            }
        }
        if (TypeConstant.getContext() == null && !result.containsUnresolved()) {
            memberTypes.computeIfAbsent(member, _ -> new ConcurrentHashMap<>()).put(query, result);
        }
        return result;
    }

    /** Invalidate a changed member during compilation, without mutating its descriptor. */
    public void clearMember(Constant member) {
        checkOwner(member);
        memberTypes.remove(member);
    }

    /**
     * Calculate formal parameter variance. Name, access, direction and the parameterized receiver
     * are all part of the key. Recursive calls conservatively answer NO, but only the completed
     * recursive root may publish that answer; dependent inner calculations remain provisional.
     *
     * @param type         the receiver, including actual arguments
     * @param name         the formal parameter name
     * @param access       the member visibility used by the calculation
     * @param consumption true for consumption, false for production
     * @param calculation the variance algebra
     * @return true iff the type consumes or produces the named formal parameter
     */
    public boolean variance(TypeConstant type, String name, Access access, boolean consumption,
                            Supplier<Usage> calculation) {
        checkOwner(type);
        var input = new Variance(name, access, consumption);
        TypeConstant context = TypeConstant.getContext();
        var key = new VarianceKey(new IdentityKey(type), input,
                context == null ? null : new IdentityKey(context));
        boolean cacheable = TypeConstant.getContext() == null && !type.containsUnresolved();
        Map<Variance, Usage> results = cacheable
                ? variances.computeIfAbsent(type, _ -> new ConcurrentHashMap<>()) : Map.of();
        Usage result = results.get(input);
        if (result != null) {
            return result == Usage.YES;
        }

        VarianceCalculation state = activeVariance.get();
        if (state == null) {
            activeVariance.set(state = new VarianceCalculation());
        }
        VarianceResult local = state.results.get(key);
        if (local != null && state.inProgress.containsAll(local.dependencies)) {
            VarianceQuery parent = state.calls.peek();
            if (parent != null) {
                parent.dependencies.addAll(local.dependencies);
                parent.uncacheable |= local.uncacheable;
            }
            return local.usage == Usage.YES;
        }
        if (!state.inProgress.add(key)) {
            state.calls.getFirst().dependencies.add(key);
            return false;
        }
        var query = new VarianceQuery(!cacheable);
        state.calls.push(query);
        boolean finished = false;
        try {
            result = requireNonNull(calculation.get());
            if (result == Usage.IN_PROGRESS) {
                throw new IllegalStateException("Variance calculation did not finish");
            }
            query.dependencies.remove(key);
            if (query.dependencies.isEmpty() && !query.uncacheable) {
                results.putIfAbsent(input, result);
            }
            // Unresolved compiler queries and recursive dependencies can be reused within this
            // calculation while their assumptions remain active, but never across calculations.
            state.results.put(key, new VarianceResult(result, Set.copyOf(query.dependencies), query.uncacheable));
            finished = true;
            return result == Usage.YES;
        } finally {
            state.calls.pop();
            state.inProgress.remove(key);
            VarianceQuery parent = state.calls.peek();
            if (parent == null) {
                activeVariance.remove();
            } else {
                parent.dependencies.addAll(query.dependencies);
                parent.uncacheable |= query.uncacheable || !finished;
            }
        }
    }

    /**
     * Specialize the owner's configured NakedRef prototype. Bootstrap configuration is an input
     * fixed by this owner; replacing it invalidates the table. Do not retain incomplete metadata.
     *
     * @param referent     the owner-local referent type
     * @param calculation the bootstrap specialization
     * @return the specialized metadata
     */
    public TypeInfo nakedRef(TypeConstant referent, Supplier<TypeInfo> calculation) {
        checkOwner(referent);
        var results = nakedRefs;
        TypeInfo info = results.get(referent);
        if (info == null) {
            info = calculation.get();
            if (TypeConstant.isComplete(info) && !info.hasErrors()) {
                results.putIfAbsent(referent, info);
            }
        }
        return info;
    }

    /**
     * Run a TypeInfo query with calculation-local placeholders, partial results and deferred work.
     * Nested calls join this calculation. Only a successful outer call publishes complete, error-free
     * entries; a failed nested call also prevents publication if its caller catches the exception.
     * Other threads can calculate independently and never observe another calculation's partials.
     *
     * @param calculation the TypeInfo builder or cache lookup
     * @return the caller's result, including its existing error recovery result if construction fails
     */
    public TypeInfo calculateTypeInfo(Supplier<TypeInfo> calculation) {
        if (TypeConstant.getContext() != null) {
            throw new IllegalStateException("TypeInfo queries require a declaration context, not a relation probe");
        }
        InfoCalculation state = activeInfo.get();
        boolean outer = state == null;
        if (outer) {
            activeInfo.set(state = new InfoCalculation(infos, owner.getInvalidationCount()));
        }
        var target = state.target;
        boolean finished = false;
        try {
            TypeInfo result = calculation.get();
            finished = true;
            if (outer && !state.failed
                    && state.invalidations == owner.getInvalidationCount()) {
                state.pending.forEach((type, entry) -> {
                    if (entry.info != null && TypeConstant.isComplete(entry.info)
                            && !entry.info.hasErrors()) {
                        target.compute(type, (_, previous) -> previous == null
                                || entry.invalidations >= previous.invalidations ? entry : previous);
                    }
                });
            }
            return result;
        } finally {
            state.failed |= !finished;
            if (outer) {
                activeInfo.remove();
            }
        }
    }

    /** Read only this calculation's provisional result, or a published complete result. */
    public TypeInfo getTypeInfo(TypeConstant type) {
        return entry(type).info;
    }

    /**
     * Stage a result inside {@link #calculateTypeInfo}. Its progress and invalidation snapshot
     * determine whether it improves this calculation's previous result. Nothing is published here.
     */
    public void setTypeInfo(TypeConstant type, TypeInfo info) {
        int rank = info == null ? 0 : info.getProgress().ordinal();
        int invalidations = info == null ? 0 : info.getInvalidationCount();
        InfoEntry previous = entry(type);
        if (rank > previous.rank || info != null && info.getProgress() == Progress.Building) {
            activeInfo.get().pending.put(type, new InfoEntry(info, rank, invalidations));
        }
    }

    /** @return the invalidation watermark for this calculation's current TypeInfo */
    public int getInvalidationCount(TypeConstant type) {
        return entry(type).invalidations;
    }

    /** Advance the watermark after the builder has checked its declaration dependencies. */
    public void setInvalidationCount(TypeConstant type, int count) {
        InfoEntry previous = entry(type);
        if (count > previous.invalidations) {
            var replacement = new InfoEntry(previous.info, previous.rank, count);
            InfoCalculation state = activeInfo.get();
            if (state == null) {
                infos.replace(type, previous, replacement);
            } else {
                state.pending.put(type, replacement);
            }
        }
    }

    /** Remove a stale TypeInfo; dependency invalidation still belongs to the compiler's protocol. */
    public void clearTypeInfo(TypeConstant type) {
        checkOwner(type);
        infos.remove(type);
        InfoCalculation state = activeInfo.get();
        if (state != null) {
            state.pending.put(type, InfoEntry.EMPTY);
        }
    }

    /** Discard only the current calculation's placeholder; another thread's result stays published. */
    public void clearPlaceholder(TypeConstant type) {
        checkOwner(type);
        InfoCalculation state = activeInfo.get();
        InfoEntry entry = state.pending.get(type);
        if (entry != null && entry.info != null && entry.info.getProgress() == Progress.Building) {
            state.pending.remove(type);
        }
    }

    /** Discard Object bootstrap's dependent partial results only in this calculation. */
    public void finishObjectBootstrap() {
        InfoCalculation state = activeInfo.get();
        state.pending.keySet().removeIf(type -> !type.isRootObject());
        state.deferred.clear();
    }

    /** Queue a recursive dependency for this calculation's existing completion algorithm. */
    public void defer(TypeConstant type) {
        checkOwner(type);
        List<TypeConstant> deferred = activeInfo.get().deferred;
        if (!deferred.contains(type)) {
            deferred.add(type);
        }
    }

    /** @return whether this calculation has dependencies still needing a completion pass */
    public boolean hasDeferred() {
        InfoCalculation state = activeInfo.get();
        return state != null && !state.deferred.isEmpty();
    }

    /** Take, and remove, the current batch without losing the rest of the calculation state. */
    public List<TypeConstant> takeDeferred() {
        InfoCalculation state = activeInfo.get();
        if (state == null || state.deferred.isEmpty()) {
            return List.of();
        }
        var result = List.copyOf(state.deferred);
        state.deferred.clear();
        return result;
    }

    /** @return this calculation's deferred-rebuild depth for the given type */
    public int recursionDepth(TypeConstant type) {
        return activeInfo.get().depths.getOrDefault(type, 0);
    }

    /** Enter or leave one deferred-rebuild frame; always leave it in a finally block. */
    public void changeRecursionDepth(TypeConstant type, int delta) {
        activeInfo.get().depths.merge(type, delta, Integer::sum);
    }

    private InfoEntry entry(TypeConstant type) {
        checkOwner(type);
        InfoCalculation state = activeInfo.get();
        if (state != null && state.pending.containsKey(type)) {
            return state.pending.get(type);
        }
        return infos.getOrDefault(type, InfoEntry.EMPTY);
    }

    /**
     * Discard derived results after declaration changes or for cache-clear equivalence checks.
     * Swapping the maps prevents a calculation already in flight from repopulating the new table.
     * Active recursion guards and descriptor identity remain intact.
     */
    public void clear() {
        infos = new ConcurrentHasherMap<>(Hasher.identity());
        clearDerived();
    }

    /** Invalidate derived answers while retaining TypeInfo dependency watermarks. */
    public void clearDerived() {
        validated = new ConcurrentHasherMap<>(Hasher.identity());
        normalized = new ConcurrentHasherMap<>(Hasher.identity());
        memberTypes = new ConcurrentHasherMap<>(Hasher.identity());
        resolutions = new ConcurrentHasherMap<>(Hasher.identity());
        nakedRefs = new ConcurrentHasherMap<>(Hasher.identity());
        variances = new ConcurrentHasherMap<>(Hasher.identity());
    }

    /** Discard results whose operand graph is about to be registered again by the compiler. */
    public void clear(TypeConstant type) {
        checkOwner(type);
        validated.remove(type);
        normalized.remove(type);
        clearMember(type);
        resolutions.remove(type);
        variances.remove(type);
    }

    private void checkOwner(Constant type) {
        if (type.getConstantPool() != owner) {
            throw new IllegalArgumentException("Metadata operand must belong to the selected owner");
        }
    }

    private void checkRuntimeResult(TypeConstant result) {
        // Compiler constraints can resolve to upstream operands before adoption is possible;
        // return them without retention. Runtime derivation must already select this owner.
        if (!owner.hasSerializedIndices()) {
            checkOwner(result);
        }
    }

    private record Resolution(TypeConstant resolver, TypeConstant result) {}

    private record Variance(String name, Access access, boolean consumption) {}

    private record VarianceKey(IdentityKey type, Variance input, IdentityKey context) {}

    private record VarianceResult(Usage usage, Set<VarianceKey> dependencies, boolean uncacheable) {}

    // NOTE: Canonical identity, not structural equality, selects memoized operands. In particular,
    // compiler placeholders may not yet support equals/hashCode. Foreign owners are rejected first.
    private record IdentityKey(TypeConstant type) {
        @Override
        public int hashCode() {
            return System.identityHashCode(type);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IdentityKey key && type == key.type;
        }
    }

    private static final class VarianceCalculation {
        private final Map<VarianceKey, VarianceResult> results = new HashMap<>();
        private final Set<VarianceKey> inProgress = new HashSet<>();
        private final Deque<VarianceQuery> calls = new ArrayDeque<>();
    }

    private static final class VarianceQuery {
        VarianceQuery(boolean uncacheable) {
            this.uncacheable = uncacheable;
        }

        private final Set<VarianceKey> dependencies = new HashSet<>();
        private boolean uncacheable;
    }

    private record InfoEntry(TypeInfo info, int rank, int invalidations) {
        private static final InfoEntry EMPTY = new InfoEntry(null, 0, 0);
    }

    private static final class InfoCalculation {
        InfoCalculation(Map<TypeConstant, InfoEntry> target, int invalidations) {
            this.target = target;
            this.invalidations = invalidations;
        }

        private final Map<TypeConstant, InfoEntry> target;
        private final int invalidations;
        private final Map<TypeConstant, InfoEntry> pending = new IdentityHashMap<>();
        private final Map<TypeConstant, Integer> depths = new IdentityHashMap<>();
        private final List<TypeConstant> deferred = new ArrayList<>();
        private boolean failed;
    }

    private final ConstantPool owner;
    private final TransientThreadLocal<InfoCalculation> activeInfo = new TransientThreadLocal<>();
    private volatile Map<TypeConstant, InfoEntry> infos = new ConcurrentHasherMap<>(Hasher.identity());
    private final TransientThreadLocal<VarianceCalculation> activeVariance = new TransientThreadLocal<>();

    // NOTE: ConcurrentHasherMap uses the JDK ConcurrentHashMap with identity equality for the
    // already-canonical receiver keys. Query records supply the remaining semantic inputs.
    private volatile Map<TypeConstant, Boolean> validated = new ConcurrentHasherMap<>(Hasher.identity());
    private volatile Map<TypeConstant, TypeConstant> normalized = new ConcurrentHasherMap<>(Hasher.identity());
    private volatile Map<Constant, Map<MemberType, TypeConstant>> memberTypes =
            new ConcurrentHasherMap<>(Hasher.identity());
    private volatile Map<TypeConstant, TypeInfo> nakedRefs = new ConcurrentHasherMap<>(Hasher.identity());
    // Retain only the most recent constant resolver per type, matching the old bounded cache.
    private volatile Map<TypeConstant, Resolution> resolutions =
            new ConcurrentHasherMap<>(Hasher.identity());
    private volatile Map<TypeConstant, Map<Variance, Usage>> variances =
            new ConcurrentHasherMap<>(Hasher.identity());
}

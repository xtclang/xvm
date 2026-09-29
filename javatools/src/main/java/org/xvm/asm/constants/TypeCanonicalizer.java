package org.xvm.asm.constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;

/**
 * Calculate a layer-one canonical type from the reachable composition conditions. Formal types
 * are retained while walking contributions, so constraints can be traced back to the original
 * class even when a contribution renames or rearranges its parameters.
 *
 * TODO CP simplify this and clean it up
 */
class TypeCanonicalizer {
    TypeCanonicalizer(TypeConstant type) {
        pool = type.getConstantPool();
        clz  = (ClassStructure) type.getSingleUnderlyingClass(true).getComponent();

        GenericTypeResolver actual = resolver(type.normalizeParameters());
        GenericTypeResolver limits = resolver(clz.getNormalizedType());
        for (TypeConstant parameter : clz.getFormalType().getParamTypes()) {
            FormalConstant formal = (FormalConstant) parameter.getDefiningConstant();
            mapActual.put(formal, actual.resolveFormalType(formal));
            mapCanonical.put(formal, limits.resolveFormalType(formal));
        }
    }

    /**
     * Compare the parameter types themselves; assignability of the class can ignore unused or
     * other parameters under its variance rules, so it does not establish canonicality.
     */
    static boolean isCanonical(TypeConstant type) {
        if (!type.isParamsSpecified()) {
            return !type.isVirtualChild() || type.getParentType().isCanonicalType();
        }
        TypeConstant canonical = type.getCanonicalType();
        if (!canonical.isParamsSpecified() || type.getParamsCount() != canonical.getParamsCount()) {
            return false;
        }
        for (int i = 0; i < type.getParamsCount(); ++i) {
            TypeConstant actual   = type.getParamType(i);
            TypeConstant expected = canonical.getParamType(i);
            if (!actual.isA(expected) || !expected.isA(actual)) {
                return false;
            }
        }
        return !type.isVirtualChild() || type.getParentType().isCanonicalType();
    }

    /**
     * Preserve the original type's modifiers, but retain parameters only when the composition
     * has reachable conditional incorporations.
     */
    TypeConstant canonicalize(TypeConstant type) {
        TypeConstant typeFormal = withoutParameters(type).adoptParameters(pool,
                clz.getFormalType().getParamTypesArray());
        collect(typeFormal, Map.of());

        if (!fConditional) {
            return withoutParameters(type);
        }

        // narrowing T also narrows U when U is declared as "U extends T"
        GenericTypeResolver resolver = resolver(typeFormal);
        boolean changed;
        do {
            changed = false;
            for (FormalConstant formal : mapCanonical.keySet()) {
                TypeConstant previous = mapCanonical.get(formal);
                TypeConstant limit = formal.getConstraintType().resolveGenerics(pool, resolver)
                        .resolveGenerics(pool, mapCanonical::get);
                TypeConstant narrowed = previous.combine(pool, limit);
                if (!narrowed.equals(previous)) {
                    mapCanonical.put(formal, narrowed);
                    changed = true;
                }
            }
        } while (changed);

        return typeFormal.resolveGenerics(pool, mapCanonical::get);
    }

    private TypeConstant withoutParameters(TypeConstant type) {
        if (type instanceof ParameterizedTypeConstant) {
            return withoutParameters(type.getUnderlyingType());
        }
        if (type instanceof AbstractDependantTypeConstant) {
            return type.getCanonicalType();
        }
        return type.isModifyingType()
                ? type.replaceUnderlying(pool, this::withoutParameters)
                : type;
    }

    /**
     * Walk composition edges, not property types or static nested classes. An "into" clause is
     * a requirement on the target, not another incorporated implementation.
     */
    private void collect(TypeConstant type, Map<FormalConstant, Candidate> candidates) {
        if (!setVisited.add(new Visit(type, candidates))) {
            return;
        }

        if (type instanceof AnnotatedTypeConstant annotated) {
            collect(annotated.getAnnotationType(), candidates);
            collect(annotated.getUnderlyingType(), candidates);
            return;
        }
        if (type.isModifyingType() && !(type instanceof ParameterizedTypeConstant)) {
            collect(type.getUnderlyingType(), candidates);
            return;
        }

        if (!type.isSingleUnderlyingClass(true)) {
            return;
        }

        ClassStructure structure = (ClassStructure)
                type.getSingleUnderlyingClass(true).getComponent();
        GenericTypeResolver resolver = resolver(type);
        for (Contribution contribution : structure.getContributionsAsList()) {
            switch (contribution.getComposition()) {
            case Annotation:
                if (!structure.isIntoClassAnnotation(contribution.getTypeConstant())) {
                    collect(pool.ensureAnnotatedTypeConstant(type, contribution.getAnnotation())
                            .getAnnotationType(), candidates);
                }
                break;

            case Incorporates:
                if (contribution.isConditional()) {
                    fConditional = true;
                    TypeConstant typeMixin = contribution.getTypeConstant().normalizeParameters()
                            .resolveGenerics(pool, resolver);
                    Map<FormalConstant, Candidate> narrowed = applies(
                            typeMixin, contribution, resolver, candidates);
                    if (narrowed != null) {
                        collect(typeMixin, narrowed);
                    }
                    break;
                }
                // fall through
            case Extends:
            case Implements:
            case Delegates:
                collect(contribution.resolveGenerics(pool, resolver), candidates);
                break;

            default:
                break;
            }
        }

        collectChildren(structure, type, candidates);
    }

    private void collectChildren(Component parent, TypeConstant typeParent,
            Map<FormalConstant, Candidate> candidates) {
        GenericTypeResolver resolver = resolver(typeParent);
        for (Component child : parent.children()) {
            if (child instanceof ClassStructure clzChild && clzChild.isVirtualChild()) {
                // keep child parameters open; their bounds determine which incorporations are
                // possible without fixing a choice for the child
                TypeConstant typeChild = clzChild.getFormalType().resolveGenerics(pool, formal ->
                        formal.getParentConstant().equals(clzChild.getIdentityConstant())
                                ? null
                                : resolver.resolveFormalType(formal));
                Map<FormalConstant, Candidate> childCandidates = new HashMap<>(candidates);
                int i = 0;
                for (TypeConstant bound : clzChild.getTypeParams().values()) {
                    FormalConstant formal = (FormalConstant) clzChild.getFormalType()
                            .getParamType(i++).getDefiningConstant();
                    TypeConstant origin = bound.resolveGenerics(pool, resolver);
                    childCandidates.put(formal,
                            new Candidate(origin, actualType(origin, childCandidates)));
                }
                collect(typeChild, Map.copyOf(childCandidates));
            } else if (child.getFormat() == Component.Format.PROPERTY) {
                collectChildren(child, typeParent, candidates);
            }
        }
    }

    /**
     * Check the entire conjunction before recording any of its constraints. A child parameter
     * can satisfy a condition if its current bound can intersect the required type.
     */
    private Map<FormalConstant, Candidate> applies(TypeConstant typeMixin,
            Contribution contribution, GenericTypeResolver resolver,
            Map<FormalConstant, Candidate> candidates) {
        Map<FormalConstant, Candidate> narrowed = new HashMap<>(candidates);
        List<Requirement> requirements = new ArrayList<>();
        int i = 0;
        for (TypeConstant constraint : contribution.getTypeParams().values()) {
            if (constraint != null) {
                TypeConstant parameter = typeMixin.getParamType(i);
                TypeConstant limit     = constraint.resolveGenerics(pool, resolver);
                TypeConstant actualLimit = actualType(limit, narrowed);
                Candidate candidate = parameter.isFormalType()
                        ? narrowed.get((FormalConstant) parameter.getDefiningConstant())
                        : null;
                if (candidate == null) {
                    if (!actualType(parameter, narrowed).isA(actualLimit)) {
                        return null;
                    }
                    requirements.add(new Requirement(parameter, limit));
                } else {
                    TypeConstant bound = candidate.bound();
                    if (bound.isIncompatibleCombo(actualLimit)) {
                        return null;
                    }
                    if (bound.isA(actualLimit)) {
                        requirements.add(new Requirement(candidate.origin(), limit));
                    }
                    narrowed.put((FormalConstant) parameter.getDefiningConstant(),
                            new Candidate(candidate.origin(), bound.combine(pool, actualLimit)));
                }
            }
            ++i;
        }
        for (Requirement requirement : requirements) {
            project(requirement.origin(), requirement.limit(), candidates);
        }
        return Map.copyOf(narrowed);
    }

    private TypeConstant actualType(TypeConstant type, Map<FormalConstant, Candidate> candidates) {
        return type.resolveGenerics(pool, formal -> {
            TypeConstant actual = mapActual.get(formal);
            Candidate candidate = candidates.get(formal);
            return actual == null ? candidate == null ? null : candidate.bound() : actual;
        });
    }

    private void project(TypeConstant origin, TypeConstant limit,
            Map<FormalConstant, Candidate> candidates) {
        if (origin.isFormalType()) {
            Candidate candidate = candidates.get((FormalConstant) origin.getDefiningConstant());
            if (candidate != null) {
                if (candidate.bound().isA(actualType(limit, candidates))
                        && !candidate.origin().equals(origin)) {
                    project(candidate.origin(), limit, candidates);
                }
                return;
            }
        }
        constrain(origin, limit);
    }

    /**
     * Keep the identities of enclosing formals distinct from equally named child parameters.
     */
    private GenericTypeResolver resolver(TypeConstant type) {
        Map<FormalConstant, TypeConstant> parameters = new HashMap<>();
        TypeConstant current = type;
        while (true) {
            ClassStructure structure = (ClassStructure)
                    current.getSingleUnderlyingClass(true).getComponent();
            int i = 0;
            for (TypeConstant parameter : structure.getFormalType().getParamTypes()) {
                FormalConstant formal = (FormalConstant) parameter.getDefiningConstant();
                parameters.put(formal, current.isTuple()
                        ? current.resolveFormalType(formal)
                        : current.getParamType(i));
                ++i;
            }
            if (!current.isVirtualChild()) {
                break;
            }
            current = current.getParentType();
        }
        return formal -> {
            TypeConstant resolved = parameters.get(formal);
            return resolved == null ? type.resolveFormalType(formal) : resolved;
        };
    }

    /**
     * Project an applied constraint through a contribution's parameter expression to this
     * class's formals. Compare formal identities, not names, to avoid capturing a child's own
     * parameter with the same name.
     */
    private void constrain(TypeConstant type, TypeConstant constraint) {
        if (type.isFormalType()) {
            FormalConstant formal = (FormalConstant) type.getDefiningConstant();
            TypeConstant previous = mapCanonical.get(formal);
            if (previous != null) {
                mapCanonical.put(formal, previous.combine(pool, constraint));
            }
        } else if (type.isParamsSpecified() && type.isSingleUnderlyingClass(true)) {
            ClassStructure structure = (ClassStructure)
                    type.getSingleUnderlyingClass(true).getComponent();
            int i = 0;
            for (StringConstant name : structure.getTypeParams().keySet()) {
                TypeConstant parameter = constraint.resolveGenericType(name.getValue());
                if (parameter != null) {
                    constrain(type.getParamType(i), parameter);
                }
                ++i;
            }
        }
    }

    private final ConstantPool pool;
    private final ClassStructure clz;
    private final Map<FormalConstant, TypeConstant> mapActual    = new HashMap<>();
    private final Map<FormalConstant, TypeConstant> mapCanonical = new HashMap<>();
    private final Set<Visit> setVisited = new HashSet<>();
    private boolean fConditional;

    private record Candidate(TypeConstant origin, TypeConstant bound) {}
    private record Requirement(TypeConstant origin, TypeConstant limit) {}
    private record Visit(TypeConstant type, Map<FormalConstant, Candidate> candidates) {}
}

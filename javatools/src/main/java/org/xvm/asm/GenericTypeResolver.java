package org.xvm.asm;


import java.util.Map;

import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * Resolver of a generic type name into an actual type.
 */
@FunctionalInterface
public interface GenericTypeResolver {
    /**
     * Resolve the generic type based on the formal parameter name.
     *
     * @param sFormalName  the formal parameter name
     *
     * @return a resolved type, or null if it could not be resolved
     */
    TypeConstant resolveGenericType(String sFormalName);

    /**
     * Resolve the generic type based on the FormalConstant representing a formal parameter.
     *
     * @param constFormal  the formal constant
     *
     * @return a resolved type, or null if it could not be resolved
     */
    default TypeConstant resolveFormalType(FormalConstant constFormal) {
        return constFormal instanceof PropertyConstant
            ? resolveGenericType(constFormal.getName())
            : null;
    }

    /**
     * Create a GenericTypeResolver based on the specified map.
     */
    static GenericTypeResolver of(Map<FormalConstant, TypeConstant> mapResolve) {
        return new TypeParameterResolver(mapResolve);
    }
    /**
     * Create a GenericTypeResolver based on two resolvers.
     */
    static GenericTypeResolver chain(GenericTypeResolver resolver1, GenericTypeResolver resolver2) {
        return new ChainResolver(resolver1, resolver2);
    }

    /**
     * Trivial GenericTypeResolver implementation based on a Map<FormalConstant, TypeConstant>.
     */
    class TypeParameterResolver
            implements GenericTypeResolver {
        public TypeParameterResolver(Map<FormalConstant, TypeConstant> mapResolve) {
            this.mapResolve = mapResolve;
        }

        public TypeConstant resolveGenericType(String sFormalName) {
            return null;
        }

        public TypeConstant resolveFormalType(FormalConstant constFormal) {
            return mapResolve.get(constFormal);
        }

        private final Map<FormalConstant, TypeConstant> mapResolve;
    }

    /**
     * Chain GenericTypeResolver implementation.
     */
    class ChainResolver
            implements GenericTypeResolver {
        public ChainResolver(GenericTypeResolver resolver1, GenericTypeResolver resolver2) {
            this.resolver1 = resolver1;
            this.resolver2 = resolver2;
        }

        @Override
        public TypeConstant resolveGenericType(String sFormalName) {
            TypeConstant type = resolver1.resolveGenericType(sFormalName);
            return type == null
                    ? resolver2.resolveGenericType(sFormalName)
                    : type;
        }

        @Override
        public TypeConstant resolveFormalType(FormalConstant constFormal) {
            TypeConstant type = resolver1.resolveFormalType(constFormal);
            return type == null
                    ? resolver2.resolveFormalType(constFormal)
                    : type;
        }

        private final GenericTypeResolver resolver1;
        private final GenericTypeResolver resolver2;
    }
}
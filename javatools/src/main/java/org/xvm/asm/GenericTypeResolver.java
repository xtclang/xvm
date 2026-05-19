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
     * Resolve the generic type based on the FormalConstant representing a formal parameter.
     *
     * @param constFormal  the formal constant
     *
     * @return a resolved type, or null if it could not be resolved
     */
    TypeConstant resolveFormalType(FormalConstant constFormal);

    /**
     * Create a GenericTypeResolver for generic properties based on the specified map.
     */
    static GenericTypeResolver of(Map<String, TypeConstant> mapResolve) {
        return new TypeParameterResolver(mapResolve);
    }
    /**
     * Create a GenericTypeResolver based on two resolvers.
     */
    static GenericTypeResolver chain(GenericTypeResolver resolver1, GenericTypeResolver resolver2) {
        return new ChainResolver(resolver1, resolver2);
    }

    /**
     * Trivial GenericTypeResolver implementation for generic properties based on a
     * Map<String, TypeConstant>.
     */
    class TypeParameterResolver
            implements GenericTypeResolver {
        public TypeParameterResolver(Map<String, TypeConstant> mapResolve) {
            this.mapResolve = mapResolve;
        }

        public TypeConstant resolveFormalType(FormalConstant constFormal) {
            return constFormal.getFormat() == Constant.Format.Property
                    ? mapResolve.get(constFormal.getName())
                    : null;
        }

        private final Map<String, TypeConstant> mapResolve;
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
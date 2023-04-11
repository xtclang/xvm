package org.xvm.asm;


import java.util.Map;

import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * Resolver of a generic type name into an actual type.
 */
public interface GenericTypeResolver
    {
    /**
     * Resolve the generic type based on the formal parameter name.
     *
     * @param sFormalName  the formal parameter name
     *
     * @return a resolved type
     */
    TypeConstant resolveGenericType(String sFormalName);

    /**
     * Resolve the generic type based on the FormalConstant representing a formal parameter.
     *
     * @param constFormal  the formal constant
     *
     * @return a resolved type
     */
    default TypeConstant resolveFormalType(FormalConstant constFormal)
        {
        return resolveGenericType(constFormal.getName());
        }

    /**
     * Create a GenericTypeResolver based on the specified map.
     */
    static GenericTypeResolver of(Map<FormalConstant, TypeConstant> mapResolve)
        {
        return new TypeParameterResolver(mapResolve);
        }

    /**
     * Trivial GenericTypeResolver implementation based on a Map<FormalConstant, TypeConstant>.
     */
    class TypeParameterResolver
            implements GenericTypeResolver
        {
        public TypeParameterResolver(Map<FormalConstant, TypeConstant> mapResolve)
            {
            this.mapResolve = mapResolve;
            }

        public TypeConstant resolveGenericType(String sFormalName)
            {
            return null;
            }

        public TypeConstant resolveFormalType(FormalConstant constFormal)
            {
            return mapResolve.get(constFormal);
            }

        Map<FormalConstant, TypeConstant> mapResolve;
        }
    }
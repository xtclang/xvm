package org.xvm.asm;


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
    }

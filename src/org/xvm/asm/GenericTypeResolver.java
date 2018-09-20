package org.xvm.asm;


import org.xvm.asm.constants.TypeConstant;


/**
 * Resolver of a generic type name into an actual type.
 */
public interface GenericTypeResolver
    {
    /**
     * Resolve the generic type based on the PropertyConstant representing a formal parameter.
     *
     * @param sFormalName  the formal parameter name
     *
     * @return a resolved type
     */
    TypeConstant resolveGenericType(String sFormalName);
    }

package org.xvm.asm.constants;

import org.xvm.asm.GenericTypeResolver;

/**
 * FormalConstant is a Constant that represents a formal type, which could be generic class level
 * type parameter, such as Map.KeyType, a method type parameter (such as Object.equals.CompileType)
 * or a formal type child constant, such as Map.equals.CompileType.KeyType)
 */
public interface FormalConstant
    {
    /**
     * @return the constraint type for this formal constant
     */
    TypeConstant getConstraintType();

    /**
     * Resolve the formal type represented by this constant using the specified resolver.
     *
     * @param resolver  the resolver to use
     *
     * @return a resolved type or null if this constant cannot be resolved
     */
    TypeConstant resolve(GenericTypeResolver resolver);
    }

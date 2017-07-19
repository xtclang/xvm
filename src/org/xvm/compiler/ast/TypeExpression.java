package org.xvm.compiler.ast;


import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedClassConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;


/**
 * A type expression specifies a type.
 *
 * @author cp 2017.03.28
 */
public abstract class TypeExpression
        extends Expression
    {
    @Override
    public TypeExpression toTypeExpression()
        {
        return this;
        }

    /**
     * Create an assembly place-holder that represents this type as an XVM TypeConstant, if this
     * type is not yet resolvable.
     *
     * @param pool  the ConstantPool
     *
     * @return an UnresolvedTypeConstant
     */
    public TypeConstant asUnresolvedTypeConstant(ConstantPool pool)
        {
        return new UnresolvedTypeConstant(pool, this);
        }

    /**
     * Create an assembly place-holder that represents this type as an XVM ClassConstant, if the
     * underlying class identity is not yet resolvable.
     *
     * @param pool  the ConstantPool
     *
     * @return an UnresolvedClassConstant
     */
    public ClassConstant asUnresolvedClassConstant(ConstantPool pool)
        {
        return new UnresolvedClassConstant(pool, this);
        }

    /**
     * Create an assembly place-holder that represents this type as an XVM ClassTypeConstant, if
     * this type is not yet resolvable because the underlying class identity is not yet resolvable.
     *
     * @param pool  the ConstantPool
     *
     * @return a ClassTypeConstant wrapped around an UnresolvedClassConstant
     */
    public ClassTypeConstant asUnresolvedClassTypeConstant(ConstantPool pool)
        {
        return new ClassTypeConstant(pool, asUnresolvedClassConstant(pool), Constants.Access.PUBLIC);
        }
    }

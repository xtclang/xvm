package org.xvm.compiler.ast;


import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ResolvableConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedClassConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;

import org.xvm.compiler.ErrorListener;


/**
 * A type expression is used to specify an abstract data type. In its compiled form, there are many
 * different possible representations of an abstract data type, depending on how it is declared, and
 * depending on how it is used. A TypeExpression may be used to indicate eitherh an IdentityConstant
 * (such as a ModuleConstant, PackageConstant, ClassConstant, etc.) or a TypeConstant (such as a
 * ClassTypeConstant, a ParameterTypeConstant, etc.) Often, a type expression must provide a
 * compiled representation of itself before it is able to resolve what its actual ADT will be; in
 * these cases, the type expression can create a temporary place-holder, known as an unresolved
 * constant, which will later be replaced with the real ADT information once the type information
 * has been fully resolved.
 *
 * @author cp 2017.03.28
 */
public abstract class TypeExpression
        extends Expression
    {
    // ----- type specific functionality -----------------------------------------------------------

    @Override
    public TypeExpression toTypeExpression()
        {
        return this;
        }

    /**
     * Obtain the IdentityConstant currently associated with this TypeExpression, creating an
     * UnresolvedClassConstant as a temporary place-holder if necessary. As a result of calling this
     * method, the TypeExpression becomes responsible for resolving the component that is referred
     * to by the TypeExpression; in other words, the TypeExpression becomes responsible for
     * eventually producing a resolved IdentityConstant.
     *
     * @return an IdentityConstant
     */
    public IdentityConstant ensureIdentityConstant()
        {
        IdentityConstant constClass = getIdentityConstant();
        if (constClass == null)
            {
            constClass = new UnresolvedClassConstant(getConstantPool(), this);
            setIdentityConstant(constClass);
            }
        return constClass;
        }

    /**
     * Obtain the TypeConstant currently associated with this TypeExpression, creating an unresolved
     * TypeConstant if necessary.
     *
     * @return a TypeConstant
     */
    public TypeConstant ensureTypeConstant()
        {
        TypeConstant constType = getTypeConstant();
        if (constType == null)
            {
            constType = new UnresolvedTypeConstant(getConstantPool(), this);
            setTypeConstant(constType);
            }
        return constType;
        }

    /**
     * Create a representation of this type as an XVM ClassTypeConstant. This may produce an error.
     * This should be overridden by any TypeExpression that has better knowledge about how to form
     * a ClassTypeConstant, or that needs to log an error.
     *
     * @param errs  for logging any errors that occur attempting to represent the type as a class
     *              type
     *
     * @return a ClassTypeConstant
     */
    public ClassTypeConstant asClassTypeConstant(ErrorListener errs)
        {
        TypeConstant constType = getTypeConstant();
        if (constType instanceof ClassTypeConstant)
            {
            return (ClassTypeConstant) constType;
            }

        return getConstantPool().ensureClassTypeConstant(ensureIdentityConstant(), Access.PUBLIC);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the IdentityConstant currently associated with this TypeExpression, or null if no
     *         one has required an IdentityConstant
     */
    public IdentityConstant getIdentityConstant()
        {
        IdentityConstant constId = m_constId;
        if (constId instanceof UnresolvedClassConstant)
            {
            IdentityConstant constResolved = ((UnresolvedClassConstant) constId).getResolvedConstant();
            if (constResolved != null)
                {
                constId = constResolved;
                setIdentityConstant(constId);
                }
            }
        return constId;
        }

    /**
     * @param constId  the IdentityConstant to associate with this TypeExpression
     */
    protected void setIdentityConstant(IdentityConstant constId)
        {
        IdentityConstant constPrev = m_constId;
        if (constId != constPrev)
            {
            // if the previous identity constant was a resolvable constant, then resolve it to
            // point to the new identity constant
            if (constPrev instanceof ResolvableConstant)
                {
                ((ResolvableConstant) constPrev).resolve(constId);
                }

            // store the new identity constant
            m_constId = constId;
            }
        }

    /**
     * @return the TypeConstant currently associated with this TypeExpression, or null
     */
    public TypeConstant getTypeConstant()
        {
        TypeConstant constType = m_constType;
        if (constType instanceof UnresolvedTypeConstant)
            {
            TypeConstant constResolved = ((UnresolvedTypeConstant) constType).getResolvedConstant();
            if (constResolved != null)
                {
                constType = constResolved;
                setTypeConstant(constType);
                }
            }
        return constType;
        }

    /**
     * @param constType  the TypeConstant to associate with this TypeExpression
     */
    protected void setTypeConstant(TypeConstant constType)
        {
        TypeConstant constPrev = m_constType;
        if (constType != constPrev)
            {
            // if the previous type constant was a resolvable type constant, then resolve it to
            // point to the new type constant
            if (constPrev instanceof ResolvableConstant)
                {
                ((ResolvableConstant) constPrev).resolve(constType);
                }

            // store the new type constant
            m_constType = constType;
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The IdentityConstant identifying the class of this TypeExpression.
     */
    private IdentityConstant m_constId;

    /**
     * The TypeConstant currently associated with this TypeExpression.
     */
    private TypeConstant m_constType;
    }

package org.xvm.compiler.ast;


import org.xvm.asm.Constant;

import org.xvm.asm.constants.ResolvableConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * A type expression is used to specify an abstract data type. In its compiled form, there are many
 * different possible representations of an abstract data type, depending on how it is declared, and
 * depending on how it is used. A TypeExpression may be used to indicate eitherh an Constant
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
     * Obtain the Constant currently associated with this TypeExpression, creating an
     * UnresolvedClassConstant as a temporary place-holder if necessary. As a result of calling this
     * method, the TypeExpression becomes responsible for resolving the component that is referred
     * to by the TypeExpression; in other words, the TypeExpression becomes responsible for
     * eventually producing a resolved Constant.
     *
     * @return an Constant
     */
    public Constant ensureIdentityConstant()
        {
        Constant constClass = getIdentityConstant();
        if (constClass == null)
            {
            constClass = instantiateIdentityConstant();
            setIdentityConstant(constClass);
            }
        return constClass;
        }

    /**
     * TODO new UnresolvedNameConstant(getConstantPool(), "name_goes_here");
     *
     * @return a constant that identifies the structure; either an IdentityConstant or a
     *         PseudoConstant
     */
    protected abstract Constant instantiateIdentityConstant();


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
            constType = instantiateTypeConstant();
            setTypeConstant(constType);
            }
        return constType;
        }

    /**
     * This needs to be overridden by any TypeExpression that evaluates to something other than a
     * class type.
     *
     * @return
     */
    protected TypeConstant instantiateTypeConstant()
        {
        return getConstantPool().ensureClassTypeConstant(ensureIdentityConstant(), null);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the Constant that identifies the structure (pseudo or real) that this expression
     *         refers to
     */
    public Constant getIdentityConstant()
        {
        Constant constId = m_constId;
        if (constId instanceof ResolvableConstant)
            {
            constId = ((ResolvableConstant) constId).unwrap();
            setIdentityConstant(constId);
            }
        return constId;
        }

    /**
     * @param constId  the Constant to associate with this TypeExpression
     */
    protected void setIdentityConstant(Constant constId)
        {
        Constant constPrev = m_constId;
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
        return m_constType;
        }

    /**
     * @param constType  the TypeConstant to associate with this TypeExpression
     */
    protected void setTypeConstant(TypeConstant constType)
        {
        // store the new type constant
        m_constType = constType;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The Constant identifying the class of this TypeExpression.
     */
    private Constant m_constId;

    /**
     * The TypeConstant currently associated with this TypeExpression.
     */
    private TypeConstant m_constType;
    }

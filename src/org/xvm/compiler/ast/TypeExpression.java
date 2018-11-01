package org.xvm.compiler.ast;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;

import org.xvm.util.Severity;


/**
 * A type expression is used to specify an abstract data type. In its compiled form, there are many
 * different possible representations of an abstract data type, depending on how it is declared, and
 * depending on how it is used. A TypeExpression may be used to indicate either a Constant
 * (such as a ModuleConstant, PackageConstant, ClassConstant, etc.) or a TypeConstant (such as a
 * ClassTypeConstant, a ParameterTypeConstant, etc.) Often, a type expression must provide a
 * compiled representation of itself before it is able to resolve what its actual ADT will be; in
 * these cases, the type expression can create a temporary place-holder, known as an unresolved
 * constant, which will later be replaced with the real ADT information once the type information
 * has been fully resolved.
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
            if (isValidated())
                {
                // once the expression has validated, we know the type
                TypeConstant type = getType();
                assert type.getParamsCount() == 1; // Type<DataType>
                constType = type.getParamTypesArray()[0];
                }
            else
                {
                constType = instantiateTypeConstant();
                }

            setTypeConstant(constType);
            }
        return constType;
        }

    /**
     * @return a TypeConstant for this TypeExpression
     */
    protected abstract TypeConstant instantiateTypeConstant();

    /**
     * @return the TypeConstant currently associated with this TypeExpression, or null
     */
    protected TypeConstant getTypeConstant()
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

    /**
     * Clear out this expression's type, if it is cached.
     */
    protected void resetTypeConstant()
        {
        m_constType = null;
        AstNode parent = getParent();
        if (parent instanceof TypeExpression)
            {
            ((TypeExpression) parent).resetTypeConstant();
            }
        }

    /**
     * Determine if this is an introductory type expression.
     *
     * @return true iff this is an introductory type expression
     */
    public boolean isIntroductoryType()
        {
        return false;
        }

    /**
     * For introductory type expressions, obtain the underlying type expression. An introductory
     * type expression is one that may be (or may contain) a separable TypeExpression that belongs
     * to something other than the resulting type, such as an annotation that affects a variable
     * implementation itself, instead of the type of the variable.
     *
     * @return the underlying TypeExpression, if any
     */
    public TypeExpression unwrapIntroductoryType()
        {
        assert !isIntroductoryType();
        return null;
        }

    /**
     * Determine if this is an introductory type expression.
     *
     * @return true iff this is an introductory type expression
     */
    public void replaceIntroducedType(TypeExpression type)
        {
        assert !isIntroductoryType();
        throw new IllegalStateException();
        }


    // ----- inner class compilation support -------------------------------------------------------

    /**
     * @return the AnonInnerClass that represents the outline of what an anonymous inner class
     *         for this type would look like
     */
    public final AnonInnerClass inferAnonInnerClass(ErrorListener errs)
        {
        AnonInnerClass info = new AnonInnerClass(this, errs);
        collectAnonInnerClassInfo(info);
        return info;
        }

    /**
     * Collect information into the provided AnonInnerClass.
     *
     * @param info  a mutable collector of information about the shape of an anonymous inner class
     */
    protected void collectAnonInnerClassInfo(AnonInnerClass info)
        {
        log(info.getErrorListener(true), Severity.ERROR, Compiler.INVALID_ANON_CLASS_TYPE);
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        if (mgr.processChildren())
            {
            ensureTypeConstant();
            }
        else
            {
            mgr.requestRevisit();
            }
        }


    // ----- Expression methods --------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        TypeConstant type = ensureTypeConstant();
        if (type == null)
            {
            throw new IllegalStateException("type has not yet been determined for this: " + this);
            }

        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(pool.typeType(), type);
        }

    @Override
    protected TypeFit calcFit(Context ctx, TypeConstant typeIn, TypeConstant typeOut)
        {
        return typeIn.isTypeOfType() && (typeOut == null || typeOut.isTypeOfType())
            ? super.calcFit(ctx, getSafeDataType(typeIn), getSafeDataType(typeOut))
            : super.calcFit(ctx, typeIn, typeOut);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool pool          = pool();
        TypeConstant typeReferent  = ensureTypeConstant();
        TypeConstant typeReference = pool.ensureParameterizedTypeConstant(pool.typeType(), typeReferent);
        return finishValidation(typeRequired, typeReference, TypeFit.Fit, typeReferent, errs);
        }

    @Override
    protected Expression finishValidation(TypeConstant typeRequired, TypeConstant typeActual,
                                          TypeFit fit, Constant constVal, ErrorListener errs)
        {
        Expression expr = super.finishValidation(typeRequired, typeActual, fit, constVal, errs);
        if (expr instanceof TypeExpression)
            {
            ((TypeExpression) expr).resetTypeConstant();
            }
        return expr;
        }

    @Override
    protected TypeConstant inferTypeFromRequired(TypeConstant typeActual, TypeConstant typeRequired)
        {
        if (typeActual.isTypeOfType() && typeRequired.isTypeOfType())
            {
            TypeConstant typeInferredReferent = super.inferTypeFromRequired(
                getSafeDataType(typeActual), getSafeDataType(typeRequired));
            if (typeInferredReferent != null)
                {
                return typeInferredReferent.getType();
                }
            }
        return null;
        }

    /**
     * Trivial helper.
     */
    protected static TypeConstant getSafeDataType(TypeConstant type)
        {
        return type != null && type.isParamsSpecified()
            ? type.getGenericParamType("DataType")
            : null;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The TypeConstant currently associated with this TypeExpression.
     */
    private TypeConstant m_constType;
    }

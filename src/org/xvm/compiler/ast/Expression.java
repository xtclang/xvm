package org.xvm.compiler.ast;


import java.util.Arrays;

import java.util.Set;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.I_Set;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.L_Set;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Move;
import org.xvm.asm.op.P_Set;
import org.xvm.asm.op.Var;

import org.xvm.compiler.Compiler;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.checkElementsNonNull;


/**
 * Base class for all Ecstasy expressions.
 * <p/>
 * Expressions go through a few stages of compilation. Initially, the expressions must determine
 * its arity and its type(s) etc., but there exists more than one possible result in some cases,
 * based on what type is expected or required of the expression. Similarly, the resulting type
 * of this expression could affect the type of a containing expression. To accommodate this, the
 * expression has to be able to answer some hypothetical questions _before_ it validates, and
 * _all_ questions after it validates.
 * <p/>
 * Concepts:
 * <pre>
 * 1. You've got to be able to ask an expression some simple, obvious questions:
 *    a. Single or multi? Most expressions represent a single L-value or R-value, but some
 *       expressions can represent a number of L-values or R-values
 *       - including "conditional" results
 *    b. What is your type? (implicit type)
 *    c. Are you a constant? (i.e. could you be if I asked you to?)
 *    d. Is it possible for you to complete? (e.g. T0D0 does not)
 *    e. Can I use you as an L-Value, i.e. something that I can assign something to?
 *    f. Do you short-circuit? e.g. "a?.b" will short-circuit iff "a" is Null
 *
 * 2. When an expression is capable of being an L-Value, there has to be some sort of L-Value
 *    representation that it can provide. For example, in the assignment "a.b.c.d = e", the left
 *    side expression of "a.b.c.d" needs to produce some code that does the "a.b.c" part, and
 *    then yields an L-Value structure that says "it's the 'd' property on this 'c' thing that
 *    I've resolved up to".
 *    a. local variable (register)
 *       - including the "black hole"
 *    b. property (target and property identifier)
 *       - including knowledge as to whether it can be treated as a "local" property
 *    c. array index (target and index)
 *       - including support for multi-dimensioned arrays (multiple indexes)
 *
 * 3. When an expression is capable of being an L-Value, it can represent more than one L-Value,
 *    for example when it is the left-hand-side of a multi-assignment, or when it represents a
 *    "conditional" value. That means that any expression that can provide more than one L-Value
 *    must implement a plural form of that method.
 *
 * 4. When an expression is capable of being an R-Value, it can represent more than one R-Value,
 *    for example when it is the right-hand-side of a multi-assignment, or when it represents a
 *    "conditional" value. That means that any expression that can provide more than one R-Value
 *    must implement a plural form of that method.
 *
 * 5. When an expression is being used as an R-Value,
 *    a. as a Constant of a specified type
 *    b. as one or more arguments
 *    c. as a conditional jump
 *    d. assignment to one or more L-values
 *
 * 6. An expression that is allowed to short-circuit must be provided with a label to which it can
 *    short-circuit. This also affects the definite assignment rules.
 */
public abstract class Expression
        extends AstNode
    {
    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected boolean usesSuper()
        {
        for (AstNode node : children())
            {
            if (!(node instanceof ComponentStatement) && node.usesSuper())
                {
                return true;
                }
            }

        return false;
        }

    /**
     * @return this expression, converted to a type expression
     */
    public TypeExpression toTypeExpression()
        {
        return new BadTypeExpression(this);
        }

    /**
     * Validate that this expression is structurally correct to be a link-time condition.
     * <p/><code><pre>
     * There are only a few expression forms that are permitted:
     * 1. StringLiteral "." "defined"
     * 2. QualifiedName "." "present"
     * 3. QualifiedName "." "versionMatches" "(" VersionLiteral ")"
     * 4. Any of 1-3 and 5 negated using "!"
     * 5. Any two of 1-5 combined using "&", "&&", "|", or "||"
     * </pre></code>
     *
     * @param errs  the error listener to log any errors to
     *
     * @return true if the expression is structurally valid
     */
    public boolean validateCondition(ErrorListener errs)
        {
        log(errs, Severity.ERROR, Compiler.ILLEGAL_CONDITIONAL);
        return false;
        }

    /**
     * @return this expression as a link-time conditional constant
     */
    public ConditionalConstant toConditionalConstant()
        {
        throw notImplemented();
        }


    // ----- Expression compilation ----------------------------------------------------------------

    /**
     * @return true iff the expression implements the "single type" / "single value" code path
     */
    protected boolean hasSingleValueImpl()
        {
        return true;
        }

    /**
     * @return true iff the expression implements the "n type" / "n value" code path
     */
    protected boolean hasMultiValueImpl()
        {
        return false;
        }

    /**
     * @param errs  the error listener to log to
     *
     * @return an expression that represents the value(s) of this expression as a tuple of those
     *         same values
     */
    protected Expression packedExpression(ErrorListener errs)
        {
        return new PackExpression(this, errs);
        }

    /**
     * @param errs  the error listener to log to
     *
     * @return an array of expressions, one for each field of this tuple
     */
    protected Expression[] unpackedExpressions(ErrorListener errs)
        {
        TypeConstant type = getType();
        if (!type.isTuple())
            {
            throw new IllegalStateException("tuple required");
            }

        if (!type.isParamsSpecified())
            {
            throw new IllegalStateException("tuple field information required");
            }

        int c = type.getParamsCount();
        UnpackExpression[] aExpr = new UnpackExpression[c];
        for (int i = 0; i < c; ++i)
            {
            aExpr[i] = new UnpackExpression(this, aExpr, i, errs);
            }
        return aExpr;
        }

    /**
     * (Pre-validation) Determine the type that the expression will resolve to, if it is given no
     * type inference information. If an expression is not able to determine an implicit type, that
     * indicates that a compile time error is likely to occur when the expression is validated, but
     * such an error is not guaranteed, since validation can introduce a required type that could be
     * utilized to validate the expression.
     *
     * @param ctx  the compilation context for the statement
     *
     * @return the type of the expression, or the first type of the expression if it yields multiple
     *         types, or null if the expression is void (or if the type cannot be determined)
     */
    public TypeConstant getImplicitType(Context ctx)
        {
        if (!hasMultiValueImpl())
            {
            throw notImplemented();
            }

        checkDepth();

        TypeConstant[] aTypes = getImplicitTypes(ctx);
        return aTypes.length == 0
                ? null
                : aTypes[0];
        }

    /**
     * (Pre-validation) Determine the type that the expression will resolve to, if it is given no
     * type inference information.
     *
     * @param ctx  the compilation context for the statement
     *
     * @return an array of the types produced by the expression, or an empty array if the expression
     *         is void (or if its type cannot be determined)
     */
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        if (!hasSingleValueImpl())
            {
            throw notImplemented();
            }

        checkDepth();

        TypeConstant type = getImplicitType(ctx);
        return type == null
                ? TypeConstant.NO_TYPES
                : new TypeConstant[] {type};
        }

    /**
     * (Pre-validation) Determine if the expression can yield the specified type.
     * <p/>
     * This method should be overridden by any Expression type that only expects to result in a
     * single value and has the ability to yield different types depending on what type is required.
     *
     * @param ctx           the compilation context for the statement
     * @param typeRequired  the type that the expression is being asked if it can provide
     *
     * @return a TypeFit value describing the expression's capability (or lack thereof) to produce
     *         the required type
     */
    public TypeFit testFit(Context ctx, TypeConstant typeRequired)
        {
        checkDepth();

        if (typeRequired == null)
            {
            // all expressions are required to be able to yield a void result
            return TypeFit.Fit;
            }

        if (hasSingleValueImpl())
            {
            return calcFit(ctx, getImplicitType(ctx), typeRequired);
            }

        if (hasMultiValueImpl())
            {
            checkDepth();
            return testFitMulti(ctx, new TypeConstant[] {typeRequired});
            }

        throw notImplemented();
        }

    /**
     * (Pre-validation) Determine if the expression can yield the specified types.
     * <p/>
     * This method should be overridden by any Expression type that expects to result in multiple
     * values and has the ability to yield different types depending on what types are required.
     *
     * @param ctx            the compilation context for the statement
     * @param atypeRequired  the types that the expression is being asked if it can provide
     *
     * @return a TypeFit value describing the expression's capability (or lack thereof) to produce
     *         the required type(s)
     */
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired)
        {
        checkDepth();

        switch (atypeRequired.length)
            {
            case 0:
                // all expressions are required to be able to yield a void result
                return TypeFit.Fit;

            case 1:
                if (hasSingleValueImpl())
                    {
                    return testFit(ctx, atypeRequired[0]);
                    }
                // fall through

            default:
                if (hasMultiValueImpl())
                    {
                    checkDepth();
                    return calcFitMulti(ctx, getImplicitTypes(ctx), atypeRequired);
                    }

                // anything that is expected to yield separate values must have a "multi"
                // implementation, so the lack of a "multi" implementation means that this
                // expression can't yield the desired number of values
                return TypeFit.NoFit;
            }
        }

    /**
     * Helper for testFit() and validate() methods.
     *
     * @param ctx      the compilation context for the statement
     * @param typeIn   the type being tested for fit
     * @param typeOut  the type that the expression is being asked if it can provide
     *
     * @return a TypeFit value describing the ability (or lack thereof) to produce the required type
     *         from the specified type
     */
    protected TypeFit calcFit(Context ctx, TypeConstant typeIn, TypeConstant typeOut)
        {
        // there are two simple cases to consider:
        // 1) it is always a fit for an expression to go "to void"
        // 2) the most common / desired case is that the type-in is compatible with the type-out
        if (typeOut == null || typeIn.isA(typeOut))
            {
            return TypeFit.Fit;
            }

        // see if we can infer some type information from the "required" type
        if (inferTypeFromRequired(typeIn, typeOut) != null)
            {
            return TypeFit.Fit;
            }

        // check for the existence of an @Auto conversion
        if (typeIn.ensureTypeInfo().findConversion(typeOut) != null)
            {
            return TypeFit.Conv;
            }

        return TypeFit.NoFit;
        }

    /**
     * Helper for testFit() and validate() methods.
     *
     * @param ctx       the compilation context for the statement
     * @param atypeIn   the type(s) being tested for fit
     * @param atypeOut  the types that the expression is being asked if it can provide
     *
     * @return a TypeFit value describing the ability (or lack thereof) to produce the required type
     *         from the specified type
     */
    protected TypeFit calcFitMulti(Context ctx, TypeConstant[] atypeIn, TypeConstant[] atypeOut)
        {
        int cTypesIn  = atypeIn.length;
        int cTypesOut = atypeOut.length;
        if (cTypesIn < cTypesOut)
            {
            return TypeFit.NoFit;
            }

        if (cTypesIn == 1 && cTypesOut <= 1)
            {
            return calcFit(ctx, atypeIn[0], cTypesOut == 0 ? null : atypeOut[0]);
            }

        TypeFit fitOut = TypeFit.Fit;
        for (int i = 0; i < cTypesOut; ++i)
            {
            TypeConstant typeIn    = atypeIn [i];
            TypeConstant typeOut   = atypeOut[i];
            TypeFit      fitSingle = calcFit(ctx, typeIn, typeOut);
            if (!fitOut.isFit())
                {
                return TypeFit.NoFit;
                }

            fitOut = fitOut.combineWith(fitSingle);
            }

        return fitOut;
        }

    // TODO need a helper for tuple stuff

    /**
     * Given the specified required type for the expression, resolve names, values, verify definite
     * assignment, etc.
     * <p/>
     * This method transitions the expression from "pre-validated" to "validated".
     * <p/>
     * This method should be overridden by any Expression type that only expects to result in a
     * single value.
     *
     * @param ctx           the compilation context for the statement
     * @param typeRequired  the type that the expression is expected to be able to provide, or null
     *                      if no particular type is expected (which requires the expression to
     *                      settle on a type on its own)
     * @param errs          the error listener to log to
     *
     * @return the resulting expression (typically this), or null if compilation cannot proceed
     */
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        if (!hasMultiValueImpl())
            {
            throw notImplemented();
            }

        checkDepth();

        TypeConstant[] aTypes = typeRequired == null
                ? TypeConstant.NO_TYPES
                : new TypeConstant[] {typeRequired};

        return validateMulti(ctx, aTypes, errs);
        }

    /**
     * Given the specified required type(s) for the expression, resolve names, values, verify
     * definite assignment, etc.
     * <p/>
     * This method transitions the expression from "pre-validated" to "validated".
     * <p/>
     * This method must be overridden by any Expression type that expects to result in multiple
     * values.
     *
     * @param ctx            the compilation context for the statement
     * @param atypeRequired  an array of required types
     * @param errs           the error listener to log to
     *
     * @return the resulting expression (typically this), or null if compilation cannot proceed
     */
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        checkDepth();

        int cTypesRequired = atypeRequired.length;
        if (cTypesRequired > 1)
            {
            // log the error, but allow validation to continue (with no particular
            // expected type) so that we get as many errors exposed as possible in the
            // validate phase
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, atypeRequired.length, 1);
            finishValidations(atypeRequired, atypeRequired, TypeFit.Fit, null, errs);
            return null;
            }

        if (hasSingleValueImpl())
            {
            return validate(ctx, cTypesRequired == 0 ? null : atypeRequired[0], errs);
            }

        throw notImplemented();
        }

    /**
     * Store the result of validating the Expression.
     *
     * @param typeRequired  the type that the expression must yield (optional)
     * @param typeActual    the type of the expression at this point (required)
     * @param fit           the fit of that type that was determined by the validation (required);
     *                      {@link TypeFit#NoFit} indicates that a type error has already been
     *                      logged; {@link TypeFit#isConverting()} indicates that a type conversion
     *                      has already been applied; {@link TypeFit#isPacking()} indicates that a
     *                      tuple packing has already been applied; {@link TypeFit#isUnpacking()}
     *                      indicates that a tuple un-packing has already been applied
     * @param constVal      a constant value, iff this expression is constant (optional)
     * @param errs          the error list to log any errors to
     *
     * @return an expression to use (which may or may not be "this"), or null to indicate that the
     *         compilation should halt as soon as is practical
     */
    protected Expression finishValidation(
            TypeConstant  typeRequired,
            TypeConstant  typeActual,
            TypeFit       fit,
            Constant      constVal,
            ErrorListener errs)
        {
        assert typeActual != null;
        assert fit != null;

        // if there is a constant value, then the type itself indicates the immutable nature of the
        // expression
        if (constVal != null)
            {
            typeActual = typeActual.ensureImmutable();
            }

        // if a required type is specified and the expression type isn't of the required type, then
        // an @Auto conversion can be used, assuming that we haven't already given up on the type
        // or already applied a type conversion
        MethodConstant idConv = null;
        if (typeRequired != null && fit.isFit() && !typeActual.isA(typeRequired))
            {
            // first, see if we can infer some type information from the "required" type
            TypeConstant typeInferred = inferTypeFromRequired(typeActual, typeRequired);
            if (typeInferred == null)
                {
                // a conversion may be necessary to deliver the required type, but only one
                // conversion (per expression value) is allowed
                if (fit.isConverting() ||
                        (idConv = typeActual.ensureTypeInfo().findConversion(typeRequired)) == null)
                    {
                    // cannot provide the required type
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                            typeRequired.getValueString(), typeActual.getValueString());

                    // pretend that we were able to do the necessary conversion (but note that there
                    // was a type fit error)
                    fit        = TypeFit.NoFit;
                    typeActual = typeRequired;
                    if (constVal != null)
                        {
                        // pretend that it was a constant
                        constVal   = generateFakeConstant(typeRequired);
                        typeActual = typeActual.ensureImmutable();
                        }
                    }
                }
            else
                {
                typeActual = typeInferred;
                }
            }

        m_fit    = fit;
        m_oType  = typeActual;
        m_oConst = constVal;

        // if we found an @Auto conversion, then create an expression that does the conversion work
        return idConv == null
                ? this
                : new ConvertExpression(this, 0, idConv, errs);
        }

    /**
     * Perform left-to-right inference of type information by augmenting the actual (right)
     * expression type from the required (left) type.
     *
     * @param typeActual    the actual type
     * @param typeRequired  the required type
     *
     * @return the inferred actual type iff it "isA" required type; null otherwise
     */
    protected TypeConstant inferTypeFromRequired(TypeConstant typeActual, TypeConstant typeRequired)
        {
        if (typeRequired.isParamsSpecified() && !typeActual.isParamsSpecified())
            {
            TypeConstant typeInferred = typeActual.adoptParameters(typeRequired.getParamTypesArray());
            if (typeInferred.isA(typeRequired))
                {
                return typeInferred;
                }
            }
        return null;
        }

    /**
     * Store the result of validating the Expression.
     *
     * @param atypeRequired  the (optional) types required from the Expression (both the array and
     *                       any of its elements can be null)
     * @param aTypeActual    the types that result from the Expression (neither the array nor its
     *                       elements can be null)
     * @param fit            the fit of those types that was determined by the validation;
     *                       {@link TypeFit#NoFit} indicates that a type error has already been
     *                       logged; {@link TypeFit#isConverting()} indicates that a type conversion
     *                       has already been applied; {@link TypeFit#isPacking()} indicates that a
     *                       tuple packing has already been applied; {@link TypeFit#isUnpacking()}
     *                       indicates that a tuple un-packing has already been applied
     * @param aconstVal      an array of constant values, equal in length to the array of types, iff
     *                       this expression is constant
     * @param errs           the error list to log any errors to
     *
     * @return this or null
     */
    protected Expression finishValidations(
            TypeConstant[] atypeRequired,
            TypeConstant[] aTypeActual,
            TypeFit        fit,
            Constant[]     aconstVal,
            ErrorListener  errs)
        {
        assert atypeRequired == null || checkElementsNonNull(atypeRequired);
        assert aTypeActual != null && checkElementsNonNull(aTypeActual);
        assert fit != null;
        assert aconstVal == null || (aconstVal.length == aTypeActual.length && checkElementsNonNull(aconstVal));

        int cActual   = aTypeActual.length;
        int cTypeReqs = atypeRequired == null ? 0 : atypeRequired.length;
        if (cTypeReqs > cActual && fit.isFit())
            {
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, cTypeReqs, cActual);
            }

        // for expressions that yield constant values, make sure that the types reflect that
        if (aconstVal != null)
            {
            for (int i = 0; i < cActual; ++i)
                {
                aTypeActual[i] = aTypeActual[i].ensureImmutable();
                }
            }

        MethodConstant[] aIdConv = null;
        if (cTypeReqs > 0 && fit.isFit() && !fit.isConverting())
            {
            for (int i = 0, c = Math.min(cActual, cTypeReqs); i < c; ++i)
                {
                TypeConstant typeActual   = aTypeActual[i];
                TypeConstant typeRequired = atypeRequired[i];
                if (!typeActual.isA(typeRequired))
                    {
                    // look for an @Auto conversion
                    MethodConstant idConv = typeActual.ensureTypeInfo().findConversion(typeRequired);
                    if (idConv == null)
                        {
                        // cannot provide the required type
                        log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                typeRequired.getValueString(), typeActual.getValueString());

                        // pretend that we were able to do the necessary conversion (but note that there
                        // was a type fit error)
                        fit        = TypeFit.NoFit;
                        typeActual = typeRequired;
                        if (aconstVal[i] != null)
                            {
                            // pretend that it was a constant
                            aconstVal[i] = generateFakeConstant(typeRequired);
                            typeActual   = typeActual.ensureImmutable();
                            }
                        aTypeActual[i] = typeActual;
                        }
                    else
                        {
                        if (aIdConv == null)
                            {
                            aIdConv = new MethodConstant[cActual];
                            }
                        aIdConv[i] = idConv;
                        }
                    }
                }
            }

        if (cTypeReqs > cActual && aconstVal != null)
            {
            // we've already reported an error for there not being enough values in the
            // expression to meet the required types, but since we're pretending to continue,
            // we might as well make up some constants to match the number of required types
            Constant[] aconstNew = new Constant[cTypeReqs];
            System.arraycopy(aconstVal, 0, aconstNew, 0, cActual);
            aconstVal = aconstNew;
            for (int i = cActual; i < cTypeReqs; ++i)
                {
                aconstVal[i] = generateFakeConstant(atypeRequired[i]);
                }
            }

        m_fit    = fit;
        m_oType  = fit.isFit() || atypeRequired == null ? aTypeActual : atypeRequired;
        m_oConst = aconstVal;

        // apply any conversions that we found previously to be necessary to deliver the required
        // data types
        Expression exprResult = this;
        if (aIdConv != null)
            {
            for (int i = 0; i < cActual; ++i)
                {
                MethodConstant idConv = aIdConv[i];
                if (idConv != null)
                    {
                    exprResult = new ConvertExpression(exprResult, i, idConv, errs);
                    }
                }
            }
        return exprResult;
        }

    /**
     * @return true iff the Expression has been validated
     */
    public boolean isValidated()
        {
        return m_fit != null;
        }

    /**
     * Throw an exception if the Expression has not been validated
     */
    protected void checkValidated()
        {
        if (!isValidated())
            {
            throw new IllegalStateException("Expression has not been validated: " + this);
            }
        }

    /**
     * (Post-validation) Determine the number of values represented by the expression.
     * <ul>
     * <li>A {@code void} expression represents no values</li>
     * <li>An {@link #isSingle() isSingle()==true} expression represents exactly one value (most
     *     common)</li>
     * <li>A multi-value expression represents more than one value</li>
     * </ul>
     *
     * @return the number of values represented by the expression
     */
    public int getValueCount()
        {
        checkValidated();

        return m_oType instanceof TypeConstant[]
                ? ((TypeConstant[]) m_oType).length
                : 1;
        }

    /**
     * (Post-validation) Determine if the Expression represents no resulting value.
     *
     * @return true iff the Expression represents a "void" expression
     */
    public boolean isVoid()
        {
        return getValueCount() == 0;
        }

    /**
     * (Post-validation) Determine if the Expression represents exactly one value.
     *
     * @return true iff the Expression represents exactly one value
     */
    public boolean isSingle()
        {
        return getValueCount() == 1;
        }

    // REVIEW who needs this method?
//    /**
//     * (Post-validation) Determine if the expression represents a {@code conditional} result. A
//     * conditional result is one in which there are multiple results, the first of which is a
//     * boolean, and the remainder of which cannot be safely accessed if the runtime value of that
//     * first boolean is {@code false}.
//     * <p/>
//     * This method must be overridden by any expression that represents or could represent a
//     * conditional result, including as the result of composition of other expressions that could
//     * represent a conditional result.
//     *
//     * @return true iff the Expression represents a conditional value
//     */
//    public boolean isConditional()
//        {
//        return false;
//        }

    /**
     * @return the TypeFit that was determined during validation
     */
    public TypeFit getTypeFit()
        {
        checkValidated();

        return m_fit;
        }

    /**
     * (Post-validation) Determine the type of the expression. For a multi-value expression, the
     * first TypeConstant is returned. For a void expression, the result is null.
     *
     * @return the type of the validated Expression, which is null for a Expression that yields a
     *         void result, otherwise the type of the <i>first</i> (and typically <i>only</i>) value
     *         resulting from the Expression
     */
    public TypeConstant getType()
        {
        checkValidated();

        return m_oType instanceof TypeConstant
                ? (TypeConstant) m_oType
                : ((TypeConstant[]) m_oType)[0];
        }

    /**
     * (Post-validation) Obtain an array of types, one for each value that this expression yields.
     * For a void expression, the result is a zero-length array.
     *
     * @return the types of the multiple values yielded by the expression; a zero-length array
     *         indicates a void type
     */
    public TypeConstant[] getTypes()
        {
        if (!(m_oType instanceof TypeConstant[]))
            {
            m_oType = new TypeConstant[] {getType()};
            }

        return (TypeConstant[]) m_oType;
        }

    /**
     * (Post-validation) Determine if the expression represents an L-Value, which means that this
     * expression can be assigned to.
     * <p/>
     * This method must be overridden by any expression that represents an L-Value, or that could
     * be composed of other expressions such that the result represents an L-Value.
     *
     * @return true iff the Expression represents an "L-value" to which a value can be assigned
     */
    public boolean isAssignable()
        {
        return false;
        }

    /**
     * (Post-validation) Determine if the expression aborts.
     * <p/>
     * This method must be overridden by any expression does not complete, or that contains
     * another expression that may not be completable.
     *
     * @return true iff the expression is capable of completing normally
     */
    public boolean isAborting()
        {
        return false;
        }

    /**
     * (Post-validation) Determine if the expression can short-circuit.
     * <p/>
     * This method must be overridden by any expression can short circuit, or any expression that
     * can short circuit as a result of containing another expression that may short-circuit.
     *
     * @return true iff the expression is capable of short-circuiting
     */
    public boolean isShortCircuiting()
        {
        return false;
        }

    /**
     * @return true iff the expression represents a non-value ('?') used to explicitly indicate an
     *         unbound parameter
     */
    public boolean isNonBinding()
        {
        return false;
        }

    /**
     * (Post-validation) Determine if the expression should be treated as a constant value. There
     * are a few exceptions, such as the TodoExpression (which claim to be constant, but must
     * actually produce code), or expressions that require code to produce a constant (or at least
     * idempotent) value; as a result, a separate method, {@link #hasConstantValue()}, indicates
     * that not only is the expression a constant, but it can provide its constant values to the
     * compiler.
     *
     * @return true iff the Expression is a constant value that is representable by a constant in
     *         the ConstantPool
     */
    public boolean isConstant()
        {
        return hasConstantValue();
        }

    /**
     * (Post-validation) Determine if the expression has a constant value available for use by the
     * compiler. Some expressions may be constant, but may not be able to provide their value at
     * compile time, because code needs to be generated on behalf of the expression.
     *
     * @return true iff the expression results in a compile-time (ConstantPool) constant value
     */
    public boolean hasConstantValue()
        {
        return m_oConst != null;
        }

    /**
     * (Post-validation) Determine if the expression needs to generate code, even if if it yields
     * a compile-time constant value.
     *
     * @return true iff the expression needs to produce code, regardless of wheterh it yields a
     *         compile-time constant value
     */
    public boolean hasSideEffects()
        {
        // generally, an expression that yields a compile-time constant value does not have
        // side-effects; this must be overridden by any expression that violates this assumption
        return !hasConstantValue();
        }

    /**
     * (Post-validation) For a expression that provides a compile-time constant, indicated by the
     * {@link #hasConstantValue()} method returning true, obtain a constant representation of the
     * value.
     * <p/>
     * If the Expression has more than one value, then this will return the first constant value. If
     * the Expression is <i>void</i>, then this will return null.
     * <p/>
     * An exception is thrown if the expression does not produce a compile-time constant.
     *
     * @return the compile-time constant value of the expression, or null if the expression is not
     *         constant
     */
    public Constant toConstant()
        {
        if (!hasConstantValue())
            {
            return null;
            }

        if (m_oConst instanceof Constant)
            {
            return (Constant) m_oConst;
            }

        return ((Constant[]) m_oConst)[0];
        }

    /**
     * (Post-validation) For a expression that provides compile-time constants, indicated by the
     * {@link #hasConstantValue()} method returning true, obtain an array of constants that
     * represent the value of the Expression.
     * <p/>
     * If the Expression is <i>void</i>, then this will return an empty array.
     *
     * @return the compile-time constant values of the expression, or null if the expression is not
     *         constant
     */
    public Constant[] toConstants()
        {
        if (!hasConstantValue())
            {
            return null;
            }

        if (!(m_oConst instanceof Constant[]))
            {
            m_oConst = new Constant[] {toConstant()};
            }

        return (Constant[]) m_oConst;
        }

    /**
     * Generate the necessary code that discards the value of this expression.
     * <p/>
     * This method should be overridden by any expression that can produce better code than the
     * default discarded-assignment code.
     *
     * @param code  the code block
     * @param errs  the error list to log any errors to
     */
    public void generateVoid(Code code, ErrorListener errs)
        {
        checkDepth();

        // a lack of side effects means that the expression can be ignored altogether
        if (hasSideEffects())
            {
            if (isSingle() && hasSingleValueImpl())
                {
                generateAssignment(code, new Assignable(), errs);
                }
            else
                {
                Assignable[] asnVoid = new Assignable[getValueCount()];
                Arrays.fill(asnVoid, new Assignable());
                generateAssignments(code, asnVoid, errs);
                }
            }
        }

    /**
     * (Post-validation) Generate an argument that represents the result of this expression.
     * <p/>
     * If the expression {@link #hasSingleValueImpl()} is {@code true}, then this method or
     * {@link #generateAssignment} must be overridden.
     *
     * @param code          the code block
     * @param fLocalPropOk  true if the resulting arguments can be expressed as property constants
     *                      if the argument values are local properties
     * @param fUsedOnce     enables use of the "frame-local stack"
     * @param errs          the error list to log any errors to
     *
     * @return a resulting argument of the validated type
     */
    public Argument generateArgument(Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        checkDepth();
        assert !isVoid();

        if (hasConstantValue())
            {
            return toConstant();
            }

        if (hasMultiValueImpl() && (!hasSingleValueImpl() || !isSingle()))
            {
            return generateArguments(code, fLocalPropOk, fUsedOnce, errs)[0];
            }

        if (hasSingleValueImpl() && !isVoid())
            {
            Assignable var = createTempVar(code, getType(), fUsedOnce, errs);
            generateAssignment(code, var, errs);
            return var.getRegister();
            }

        throw notImplemented();
        }

    /**
     * Generate arguments of the specified types for this expression, or generate an error if that
     * is not possible.
     * <p/>
     * This method must be overridden by any expression that is multi-value-aware.
     *
     * @param code          the code block
     * @param fLocalPropOk  true if the resulting arguments can be expressed as property constants
     *                      if the argument values are local properties
     * @param fUsedOnce     enables use of the "frame-local stack" (for <b><i>up to one of</i></b>
     *                      the resulting arguments)
     * @param errs          the error list to log any errors to
     *
     * @return an array of resulting arguments, which will either be the same length as the value
     *         count of the expression, or length 1 for a tuple result iff fPack is true
     */
    public Argument[] generateArguments(Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        checkDepth();

        if (hasConstantValue())
            {
            return toConstants();
            }

        if (isVoid())
            {
            // void means that the results of the expression are black-holed
            generateAssignments(code, NO_LVALUES, errs);
            return NO_RVALUES;
            }

        if (hasSingleValueImpl() && isSingle())
            {
            // optimize for single argument case
            return new Argument[] { generateArgument(code, fLocalPropOk, fUsedOnce, errs) };
            }

        TypeConstant[] aTypes = getTypes();
        int            cTypes = aTypes.length;
        Assignable[]   aLVals = new Assignable[cTypes];
        aLVals[0] = createTempVar(code, aTypes[0], fUsedOnce, errs);
        for (int i = 1; i < cTypes; ++i)
            {
            aLVals[i] = createTempVar(code, aTypes[i], false, errs);
            }
        generateAssignments(code, aLVals, errs);

        // the temporaries are each represented by a register; return those registers as the
        // generated arguments
        Register[] aRegs = new Register[cTypes];
        for (int i = 0; i < cTypes; ++i)
            {
            aRegs[i] = aLVals[i].getRegister();
            }
        return aRegs;
        }

    /**
     * Generate the necessary code that assigns the value of this expression to the specified
     * L-Value, or generate an error if that is not possible.
     * <p/>
     * This method should be overridden by any expression that can produce better code than the
     * default assignment code.
     *
     * @param code  the code block
     * @param LVal  the Assignable object representing the L-Value
     * @param errs  the error list to log any errors to
     */
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        checkDepth();

        if (hasSingleValueImpl())
            {
            // this will be overridden by classes that can push down the work
            Argument arg = generateArgument(code, LVal.supportsLocalPropMode(), true, errs);
            LVal.assign(arg, code, errs);
            return;
            }

        if (hasMultiValueImpl())
            {
            generateAssignments(code, new Assignable[] {LVal}, errs);
            return;
            }

        throw notImplemented();
        }

    /**
     * Generate the necessary code that assigns the values of this expression to the specified
     * L-Values, or generate an error if that is not possible.
     * <p/>
     * This method should be overridden by any expression that must support multi-values and can
     * produce better code than the default assignment code.
     *
     * @param code   the code block
     * @param aLVal  an array of Assignable objects representing the L-Values
     * @param errs   the error list to log any errors to
     */
    public void generateAssignments(Code code, Assignable[] aLVal, ErrorListener errs)
        {
        checkDepth();

        int cLVals = aLVal.length;
        int cRVals = getValueCount();
        assert cLVals <= cRVals;
        if (cLVals < cRVals)
            {
            // blackhole the missing LVals
            Assignable[] aLValNew = new Assignable[cRVals];
            Arrays.fill(aLValNew, new Assignable());
            System.arraycopy(aLVal, 0, aLValNew, 0, cLVals);
            aLVal  = aLValNew;
            cLVals = cRVals;
            }

        if (!m_fInAssignment)
            {
            switch (cLVals)
                {
                case 0:
                    m_fInAssignment = true;
                    generateVoid(code, errs);
                    m_fInAssignment = false;
                    return;

                case 1:
                    if (hasSingleValueImpl())
                        {
                        m_fInAssignment = true;
                        generateAssignment(code, aLVal[0], errs);
                        m_fInAssignment = false;
                        return;
                        }
                }
            }

        if (hasMultiValueImpl())
            {
            boolean fLocalPropOk = true;
            for (int i = 0; i < cLVals; ++i)
                {
                fLocalPropOk &= aLVal[i].supportsLocalPropMode();
                }
            Argument[] aArg = generateArguments(code, fLocalPropOk, true, errs);
            for (int i = 0; i < cLVals; ++i)
                {
                aLVal[i].assign(aArg[i], code, errs);
                }
            return;
            }

        throw notImplemented();
        }

    /**
     * Generate the necessary code that jumps to the specified label if this expression evaluates
     * to the boolean value indicated in <tt>fWhenTrue</tt>.
     *
     * @param code       the code block
     * @param label      the label to conditionally jump to
     * @param fWhenTrue  indicates whether to jump when this expression evaluates to true, or
     *                   whether to jump when this expression evaluates to false
     * @param errs       the error list to log any errors to
     */
    public void generateConditionalJump(Code code, Label label, boolean fWhenTrue, ErrorListener errs)
        {
        checkDepth();

        assert !isVoid() && getType().isA(pool().typeBoolean());

        // this is just a generic implementation; sub-classes should override this simplify the
        // generated code (e.g. by not having to always generate a separate boolean value)
        Argument arg = generateArgument(code, true, true, errs);
        code.add(fWhenTrue
                ? new JumpTrue(arg, label)
                : new JumpFalse(arg, label));
        }

    /**
     * Produce a temporary variable.
     *
     * @param code       the code block
     * @param type       the type of the temporary variable
     * @param fUsedOnce  true iff the value will be used once and only once (such that the local
     *                   stack can be utilized for storage)
     * @param errs       the error list to log any errors to
     *
     * @return the Assignable representing the temporary variable; the Assignable will contain a
     *         Register
     */
    protected Assignable createTempVar(Code code, TypeConstant type, boolean fUsedOnce, ErrorListener errs)
        {
        Register reg;
        if (fUsedOnce)
            {
            reg = new Register(type, Op.A_STACK);
            }
        else
            {
            reg = new Register(type);
            code.add(new Var(reg));
            }
        return new Assignable(reg);
        }

    /**
     * For an L-Value expression with exactly one value, create a representation of the L-Value.
     * <p/>
     * An exception is generated if the expression is not assignable.
     * <p/>
     * This method must be overridden by any expression that is assignable, unless the multi-value
     * version of this method is overridden instead.
     *
     * @param code  the code block
     * @param errs  the error list to log any errors to
     *
     * @return an Assignable object
     */
    public Assignable generateAssignable(Code code, ErrorListener errs)
        {
        checkDepth();

        if (!isAssignable() || isVoid())
            {
            throw new IllegalStateException();
            }

        if (hasMultiValueImpl())
            {
            return generateAssignables(code, errs)[0];
            }

        throw notImplemented();
        }

    /**
     * For an L-Value expression, create representations of the L-Values.
     * <p/>
     * An exception is generated if the expression is not assignable.
     * <p/>
     * This method must be overridden by any expression that is assignable and multi-value-aware.
     *
     * @param code  the code block
     * @param errs  the error list to log any errors to
     *
     * @return an array of {@link #getValueCount()} Assignable objects
     */
    public Assignable[] generateAssignables(Code code, ErrorListener errs)
        {
        checkDepth();

        if (isVoid())
            {
            generateVoid(code, errs);
            return NO_LVALUES;
            }

        if (hasSingleValueImpl() && isSingle())
            {
            return new Assignable[] { generateAssignable(code, errs) };
            }

        // a sub-class should have overridden this method
        assert isAssignable();
        throw hasMultiValueImpl()
                ? notImplemented()
                : new IllegalStateException();
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Determine if this expression can generate an argument of the specified type, or that can be
     * assigned to the specified type.
     *
     * @param typeThat  an argument type
     *
     * @return true iff this expression can be rendered as the specified argument type
     */
    public boolean isAssignableTo(TypeConstant typeThat)
        {
        TypeConstant typeImplicit = getType();
        return typeImplicit.isA(typeThat)
                || typeImplicit.ensureTypeInfo().findConversion(typeThat) != null;
        }

    /**
     * @return true iff the Expression is of the type "Boolean"
     */
    public boolean isTypeBoolean()
        {
        return getType().isEcstasy("Boolean");
        }

    /**
     * @return true iff the Expression is the constant value "false"
     */
    public boolean isConstantFalse()
        {
        return hasConstantValue() && toConstant().equals(pool().valFalse());
        }

    /**
     * @return true iff the Expression is the constant value "false"
     */
    public boolean isConstantTrue()
        {
        return hasConstantValue() && toConstant().equals(pool().valTrue());
        }

    /**
     * @return true iff the Expression is the constant value "Null"
     */
    public boolean isConstantNull()
        {
        return hasConstantValue() && toConstant().equals(pool().valNull());
        }

    /**
     * Given an constant, attempt to convert it to the specified type.
     *
     * @param constIn  the constant
     * @param typeOut  the type that the constant must be assignable to
     *
     * @return the constant to use, or null if conversion is not possible
     */
    protected Constant convertConstant(Constant constIn, TypeConstant typeOut)
        {
        TypeConstant typeIn = constIn.getType();
        if (typeIn.isA(typeOut))
            {
            // common case; no conversion is necessary
            return constIn;
            }

        try
            {
            return constIn.convertTo(typeOut);
            }
        catch (ArithmeticException e)
            {
            return null;
            }
        }

    /**
     * Given an constant, verify that it can be assigned to (or somehow converted to) the specified
     * type, and do so.
     *
     * @param constIn  the constant that needs to be validated as assignable
     * @param typeOut  the type that the constant must be assignable to
     * @param errs     the error list to log any errors to, for example if the constant cannot be
     *                 coerced in a manner to make it assignable
     *
     * @return the constant to use
     */
    protected Constant validateAndConvertConstant(Constant constIn, TypeConstant typeOut, ErrorListener errs)
        {
        TypeConstant typeIn = constIn.getType();
        if (typeIn.isA(typeOut))
            {
            // common case; no conversion is necessary
            return constIn;
            }

        Constant constOut;
        try
            {
            constOut = constIn.convertTo(typeOut);
            }
        catch (ArithmeticException e)
            {
            // conversion failure due to range etc.
            log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, typeOut,
                    constIn.getValueString());
            return generateFakeConstant(typeOut);
            }

        if (constOut == null)
            {
            // conversion apparently was not possible
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeOut, typeIn);
            constOut = generateFakeConstant(typeOut);
            }

        return constOut;
        }

    /**
     * Helper to find an op method.
     *
     * @param ctx          the compilation context
     * @param typeTarget   the type on which to search for the op
     * @param sMethodName  default name of the op method
     * @param sOp          the operator string
     * @param aexprParams  the (optional) parameter expressions (which may not yet be validated)
     * @param typeReturn   the (optional) return type from the op
     * @param fRequired    true if the op method must be found
     * @param errs         listener to log any errors to
     *
     * @return the MethodConstant for the desired op, or null if an exact match was not found
     */
    public MethodConstant findOpMethod(
            Context       ctx,
            TypeConstant  typeTarget,
            String        sMethodName,
            String        sOp,
            Expression[]  aexprParams,
            TypeConstant  typeReturn,
            boolean       fRequired,
            ErrorListener errs)
        {
        assert sMethodName != null && sMethodName.length() > 0;
        assert sOp         != null && sOp        .length() > 0;

        TypeInfo infoTarget = typeTarget.ensureTypeInfo(errs);
        int      cParams    = aexprParams == null ? -1 : aexprParams.length;
        Set<MethodConstant> setMethods = infoTarget.findOpMethods(sMethodName, sOp, cParams);
        if (setMethods.isEmpty())
            {
            if (fRequired)
                {
                if (cParams < 0 || infoTarget.findOpMethods(sMethodName, sOp, -1).isEmpty())
                    {
                    log(errs, Severity.ERROR, Compiler.MISSING_OPERATOR,
                            sOp, typeTarget.getValueString());
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.MISSING_OPERATOR_SIGNATURE,
                            sOp, typeTarget.getValueString(), cParams);
                    }
                }
            return null;
            }

        TypeConstant[] atypeParams = null;
        if (cParams > 0)
            {
            atypeParams = new TypeConstant[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                Expression exprParam = aexprParams[i];
                if (exprParam != null)
                    {
                    atypeParams[i] = exprParam.isValidated() ? getType() : getImplicitType(ctx);
                    }
                }
            }

        MethodConstant idBest = null;
        NextOp: for (MethodConstant idOp : setMethods)
            {
            if (cParams > 0)
                {
                // TODO eventually this logic has to support parameters with default values
                TypeConstant[] atypeOpParams = idOp.getRawParams();
                int            cOpParams     = atypeOpParams.length;
                if (cParams != cOpParams)
                    {
                    continue NextOp;
                    }

                for (int i = 0; i < cParams; ++i)
                    {
                    if (atypeParams[i] != null && atypeParams[i].isAssignableTo(atypeOpParams[i]))
                        {
                        continue NextOp;
                        }
                    }
                }

            if (typeReturn != null)
                {
                TypeConstant typeOpReturn = idOp.getRawReturns()[0];
                if (!typeOpReturn.isAssignableTo(typeReturn))
                    {
                    continue NextOp;
                    }
                }

            if (idBest != null)
                {
                boolean fOldBetter = idOp.getSignature().isSubstitutableFor(idBest.getSignature(), typeTarget);
                boolean fNewBetter = idBest.getSignature().isSubstitutableFor(idOp.getSignature(), typeTarget);
                if (fOldBetter ^ fNewBetter)
                    {
                    if (fNewBetter)
                        {
                        idBest = idOp;
                        }
                    }
                else
                    {
                    // note: theoretically could still be one better than either of these two, but
                    // for now, just assume it's an error at this point
                    log(errs, Severity.ERROR, Compiler.AMBIGUOUS_OPERATOR_SIGNATURE,
                                sOp, typeTarget.getValueString());
                    return null;
                    }
                }
            }

        if (idBest == null && fRequired)
            {
            log(errs, Severity.ERROR, Compiler.MISSING_OPERATOR_SIGNATURE,
                    sOp, typeTarget.getValueString(), cParams);
            }

        return idBest;
        }


    /**
     * Generate a "this" or some other reserved register.
     *
     * @param nReg  the register identifier
     * @param errs  the error list to log to
     *
     * @return the reserved register
     */
    protected Argument generateReserved(int nReg, ErrorListener errs)
        {
        boolean         fNoFunction  = true;
        boolean         fNoConstruct = true;
        ConstantPool    pool         = pool();
        MethodStructure method       = (MethodStructure) getComponent();
        TypeConstant    type         = method.getContainingClass().getIdentityConstant().getType();
        switch (nReg)
            {
            case Op.A_TARGET:
                break;

            case Op.A_PUBLIC:
                type = pool.ensureAccessTypeConstant(type, Access.PUBLIC);
                break;

            case Op.A_PROTECTED:
                type = pool.ensureAccessTypeConstant(type, Access.PROTECTED);
                break;

            case Op.A_PRIVATE:
                type = pool.ensureAccessTypeConstant(type, Access.PRIVATE);
                break;

            case Op.A_STRUCT:
                type = pool.ensureAccessTypeConstant(type, Access.STRUCT);
                fNoConstruct = false;
                break;

            case Op.A_SERVICE:
                type = pool.typeService();
                fNoFunction  = false;
                fNoConstruct = false;
                break;

            case Op.A_SUPER:
                {
                TypeInfo       info       = type.ensureTypeInfo(errs);
                MethodConstant idMethod   = method.getIdentityConstant();
                MethodInfo     infoMethod = info.getMethodById(idMethod);

                if (!infoMethod.hasSuper(info))
                    {
                    log(errs, Severity.ERROR, Compiler.NO_SUPER);
                    }

                type = idMethod.getSignature().asFunctionType();
                break;
                }

            default:
                throw new IllegalArgumentException("nReg=" + nReg);
            }

        if (fNoFunction && method.isFunction() && !method.isConstructor()
                || fNoConstruct && method.isConstructor())
            {
            log(errs, Severity.ERROR, Compiler.NO_THIS);
            }

        return new Register(type, nReg);
        }

    /**
     * When a register is needed to store a value that is never used, the "black hole" register is
     * used. It is considered a "write only" register. This is also useful during compilation, when
     * an expression cannot yield an actual argument; the expression should log an error, and return
     * a black hole register instead (which will serve as a natural assertion later in the assembly
     * cycle, in case someone forgets to log an error).
     *
     * @param type  the type of the register
     *
     * @return a black hole register of the specified type
     */
    protected Register generateBlackHole(TypeConstant type)
        {
        return new Register(type, Op.A_IGNORE);
        }

    /**
     * When an error occurs during compilation, but a constant of a specific type is required, this
     * method comes to the rescue.
     *
     * @param type  the type of the constant
     *
     * @return a constant of the specified type
     */
    protected Constant generateFakeConstant(TypeConstant type)
        {
        return Constant.defaultValue(type);
        }

    /**
     * Temporary to prevent stack overflow from methods that haven't yet been overridden.
     *
     * @throws UnsupportedOperationException if it appears that there is an infinite loop
     */
    protected void checkDepth()
        {
        if (++m_cDepth > 40)
            {
            throw notImplemented();
            }
        }


    // ----- inner class: Assignable ---------------------------------------------------------------

    /**
     * Assignable represents an L-Value.
     */
    public class Assignable
        {
        // ----- constructors ------------------------------------------------------------------

        /**
         * Construct a black hole L-Value.
         */
        public Assignable()
            {
            m_nForm = BlackHole;
            }

        /**
         * Construct an Assignable based on a local variable.
         *
         * @param regVar  the Register, representing the local variable
         */
        public Assignable(Register regVar)
            {
            m_nForm = LocalVar;
            m_arg   = regVar;
            }

        /**
         * Construct an Assignable based on a property (either local or "this").
         *
         * @param regTarget  the register, representing the property target
         * @param constProp  the PropertyConstant
         */
        public Assignable(Register regTarget, PropertyConstant constProp)
            {
            m_nForm = regTarget.getIndex() == Op.A_TARGET ? LocalProp : TargetProp;
            m_arg   = regTarget;
            m_prop  = constProp;
            }

        /**
         * Construct an Assignable based on a single dimension array local variable.
         *
         * @param argArray  the Register, representing the local variable holding an array
         * @param index     the index into the array
         */
        public Assignable(Argument argArray, Argument index)
            {
            m_nForm  = Indexed;
            m_arg    = argArray;
            m_oIndex = index;
            }

        /**
         * Construct an Assignable based on a multi (any) dimension array local variable.
         *
         * @param regArray  the Register, representing the local variable holding an array
         * @param indexes   an array of indexes into the array
         */
        public Assignable(Argument regArray, Argument[] indexes)
            {
            assert indexes != null && indexes.length > 0;

            m_nForm  = indexes.length == 1 ? Indexed : IndexedN;
            m_arg    = regArray;
            m_oIndex = indexes.length == 1 ? indexes[0] : indexes;
            }

        /**
         * Construct an Assignable based on a local property that is a single dimension array.
         *
         * @param argArray  the Register, representing the local variable holding an array
         * @param index     the index into the array
         */
        public Assignable(Argument argArray, PropertyConstant constProp, Argument index)
            {
            m_nForm  = IndexedProp;
            m_arg    = argArray;
            m_prop   = constProp;
            m_oIndex = index;
            }

        /**
         * Construct an Assignable based on a local property that is a multi (any) dimension array.
         *
         * @param regArray  the Register, representing the local variable holding an array
         * @param indexes   an array of indexes into the array
         */
        public Assignable(Argument regArray, PropertyConstant constProp, Argument[] indexes)
            {
            assert indexes != null && indexes.length > 0;

            m_nForm  = indexes.length == 1 ? IndexedProp : IndexedNProp;
            m_arg    = regArray;
            m_prop   = constProp;
            m_oIndex = indexes.length == 1 ? indexes[0] : indexes;
            }

        // ----- accessors ---------------------------------------------------------------------

        /**
         * @return the type of the L-Value
         */
        public TypeConstant getType()
            {
            switch (m_nForm)
                {
                case BlackHole:
                    return pool().typeObject();

                case LocalVar:
                    return getRegister().getType();

                case LocalProp:
                case TargetProp:
                case IndexedProp:
                case IndexedNProp:
                    return getProperty().getType();

                case Indexed:
                case IndexedN:
                    return getArray().getType();

                default:
                    throw new IllegalStateException();
                }
            }

        /**
         * Determine the type of assignability:
         * <ul>
         * <li>{@link #BlackHole} - a write-only register that anyone can assign to, resulting in
         *     the value being discarded</li>
         * <li>{@link #LocalVar} - a local variable of a method that can be assigned</li>
         * <li>{@link #LocalProp} - a local (this:private) property that can be assigned</li>
         * <li>{@link #TargetProp} - a property of a specified reference that can be assigned</li>
         * <li>{@link #Indexed} - an index into a single-dimensioned array</li>
         * <li>{@link #IndexedN} - an index into a multi-dimensioned array</li>
         * <li>{@link #IndexedProp} - an index into a single-dimensioned array property</li>
         * <li>{@link #IndexedNProp} - an index into a multi-dimensioned array property</li>
         * </ul>
         *
         * @return the form of the Assignable, one of: {@link #BlackHole}, {@link #LocalVar},
         *         {@link #LocalProp}, {@link #TargetProp}, {@link #Indexed}, {@link #IndexedN}
         */
        public int getForm()
            {
            return m_nForm;
            }

        /**
         * @return the register, iff this Assignable represents a local variable
         */
        public Register getRegister()
            {
            if (m_nForm != LocalVar)
                {
                throw new IllegalStateException();
                }
            return (Register) m_arg;
            }

        /**
         * @return true iff the lvalue is a register for a LocalVar, the property constant for a
         *         LocalProp, or the black-hole register for a BlackHole
         */
        public boolean isLocalArgument()
            {
            switch (m_nForm)
                {
                case BlackHole:
                case LocalVar:
                case LocalProp:
                    return true;

                default:
                    return false;
                }
            }

        /**
         * @return the register for a LocalVar, the property constant for a LocalProp, or the
         *         black-hole register for a BlackHole
         */
        public Argument getLocalArgument()
            {
            switch (m_nForm)
                {
                case BlackHole:
                    return new Register(pool().typeObject(), Op.A_IGNORE);

                case LocalVar:
                    return getRegister();

                case LocalProp:
                    return getProperty();

                default:
                    throw new IllegalStateException();
                }
            }

        /**
         * @return the property target, iff this Assignable represents a property
         */
        public Argument getTarget()
            {
            if (m_nForm != LocalProp && m_nForm != TargetProp)
                {
                throw new IllegalStateException();
                }
            return m_arg;
            }

        /**
         * @return the property, iff this Assignable represents a property
         */
        public PropertyConstant getProperty()
            {
            if (m_nForm != LocalProp && m_nForm != TargetProp)
                {
                throw new IllegalStateException();
                }
            return m_prop;
            }

        /**
         * @return the argument for the array, iff this Assignable represents an array
         */
        public Argument getArray()
            {
            if (m_nForm != Indexed && m_nForm != IndexedN)
                {
                throw new IllegalStateException();
                }
            return m_arg;
            }

        /**
         * @return the array index, iff this Assignable represents a 1-dimensional array
         */
        public Argument getIndex()
            {
            if (m_nForm == Indexed || m_nForm == IndexedProp)
                {
                return (Argument) m_oIndex;
                }

            throw new IllegalStateException();
            }

        /**
         * @return the array indexes, iff this Assignable represents an any-dimensional array
         */
        public Argument[] getIndexes()
            {
            if (m_nForm == Indexed || m_nForm == IndexedProp)
                {
                return new Argument[] {(Argument) m_oIndex};
                }

            if (m_nForm == IndexedN || m_nForm == IndexedNProp)
                {
                return (Argument[]) m_oIndex;
                }

            throw new IllegalStateException();
            }

        // ----- compilation -------------------------------------------------------------------

        /**
         * @return false iff the assignment of this LVal can<b>not</b> pull directly from a local
         *         property using the optimized (property constant only) encoding
         */
        public boolean supportsLocalPropMode()
            {
            // TODO
            return true;
            }

        /**
         * Generate the assignment-specific assembly code.
         *
         * @param arg   the Argument, representing the R-value
         * @param code  the code object to which the assembly is added
         * @param errs  the error listener to log to
         */
        public void assign(Argument arg, Code code, ErrorListener errs)
            {
            switch (m_nForm)
                {
                case BlackHole:
                    break;

                case LocalVar:
                    code.add(new Move(arg, getRegister()));
                    break;

                case LocalProp:
                    code.add(new L_Set(getProperty(), arg));
                    break;

                case TargetProp:
                    code.add(new P_Set(getProperty(), getTarget(), arg));
                    break;

                case Indexed:
                    code.add(new I_Set(getArray(), getIndex(), arg));
                    break;

                case IndexedN:
                    throw notImplemented();

                case IndexedProp:
                    code.add(new I_Set(getProperty(), getIndex(), arg));
                    break;

                case IndexedNProp:
                    throw notImplemented();

                default:
                    throw new IllegalStateException();
                }
            }

        // ----- fields ------------------------------------------------------------------------

        public static final byte BlackHole    = 0;
        public static final byte LocalVar     = 1;
        public static final byte LocalProp    = 2;
        public static final byte TargetProp   = 3;
        public static final byte Indexed      = 4;
        public static final byte IndexedN     = 5;
        public static final byte IndexedProp  = 6;
        public static final byte IndexedNProp = 7;

        private byte             m_nForm;
        private Argument         m_arg;
        private PropertyConstant m_prop;
        private Object           m_oIndex;
        }


    // ----- TypeFit enumeration -------------------------------------------------------------------


    /**
     * Represents the ability of an expression to yield a requested type:
     * <ul>
     * <li>{@code NoFit} - the expression can <b>not</b> yield the requested type;</li>
     * <li>{@code ConvPackUnpack} - the expression can yield the requested type via a combination of
     *     {@code @Auto} type conversion, tuple packing, and tuple unpacking;</li>
     * <li>{@code ConvPack} - the expression can yield the requested type via a combination of
     *     {@code @Auto} type conversion and tuple packing;</li>
     * <li>{@code ConvUnpack} - the expression can yield the requested type via a combination of
     *     {@code @Auto} type conversion and tuple unpacking;</li>
     * <li>{@code Conv} - the expression can yield the requested type via {@code @Auto} type
     *     conversion;</li>
     * <li>{@code PackUnpack} - the expression can yield the requested type via a combination of
     *     tuple packing and tuple unpacking;</li>
     * <li>{@code Pack} - the expression can yield the requested type via tuple packing;</li>
     * <li>{@code Unpack} - the expression can yield the requested type via tuple unpacking;</li>
     * <li>{@code Fit} - the expression can yield the requested type.</li>
     * </ul>
     */
    public enum TypeFit
        {
            NoFit(0b0000),
            ConvPackUnpack(0b1111),
            ConvPack(0b1011),
            ConvUnpack(0b0111),
            Conv(0b0011),
            PackUnpack(0b1101),
            Pack(0b0101),
            Unpack(0b1001),
            Fit(0b0001);

        /**
         * Constructor.
         *
         * @param nFlags  bit flags defining how good a fit the TypeFit is
         */
        TypeFit(int nFlags)
            {
            FLAGS = nFlags;
            }

        /**
         * @return true iff the type fits, regardless of whether it needs conversion or packing or
         *         unpacking
         */
        public boolean isFit()
            {
            return (FLAGS & FITS) != 0;
            }

        /**
         * @return a TypeFit that does everything this TypeFit does, plus fits
         */
        public TypeFit ensureFit()
            {
            return isFit()
                    ? this
                    : Fit;
            }

        /**
         * @return true iff the type goes through at least one "@Auto" conversion in order to fit
         */
        public boolean isConverting()
            {
            return (FLAGS & CONVERTS) != 0;
            }

        /**
         * @return a TypeFit that does everything this TypeFit does, plus type conversion
         */
        public TypeFit addConversion()
            {
            return isFit()
                    ? forFlags(FLAGS | CONVERTS)
                    : NoFit;
            }

        /**
         * @return a TypeFit that does everything this TypeFit does, minus type conversion
         */
        public TypeFit removeConversion()
            {
            return isConverting()
                    ? forFlags(FLAGS & ~CONVERTS)
                    : this;
            }

        /**
         * @return true iff the type goes through a tuple creation
         */
        public boolean isPacking()
            {
            return (FLAGS & PACKS) != 0;
            }

        /**
         * @return a TypeFit that does everything this TypeFit does, plus Tuple packing
         */
        public TypeFit addPack()
            {
            return isFit()
                    ? forFlags(FLAGS | PACKS)
                    : NoFit;
            }

        /**
         * @return a TypeFit that does everything this TypeFit does, minus Tuple packing
         */
        public TypeFit removePack()
            {
            return isPacking()
                    ? forFlags(FLAGS & ~PACKS)
                    : this;
            }

        /**
         * @return true iff the type goes through a tuple extraction
         */
        public boolean isUnpacking()
            {
            return (FLAGS & UNPACKS) != 0;
            }

        /**
         * @return a TypeFit that does everything this TypeFit does, plus Tuple unpacking
         */
        public TypeFit addUnpack()
            {
            return isFit()
                    ? forFlags(FLAGS | UNPACKS)
                    : NoFit;
            }

        /**
         * @return a TypeFit that does everything this TypeFit does, minus Tuple unpacking
         */
        public TypeFit removeUnpack()
            {
            return isConverting()
                    ? forFlags(FLAGS & ~UNPACKS)
                    : this;
            }

        /**
         * Produce a fit that combines this fit and that fit.
         *
         * @param that  the other fit
         *
         * @return a fit that combines all the attributes of this fit and that fit
         */
        public TypeFit combineWith(TypeFit that)
            {
            return forFlags(this.FLAGS | that.FLAGS);
            }

        /**
         * Determine which is the best fit, and return that best fit.
         *
         * @param that  the other fit
         *
         * @return whichever fit is considered better
         */
        public TypeFit betterOf(TypeFit that)
            {
            return this.ordinal() > that.ordinal() ? this : that;
            }

        /**
         * Determine if another fit is better than this fit.
         *
         * @param that  the other fit
         *
         * @return true iff the other fit is considered to be a better fit than this fit
         */
        public boolean betterThan(TypeFit that)
            {
            return this.ordinal() > that.ordinal();
            }

        /**
         * Determine if another fit is worse than this fit.
         *
         * @param that  the other fit
         *
         * @return true iff the other fit is considered to be a worse fit than this fit
         */
        public boolean worseThan(TypeFit that)
            {
            return this.ordinal() < that.ordinal();
            }

        /**
         * Look up a TypeFit enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the TypeFit enum for the specified ordinal
         */
        public static TypeFit valueOf(int i)
            {
            return BY_ORDINAL[i];
            }

        /**
         * Look up a TypeFit enum by its flags.
         *
         * @param nFlags  the flags
         *
         * @return the TypeFit enum for the specified ordinal
         */
        public static TypeFit forFlags(int nFlags)
            {
            if (nFlags >= 0 && nFlags <= BY_FLAGS.length)
                {
                TypeFit fit = BY_FLAGS[nFlags];
                if (fit != null)
                    {
                    return fit;
                    }
                }

            throw new IllegalStateException("no fit for flag value: " + nFlags);
            }

        /**
         * All of the TypeFit enums, by ordinal.
         */
        private static final TypeFit[] BY_ORDINAL = TypeFit.values();

        /**
         * All of the TypeFit enums, by flags.
         */
        private static final TypeFit[] BY_FLAGS = new TypeFit[0b10000];

        static
            {
            for (TypeFit fit : BY_ORDINAL)
                {
                BY_FLAGS[fit.FLAGS] = fit;
                }
            }

        public static final int FITS     = 0b0001;
        public static final int CONVERTS = 0b0010;
        public static final int PACKS    = 0b0100;
        public static final int UNPACKS  = 0b1000;

        /**
         * Represents the state of the TypeFit.
         */
        public final int FLAGS;
        }


    // ----- TuplePref enumeration -----------------------------------------------------------------


    /**
     * Represents the form that the Expression can or does yield a tuple:
     * <ul>
     * <li>{@code Rejected} - the expression must <b>not</b> yield the requested type(s) in a tuple
     *                        form</li>
     * <li>{@code Accepted} - the expression should yield a tuple if not yielding a tuple would
     *                        involve additional cost</li>
     * <li>{@code Desired}  - the expression should yield a tuple if it can do with no cost</li>
     * <li>{@code Required} - the expression must <b>always</b> yields a tuple of the requested
     *                        type(s)</li>
     * </ul>
     */
    // REVIEW could it simplify to: Never, Either, Always?
    public enum TuplePref
        {
            Rejected, Accepted, Desired, Required
        }


    // ----- fields --------------------------------------------------------------------------------

    public static final Assignable[] NO_LVALUES = new Assignable[0];
    public static final Argument[]   NO_RVALUES = new Argument[0];

    /**
     * After validation, contains the TypeFit determined during the validation.
     */
    private TypeFit m_fit;

    /**
     * After validation, contains the type(s) of the expression, stored as either a
     * {@code TypeConstant} or a {@code TypeConstant[]}.
     */
    private Object m_oType;

    /**
     * After validation, contains the constant value(s) of the expression, iff the expression is a
     * constant, stored as either a {@code Constant} or a {@code Constant[]}.
     */
    private Object m_oConst;

    /**
     * This allows a sub-class to not override either generateAssignment() method, by having a
     * relatively inefficient and/or non-effective implementation being provided by default (without
     * infinite recursion), or alternatively implementing one and/or the other of the two methods.
     */
    private transient boolean m_fInAssignment;

    /**
     * (Temporary) Infinite recursion prevention.
     */
    private transient byte m_cDepth;
    }

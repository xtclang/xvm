package org.xvm.compiler.ast;


import java.util.Arrays;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.ast.ExprAST;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.*;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

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
        extends AstNode {
    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected boolean usesSuper() {
        for (AstNode node : children()) {
            if (!(node instanceof ComponentStatement) && node.usesSuper()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Convert this expression into a TypeExpression.
     *
     * Note: if this node is already linked (i.e. has a parent) it has a responsibility of
     * connecting any newly created node to its parent.
     *
     * @return this expression, converted to a type expression
     */
    public TypeExpression toTypeExpression() {
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
    public boolean validateCondition(ErrorListener errs) {
        log(errs, Severity.ERROR, Compiler.ILLEGAL_CONDITIONAL);
        return false;
    }

    /**
     * @return this expression as a link-time conditional constant
     */
    public ConditionalConstant toConditionalConstant() {
        throw notImplemented();
    }

    /**
     * Mark this expression as "possibly" asymmetrical - returning conditional "False" on some branch.
     *
     * This method must be called *before* the validation or testFit.
     */
    public void markConditional() {
    }


    // ----- Expression compilation ----------------------------------------------------------------

    /**
     * @return true iff the expression implements the "single type" / "single value" code path
     */
    protected boolean hasSingleValueImpl() {
        return true;
    }

    /**
     * @return true iff the expression implements the "n type" / "n value" code path
     */
    protected boolean hasMultiValueImpl() {
        return false;
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
    public TypeConstant getImplicitType(Context ctx) {
        if (!hasMultiValueImpl()) {
            throw notImplemented();
        }

        TypeConstant[] aTypes = getImplicitTypes(ctx);
        return aTypes == null || aTypes.length == 0
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
    public TypeConstant[] getImplicitTypes(Context ctx) {
        if (!hasSingleValueImpl()) {
            throw notImplemented();
        }

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
     * @param fExhaustive   if false, the caller will tolerate a false negative and will re-validate
     *                      this expression anyway; otherwise an exhaustive test is expected, since
     *                      a negative response will not be re-checked and will most likely impact
     *                      the caller's processing
     * @param errs          (optional) the error listener to log to
     *
     * @return a TypeFit value describing the expression's capability (or lack thereof) to produce
     *         the required type
     */
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive, ErrorListener errs) {
        if (typeRequired == null) {
            // all expressions are required to be able to yield a void result
            return TypeFit.Fit;
        }

        if (hasSingleValueImpl()) {
            return calcFit(ctx, getImplicitType(ctx), typeRequired);
        }

        if (hasMultiValueImpl()) {
            return testFitMulti(ctx, typeRequired == null ? TypeConstant.NO_TYPES
                                                          : new TypeConstant[] {typeRequired}, fExhaustive, errs);
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
     * @param fExhaustive    if false, the caller will tolerate a false negative and will re-validate
     *                       this expression anyway; otherwise an exhaustive test is expected, since
     *                       a negative response will not be re-checked and will most likely impact
     *                       the caller's processing
     * @param errs           (optional) the error listener to log to
     *
     * @return a TypeFit value describing the expression's capability (or lack thereof) to produce
     *         the required type(s)
     */
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, boolean fExhaustive,
                                ErrorListener errs) {
        switch (atypeRequired.length) {
        case 0:
            // all expressions are required to be able to yield a void result
            return TypeFit.Fit;

        case 1:
            if (hasSingleValueImpl()) {
                return testFit(ctx, atypeRequired[0], fExhaustive, errs);
            }
            // fall through

        default:
            // anything that is expected to yield separate values must have a "multi"
            // implementation, so the lack of a "multi" implementation means that this
            // expression can't yield the desired number of values
            return hasMultiValueImpl()
                    ? calcFitMulti(ctx, getImplicitTypes(ctx), atypeRequired)
                    : TypeFit.NoFit;
        }
    }

    /**
     * An implementation of the "tesFit" API via the validation of the cloned expression.
     *
     * This implementation could be computationally expensive and should be used sparingly.
     */
    protected TypeFit testFitExhaustive(Context ctx, TypeConstant typeRequired, ErrorListener errs) {
        return testFitMultiExhaustive(ctx, new TypeConstant[] {typeRequired}, errs);
    }

    /**
     * An implementation of the "tesFit" API via the validation of the cloned expression.
     *
     * This implementation could be computationally expensive and should be used sparingly.
     */
    protected TypeFit testFitMultiExhaustive(Context ctx, TypeConstant[] atypeRequired,
                                             ErrorListener errs) {
        Expression exprTemp = (Expression) clone();
        Context    ctxTemp  = ctx.enter();
        Expression exprNew  = exprTemp.validateMulti(ctxTemp, atypeRequired,
                                        errs == null ? ErrorListener.BLACKHOLE : errs);
        exprTemp.discard(true);
        ctxTemp.discard();

        return exprNew == null ? TypeFit.NoFit : TypeFit.Fit;
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
    protected TypeFit calcFit(Context ctx, TypeConstant typeIn, TypeConstant typeOut) {
        // starting type is required
        if (typeIn == null) {
            return TypeFit.NoFit;
        }

        // there are two simple cases to consider:
        // 1) it is always a fit for an expression to go "to void"
        // 2) the most common / desired case is that the type-in is compatible with the type-out
        if (typeOut == null || isA(ctx, typeIn, typeOut)) {
            return TypeFit.Fit;
        }

        // check for the existence of an @Auto conversion
        if (typeIn.getConverterTo(typeOut) != null) {
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
    protected TypeFit calcFitMulti(Context ctx, TypeConstant[] atypeIn, TypeConstant[] atypeOut) {
        int cTypesIn  = atypeIn.length;
        int cTypesOut = atypeOut.length;
        if (cTypesIn < cTypesOut) {
            return TypeFit.NoFit;
        }

        TypeFit fitOut = TypeFit.Fit;
        if (cTypesOut == 1) {
            // check for a Tuple packing
            TypeConstant typeOut = atypeOut[0];
            if (typeOut.isTuple() && typeOut.getParamsCount() <= cTypesIn &&
                    (cTypesIn > 1 || !atypeIn[0].isTuple())) {
                atypeOut  = typeOut.getParamTypesArray();
                cTypesOut = atypeOut.length;
                fitOut    = TypeFit.Pack;
            }
        }

        for (int i = 0; i < cTypesOut; ++i) {
            TypeConstant typeIn    = atypeIn [i];
            TypeConstant typeOut   = atypeOut[i];
            TypeFit      fitSingle = calcFit(ctx, typeIn, typeOut);
            if (!fitOut.isFit()) {
                return TypeFit.NoFit;
            }

            fitOut = fitOut.combineWith(fitSingle);
        }

        return fitOut;
    }

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
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs) {
        if (!hasMultiValueImpl()) {
            throw notImplemented();
        }

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
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs) {
        int cTypesRequired = atypeRequired == null ? 0 : atypeRequired.length;
        if (cTypesRequired > 1) {
            if (!getParent().allowsConditional(this)) {
                return new UnpackExpression(this, null).validateMulti(ctx, atypeRequired, errs);
            }

            // the parent requires more than one type (conditionally), but this expression can only
            // return one; this can only be a "False" part of the conditional return; we'll check
            // afterward unless it's already caught by the validation logic
        }

        if (hasSingleValueImpl()) {
            Expression exprNew = validate(ctx, cTypesRequired == 0 ? null : atypeRequired[0], errs);

            if (cTypesRequired > 1 && exprNew != null &&
                    !exprNew.getType().equals(pool().typeFalse())) {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, cTypesRequired, 1);
            }
            return exprNew;
        }

        throw notImplemented();
    }

    /**
     * Convert this expression into a TypeExpression and determine if it can yield the specified
     * type.
     *
     * @see #testFit(Context, TypeConstant, boolean, ErrorListener)
     */
    protected TypeFit testFitAsType(Context ctx, TypeConstant typeRequired, boolean fExhaustive,
                                    ErrorListener errs) {
        TypeExpression exprType = toTypeExpression();
        return new StageMgr(exprType, Compiler.Stage.Validated, errs).fastForward(20)
                ? exprType.testFit(ctx, typeRequired, fExhaustive, ErrorListener.BLACKHOLE)
                : TypeFit.NoFit;
    }

    /**
     * Convert this expression into a TypeExpression and validate it.
     *
     * @see #validate(Context, TypeConstant, ErrorListener)
     */
    protected Expression validateAsType(Context ctx, TypeConstant typeRequired, ErrorListener errs) {
        TypeExpression exprType = toTypeExpression();

        if (new StageMgr(exprType, Compiler.Stage.Validated, ErrorListener.BLACKHOLE).fastForward(20)) {
            ErrorListener errsTemp = errs.branch(this);
            Expression    exprNew  = exprType.validate(ctx, typeRequired, errsTemp);
            if (exprNew != null) {
                errsTemp.merge();
                return exprNew;
            }
        }
        return null;
    }

    /**
     * Store the result of validating the Expression.
     *
     * @param ctx           the (optional) compiler context
     * @param typeRequired  the type that the expression must yield (optional)
     * @param typeActual    the type of the expression at this point (optional, in case of error)
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
            Context       ctx,
            TypeConstant  typeRequired,
            TypeConstant  typeActual,
            TypeFit       fit,
            Constant      constVal,
            ErrorListener errs) {
        assert fit != null;

        if (fit.isFit()) {
            checkShortCircuit(errs);
        }

        ConstantPool pool = pool();

        // a null actual type indicates a fairly dramatic (i.e. halt required) validation failure
        if (typeActual == null) {
            assert !fit.isFit();
            assert constVal == null;

            m_fit    = TypeFit.NoFit;
            m_oType  = typeRequired == null ? pool.typeObject() : typeRequired;
            m_oConst = null;

            return null;
        }

        // if there is a constant value, then the type itself indicates the immutable nature of the
        // expression
        if (constVal != null
                && !constVal.equals(pool.ensureMatchAnyConstant(typeActual))
                && !typeActual.isA(pool.typeService())) {
            typeActual = typeActual.freeze();
        }

        // if a required type is specified and the expression type isn't of the required type, then
        // an @Auto conversion can be used, assuming that we haven't already given up on the type
        // or already applied a type conversion; note that a SingletonConstant value indicates
        // a run-time constant that may not be computable at compile time
        MethodConstant idConv = null;
        if (typeRequired != null && fit.isFit() && !isA(ctx, typeActual, typeRequired)) {
            typeRequired = typeRequired.removeAutoNarrowing();

            // a conversion may be necessary to deliver the required type, but only one
            // conversion (per expression value) is allowed
            if (fit.isConverting() ||
                    (idConv = typeActual.ensureTypeInfo(errs).findConversion(typeRequired)) == null) {
                // cannot provide the required type
                reportTypeMismatch(typeRequired, typeActual, errs);
                fit = TypeFit.NoFit;
            } else if (constVal != null && !(constVal instanceof SingletonConstant)) {
                // an "out-of-range" error may be logged there, but we'll continue as a "fit"
                // nevertheless
                Constant constConv = convertConstant(constVal, typeRequired, errs);
                if (constConv == null) {
                    // there is no compile-time conversion available;
                    // continue with run-time conversion
                    // TODO: for now it's most likely our omission
                    log(errs, Severity.ERROR, Compiler.NOT_IMPLEMENTED,
                        "Constant conversion from \"" + typeActual.getValueString() +
                        "\" to \"" + typeRequired.getValueString() + '"');
                    fit = TypeFit.NoFit;
                } else {
                    typeActual = constConv.getType().freeze();
                    idConv     = null;
                    fit        = TypeFit.Conv;
                }
                constVal = constConv;
            }
        }

        if (typeActual.containsUnresolved()) {
            // if the type contains any PendingTypeConstant, resolve it down to its constraints
            typeActual = typeActual.resolveConstraints(true);

            // Note: there still remains a scenario, when "typeActual" contains unresolved types -
            //       specifically an annotated type with not-yet-resolved parameters;
            //       see the corresponding resolution logic in VariableDeclarationStatement#emit()
            //       and NewExpression#generateDynamicParameters()
        }

        m_fit    = fit;
        m_oType  = typeActual;
        m_oConst = constVal;

        if (!fit.isFit()) {
            return null;
        }

        // if we found an @Auto conversion, then create an expression that does the conversion work
        return idConv == null
                ? this
                : new ConvertExpression(this, new MethodConstant[]{idConv}, errs);
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
    protected TypeConstant inferTypeFromRequired(TypeConstant typeActual, TypeConstant typeRequired) {
        if (typeRequired.isNullable() && !typeActual.isNullable()) {
            // consider an example:
            //   class C(Set<Int>? ints) {...}
            //   ...
            //   C c = new C(new HashSet());
            //
            // it's clear that the despite the fact that the required type for the constructor
            // parameter is "Nullable | Set<Int>", the actual type should be HashSet<Int>
            typeRequired = typeRequired.removeNullable();
        }

        if (typeRequired.isParameterizedDeep() && !typeActual.equals(typeRequired)) {
            if (typeActual.isParamsSpecified()) {
                // there is a possibility that the type parameters could be in turn inferred, e.g.
                //   PropertyMapping<structType.DataType>[] fields = new PropertyMapping[];
                TypeConstant[] atypeRequired = typeRequired.getParamTypesArray();
                TypeConstant[] atypeActual   = typeActual.getParamTypesArray();
                int            cRequired     = atypeRequired.length;
                int            cActual       = atypeActual.length;
                TypeConstant[] atypeInferred = atypeActual;

                for (int i = 0, c = Math.min(cRequired, cActual); i < c; i++) {
                    TypeConstant typeInferred = inferTypeFromRequired(atypeActual[i], atypeRequired[i]);
                    if (typeInferred != null) {
                        if (atypeInferred == atypeActual) {
                            atypeInferred = atypeInferred.clone();
                        }
                        atypeInferred[i] = typeInferred;
                    }
                }
                return atypeInferred == atypeActual
                        ? null
                        : typeActual.adoptParameters(pool(), atypeInferred);
            } else {
                TypeConstant typeInferred = typeActual.adoptParameters(pool(), typeRequired);
                if (typeInferred.isA(typeRequired)) {
                    return typeInferred;
                }
            }
        }
        return null;
    }

    /**
     * Having a parameterizable class and a constructor on that class resolve the generic types
     * using the arguments expressions for the constructor's arguments.
     *
     * @param ctx       the compiler context
     * @param clz       the class structure
     * @param ctor      the constructor
     * @param listExpr  the list of argument expressions
     *
     * @return a resolved formal type or null if the resolution was unsuccessful
     */
    protected TypeConstant inferTypeFromConstructor(Context ctx, ClassStructure clz,
                                                    MethodStructure ctor, List<Expression> listExpr) {
        TypeConstant[] atypeParam = ctor.getParamTypes();
        int            cArgs      = listExpr.size();

        assert cArgs <= atypeParam.length;

        Map<String, TypeConstant> mapResolve = new HashMap<>();
        for (Map.Entry<StringConstant, TypeConstant> entry : clz.getTypeParamsAsList()) {
            String sName = entry.getKey().getValue();
            for (int i = 0; i < cArgs; i++) {
                TypeConstant typeActual = listExpr.get(i).getImplicitType(ctx);
                if (typeActual != null) {
                    TypeConstant typeResolved = atypeParam[i].
                            resolveTypeParameter(typeActual, sName);
                    if (typeResolved != null) {
                        // if the expression's type is an enum value, widen it to its parent
                        // Enumeration; for example, if the argument is Null, widen the type to
                        // Nullable, if the type is True widen it to Boolean, etc.
                        if (typeResolved.isEnumValue()) {
                            typeResolved = typeResolved.getSingleUnderlyingClass(false).
                                                getNamespace().getType();
                        }
                        mapResolve.put(sName, typeResolved);
                    }
                }
            }
        }

        return mapResolve.isEmpty()
            ? null
            : clz.getFormalType().resolveGenerics(pool(), mapResolve::get);
    }

    /**
     * Store the result of validating the Expression.
     *
     * Important note: the array of actual types comes from the actual signature and
     * must be cloned if it's to be changed.
     *
     * @param ctx            the (optional) compiler context
     * @param atypeRequired  the (optional) types required from the Expression (both the array and
     *                       any of its elements can be null)
     * @param atypeActual    the types that result from the Expression (neither the array nor its
     *                       elements can be null, except in the case of an error, in which the
     *                       array can be null)
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
            Context        ctx,
            TypeConstant[] atypeRequired,
            TypeConstant[] atypeActual,
            TypeFit        fit,
            Constant[]     aconstVal,
            ErrorListener  errs) {
        assert fit != null;
        assert atypeRequired == null || checkElementsNonNull(atypeRequired);
        assert atypeActual   == null || checkElementsNonNull(atypeActual);
        assert aconstVal     == null || checkElementsNonNull(aconstVal) && aconstVal.length == atypeActual.length;

        if (fit.isFit()) {
            checkShortCircuit(errs);
        }

        ConstantPool pool = pool();

        // a null actual type indicates a fairly dramatic (i.e. halt required) validation failure
        if (atypeActual == null) {
            assert !fit.isFit() && aconstVal == null && (errs.hasSeriousErrors() || errs.isSilent());
            m_fit = TypeFit.NoFit;
            return null;
        }

        int cActual   = atypeActual.length;
        int cTypeReqs = atypeRequired == null ? 0 : atypeRequired.length;
        if (cTypeReqs > cActual && fit.isFit()) {
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, cTypeReqs, cActual);
            m_fit = TypeFit.NoFit;
            return null;
        }

        boolean fCloneActual = true;

        // for expressions that yield constant values, make sure that the types reflect that
        for (int i = 0; i < cActual; ++i) {
            TypeConstant typeActual = atypeActual[i];
            if (typeActual.containsUnresolved()) {
                // see the comment in "finishValidation()"
                typeActual = typeActual.resolveConstraints(true);
            }

            Constant constVal = aconstVal == null ? null : aconstVal[i];
            if (constVal != null
                    && !constVal.equals(pool.ensureMatchAnyConstant(typeActual))
                    && !typeActual.isA(pool.typeService())) {
                TypeConstant typeImm = typeActual.freeze();

                if (!typeActual.equals(typeImm)) {
                    if (fCloneActual) {
                        atypeActual  = atypeActual.clone();
                        fCloneActual = false;
                    }
                    atypeActual[i] = typeImm;
                }
            }
        }

        MethodConstant[] aIdConv = null;
        if (cTypeReqs > 0 && fit.isFit()) {
            for (int i = 0, c = Math.min(cActual, cTypeReqs); i < c; ++i) {
                TypeConstant typeActual   = atypeActual[i];
                TypeConstant typeRequired = atypeRequired[i];
                if (isA(ctx, typeActual, typeRequired)) {
                    continue;
                }

                // look for an @Auto conversion, assuming that we haven't already applied it
                MethodConstant idConv;
                if (fit.isConverting() ||
                        (idConv = typeActual.ensureTypeInfo(errs).findConversion(typeRequired)) == null) {
                    // cannot provide the required type
                    reportTypeMismatch(typeRequired, typeActual, errs);
                    fit = TypeFit.NoFit;
                } else {
                    Constant constVal = aconstVal == null ? null : aconstVal[i];
                    if (constVal != null) {
                        Constant constConv = convertConstant(constVal, typeRequired, errs);
                        if (constConv == null) {
                            // there is no compile-time conversion available; continue with run-time
                            // conversion
                            // TODO GG: remove the soft assert below
                            System.err.println("No conversion found for " + constVal);
                        } else {
                            if (fCloneActual) {
                                atypeActual  = atypeActual.clone();
                                fCloneActual = false;
                            }
                            atypeActual[i] = constConv.getType().freeze();
                            idConv         = null;
                        }
                        aconstVal[i] = constConv;
                    }

                    if (idConv != null) {
                        if (aIdConv == null) {
                            aIdConv = new MethodConstant[cActual];
                        }
                        aIdConv[i] = idConv;
                    }
                }
            }
        }

        if (cTypeReqs > cActual && aconstVal != null) {
            // we've already reported an error for there not being enough values in the
            // expression to meet the required types, but since we're pretending to continue,
            // we might as well make up some constants to match the number of required types
            Constant[] aconstNew = new Constant[cTypeReqs];
            System.arraycopy(aconstVal, 0, aconstNew, 0, cActual);
            aconstVal = aconstNew;
            for (int i = cActual; i < cTypeReqs; ++i) {
                aconstVal[i] = generateFakeConstant(atypeRequired[i]);
            }
        }

        m_fit    = fit;
        m_oType  = fit.isFit() || atypeRequired == null ? atypeActual : null;
        m_oConst = aconstVal;

        if (!fit.isFit()) {
            return null;
        }

        // apply any conversions that we found previously to be necessary to deliver the required
        // data types
        return aIdConv == null
                ? this
                : new ConvertExpression(this, aIdConv, errs);
    }

    /**
     * @return true iff the "in" type *isA* the "out" type in the specified context
     */
    protected boolean isA(Context ctx, TypeConstant typeIn, TypeConstant typeOut) {
        if (typeIn.isA(typeOut)) {
            return true;
        }

        if (ctx == null) {
            return false;
        }

        TypeConstant typeInR  = ctx.resolveFormalType(typeIn);
        TypeConstant typeOutR = ctx.resolveFormalType(typeOut);

        return (typeInR != typeIn || typeOutR != typeOut) && typeInR.isA(typeOutR);
    }

    /**
     * @return true iff the "in" type is assignable to the "out" type in the specified context
     */
    protected boolean isAssignable(Context ctx, TypeConstant typeIn, TypeConstant typeOut) {
        if (typeIn.isAssignableTo(typeOut)) {
            return true;
        }

        if (ctx == null) {
            return false;
        }

        TypeConstant typeOutResolved = ctx.resolveFormalType(typeOut);
        return typeOutResolved != typeOut && typeIn.isAssignableTo(typeOutResolved);
    }

    /**
     * @return true iff the expression can stand alone as its own Statement
     */
    public boolean isStandalone() {
        return false;
    }

    /**
     * Called by a child Expression that wants to replace itself with a different Expression.
     *
     * @param that  an expression to use in lieu of this expression as a child of an AstNode
     *
     * @return the expression to use in place of this
     */
    protected Expression replaceThisWith(Expression that) {
        getParent().adopt(that);
        that.setStage(getStage());
        return that;
    }

    /**
     * @return true iff the Expression has been validated
     */
    public boolean isValidated() {
        return m_fit != null;
    }

    /**
     * Throw an exception if the Expression has not been validated
     */
    protected void checkValidated() {
        if (!isValidated()) {
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
    public int getValueCount() {
        checkValidated();

        return m_oType instanceof TypeConstant[] aTypes
                ? aTypes.length
                : 1;
    }

    /**
     * (Post-validation) Determine if the Expression represents no resulting value.
     *
     * @return true iff the Expression represents a "void" expression
     */
    public boolean isVoid() {
        return getValueCount() == 0;
    }

    /**
     * (Post-validation) Determine if the Expression represents exactly one value.
     *
     * @return true iff the Expression represents exactly one value
     */
    public boolean isSingle() {
        return getValueCount() == 1;
    }

    /**
     * (Post-validation) Determine if the expression represents a {@code conditional} result. A
     * conditional result is one in which there are multiple results, the first of which is a
     * boolean, and the remainder of which cannot be safely accessed if the runtime value of that
     * first boolean is {@code false}.
     * <p/>
     * This method must be overridden by any expression that represents or could represent a
     * conditional result, including as the result of composition of other expressions that could
     * represent a conditional result.
     *
     * @return true iff the Expression represents a conditional value
     */
    public boolean isConditionalResult() {
        return false;
    }

    /**
     * @return the TypeFit that was determined during validation
     */
    public TypeFit getTypeFit() {
        checkValidated();

        return m_fit;
    }

    /**
     * (Post-validation) Determine the type of the expression. For a multi-value expression, the
     * first TypeConstant is returned. For a void expression, the result is null.
     *
     * @return the type of the validated Expression, which is null for an Expression that yields a
     *         void result, otherwise the type of the <i>first</i> (and typically <i>only</i>) value
     *         resulting from the Expression
     */
    public TypeConstant getType() {
        checkValidated();

        if (m_oType instanceof TypeConstant type) {
            return type;
        }

        TypeConstant[] atype = (TypeConstant[]) m_oType;
        return atype.length == 0 ? null : atype[0];
    }

    /**
     * For debugging and error reporting, provide a non-null String representation of the type of
     * this expression which is tolerant of the stage of the compilation.
     *
     * @param ctx  an optional context
     *
     * @return a String that best describes the type of this expression
     */
    public String getTypeString(Context ctx) {
        if (m_oType instanceof TypeConstant type) {
            return type.getValueString();
        }

        if (m_oType instanceof TypeConstant[] aTypes) {
            return switch (aTypes.length) {
                case 0   -> "void";
                case 1  -> aTypes[0].getValueString();
                default -> aTypes[0].getValueString() + " (+" + (aTypes.length - 1) + " more)";
            };
        }

        if (ctx != null) {
            TypeConstant type = getImplicitType(ctx);
            if (type != null) {
                return type.getValueString();
            }
        }

        return "(unknown)";
    }

    /**
     * (Post-validation) Obtain an array of types, one for each value that this expression yields.
     * For a void expression, the result is a zero-length array.
     *
     * @return the types of the multiple values yielded by the expression; a zero-length array
     *         indicates a void type
     */
    public TypeConstant[] getTypes() {
        return m_oType instanceof TypeConstant[] aTypes
                ? aTypes
                : new TypeConstant[] {getType()};
    }

    /**
     * Obtain a {@link ExprAST binady expression expression} that represents this AST node and can
     * be used to serialize the AST.
     * <p/>
     * This method must not be called until after the expression has been validated.
     *
     * @return an "AST node" from the expression branch of the BinaryAST hierarchy of classes
     */
    public ExprAST getExprAST(Context ctx) {
        assert isValidated();
        throw new UnsupportedOperationException(
                "BAST for Expression: " + this.getClass().getSimpleName());
    }

    /**
     * Query the expression to determine if it would be a good candidate for tracing.
     *
     * @return true iff the expression claims that it is worthy to be a candidate for debug tracing
     */
    public boolean isTraceworthy() {
        assert isValidated();
        return false;
    }

    /**
     * Create a TraceExpression for this expression.
     *
     * @return a TraceExpression that has already inserted itself as the parent of this expression
     *         and the child of the previous parent
     */
    public TraceExpression requireTrace() {
        if (!isValidated() || !isTraceworthy()) {
            throw new IllegalStateException("expr=" + this);
        }

        AstNode         parent    = getParent();
        TraceExpression exprTrace = new TraceExpression(this);
        parent.replaceChild(this, exprTrace);
        this.setParent(exprTrace);
        return exprTrace;
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
    public boolean isAssignable(Context ctx) {
        return false;
    }

    /**
     * Check to make sure that the expression can be assigned to at this point in the validation
     * process, and log an error if it cannot.
     *
     * @param ctx   the validation context
     * @param errs  the error list to log to
     */
    public void requireAssignable(Context ctx, ErrorListener errs) {
        if (!isAssignable(ctx)) {
            log(errs, Severity.ERROR, Compiler.ASSIGNABLE_REQUIRED);
        }
    }

    /**
     * Mark the LValue as being assigned to at this point in the validation process.
     *
     * @param ctx    the validation context
     * @param fCond  true if the assignment is a conditional assignment (such that the l-value is
     *               definitely assigned if it is true)
     * @param errs   the error list to log to
     */
    public void markAssignment(Context ctx, boolean fCond, ErrorListener errs) {
    }

    /**
     * Test if this expression is used as an R-Value, which is something that yields a value.
     * <p/>
     * In most cases, an expression is used as an R-Value (i.e. it has a value), but an expression
     * can be used as a left side of an assignment, for example, which makes it an L-Value. In a
     * few cases, an expression can be used as both an R-Value and an L-Value, such as with the
     * pre-/post-increment/-decrement operators.
     *
\    * @return true iff this expression is used as an R-Value
     */
    protected boolean isRValue() {
        return getParent().isRValue(this);
    }

    /**
     * (Post-validation) Determine if the expression can short-circuit.
     * <p/>
     * This method must be overridden by any expression can short circuit, or any expression that
     * can short circuit as a result of containing another expression that may short-circuit.
     *
     * @return true iff the expression is capable of short-circuiting
     */
    public boolean isShortCircuiting() {
        return false;
    }

    @Override
    protected boolean allowsShortCircuit(AstNode nodeChild) {
        return getParent().allowsShortCircuit(this);
    }

    @Override
    protected Label ensureShortCircuitLabel(AstNode nodeOrigin, Context ctxOrigin) {
        // by default, the expression passes the request to its parent AST node
        return getParent().ensureShortCircuitLabel(nodeOrigin, ctxOrigin);
    }

    /**
     * @return true iff the expression represents a non-value ('?') used to explicitly indicate an
     *         unbound parameter
     */
    public boolean isNonBinding() {
        return false;
    }

    /**
     * (Post-validation) Determine if the expression has a constant value available for use by the
     * compiler. Some expressions may be constant, but may not be able to provide their value at
     * compile time, because code needs to be generated on behalf of the expression.
     *
     * @return true iff the expression results in a compile-time (ConstantPool) constant value
     */
    public boolean isConstant() {
        return m_oConst != null;
    }

    /**
     * (Post-validation) Determine if the expression should be treated as a constant value at
     * runtime. This allows an expression that does not have a compile-time constant value to be
     * used by the compiler to generate a runtime constant value, so that it can be used in a
     * language construct that requires a constant, but for a natural reason does not have the
     * ability to represent that value at compile time. For example, this could be useful for
     * switch cases of types that are known to be constant, but whose object values will not be
     * assemblable until runtime.
     *
     * @return true iff the Expression is a constant value that is representable by a constant in
     *         the ConstantPool or by a single constant value at runtime
     */
    public boolean isRuntimeConstant() {
        return isConstant();
    }

    /**
     * (Post-validation) Determine if the expression needs to generate code, even if it yields
     * a compile-time constant value.
     *
     * @return true iff the expression needs to produce code, regardless of whether it yields a
     *         compile-time constant value
     */
    public boolean hasSideEffects() {
        // generally, an expression that yields a compile-time constant value does not have
        // side effects; this must be overridden by any expression that violates this assumption
        return !isConstant();
    }

    /**
     * Determine if this expression can potentially affect (mutate the expected value of) a
     * preceding expression. In a sequence of expressions, such as a sequence of arguments for a
     * function call, the arguments must each be evaluated, but the behavior must be guaranteed to
     * occur "left to right", such that trailing (i.e. "to the right") expressions cannot cause the
     * value represented by any preceding (i.e. "to the left") expression to be modified. When a
     * possibility of mutation exists, the preceding expression must make a "point in time
     * snapshot".
     *
     * @param exprLeft the expression to assess the impact for
     * @param arg      the value of the specified expression
     *
     * @return one of the {@link SideEffect} values
     */
    protected SideEffect mightAffect(Expression exprLeft, Argument arg) {
        // constants cannot affect anything or be affected
        if (isConstant() || exprLeft.isConstant()) {
            return SideEffect.DefNo;
        }

        if (arg instanceof PropertyConstant) {
            // any computation (e.g. an invocation or a property access) may impact a property
            return SideEffect.AnyCompute;
        }

        return SideEffect.Unknown;
    }

    /**
     * (Post-validation) Determine if the expression can generate a compact variable initialization
     * (constant, sequence or tuple).
     *
     * This method should be overridden by any expression that can produce better code than the
     * default lvalue assignment code.
     *
     * @param lvalue  the lvalue declaration statement
     *
     * @return true iff the expression can generate a compact var initialization
     */
    public boolean supportsCompactInit(VariableDeclarationStatement lvalue) {
        return isConstant();
    }

    /**
     * (Post-validation) For an expression that provides a compile-time constant, indicated by the
     * {@link #isConstant()} method returning true, obtain a constant representation of the value.
     * <p/>
     * If the Expression has more than one value, then this will return the first constant value. If
     * the Expression is <i>void</i>, then this will return null.
     *
     * @return the compile-time constant value of the expression, or null if the expression is not
     *         constant
     */
    public Constant toConstant() {
        if (!isConstant()) {
            return null;
        }

        if (m_oConst instanceof Constant constant) {
            return constant;
        }

        return ((Constant[]) m_oConst)[0];
    }

    /**
     * (Post-validation) For an expression that provides compile-time constants, indicated by the
     * {@link #isConstant()} method returning true, obtain an array of constants that represent the
     * value of the Expression.
     * <p/>
     * If the Expression is <i>void</i>, then this will return an empty array.
     *
     * @return the compile-time constant values of the expression, or null if the expression is not
     *         constant
     */
    public Constant[] toConstants() {
        if (!isConstant()) {
            return null;
        }

        return m_oConst instanceof Constant[] aConst
                ? aConst
                : new Constant[] {toConstant()};
    }

    /**
     * Generate the necessary code that discards the value of this expression.
     * <p/>
     * This method should be overridden by any expression that can produce better code than the
     * default discarded-assignment code.
     *
     * @param ctx   the compilation context for the statement
     * @param code  the code block
     * @param errs  the error list to log any errors to
     */
    public void generateVoid(Context ctx, Code code, ErrorListener errs) {
        // a lack of side effects means that the expression can be ignored altogether
        if (hasSideEffects()) {
            if (isSingle() && hasSingleValueImpl()) {
                generateAssignment(ctx, code, new Assignable(), errs);
            } else {
                Assignable[] asnVoid = new Assignable[getValueCount()];
                Arrays.fill(asnVoid, new Assignable());
                generateAssignments(ctx, code, asnVoid, errs);
            }
        }
    }

    /**
     * Generate the necessary code that initializes an l-value variable.
     * <p/>
     * This method should be overridden by any expression that overrides
     * {@link #supportsCompactInit} method.
     *
     * @param ctx     the compilation context for the statement
     * @param code    the code block
     * @param lvalue  the variable declaration statement representing the lvalue
     * @param errs    the error list to log any errors to
     */
    public void generateCompactInit(
            Context ctx, Code code, VariableDeclarationStatement lvalue, ErrorListener errs) {
        assert supportsCompactInit(lvalue);

        StringConstant idName = pool().ensureStringConstant(lvalue.getName());
        code.add(new Var_IN(lvalue.getRegister(), idName, toConstant()));
    }

    /**
     * (Post-validation) Generate an argument that represents the result of this expression.
     * <p/>
     * If the expression {@link #hasSingleValueImpl()} is {@code true}, then this method or
     * {@link #generateAssignment} must be overridden.
     *
     * @param ctx           the compilation context for the statement
     * @param code          the code block
     * @param fLocalPropOk  true if the resulting arguments can be expressed as property constants
     *                      if the argument values are local properties
     * @param fUsedOnce     enables use of the "frame-local stack"
     * @param errs          the error list to log any errors to
     *
     * @return a resulting argument of the validated type
     */
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs) {
        assert !isVoid();

        if (isConstant()) {
            return toConstant();
        }

        if (hasMultiValueImpl() && (!hasSingleValueImpl() || !isSingle())) {
            return generateArguments(ctx, code, fLocalPropOk, fUsedOnce, errs)[0];
        }

        if (hasSingleValueImpl() && !isVoid()) {
            Assignable var = createTempVar(code, getType(), fUsedOnce);
            generateAssignment(ctx, code, var, errs);
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
     * @param ctx           the compilation context for the statement
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
    public Argument[] generateArguments(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs) {
        if (isConstant()) {
            return toConstants();
        }

        if (isVoid()) {
            // void means that the results of the expression are black-holed
            generateAssignments(ctx, code, NO_LVALUES, errs);
            return NO_RVALUES;
        }

        if (hasSingleValueImpl() && isSingle()) {
            // optimize for single argument case
            return new Argument[] { generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs) };
        }

        TypeConstant[] aTypes = getTypes();
        int            cTypes = aTypes.length;
        Assignable[]   aLVals = new Assignable[cTypes];
        aLVals[0] = createTempVar(code, aTypes[0], false);
        for (int i = 1; i < cTypes; ++i) {
            aLVals[i] = createTempVar(code, aTypes[i], false);
        }
        generateAssignments(ctx, code, aLVals, errs);

        // the temporaries are each represented by a register; return those registers as the
        // generated arguments
        Register[] aRegs = new Register[cTypes];
        for (int i = 0; i < cTypes; ++i) {
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
     * @param ctx   the compilation context for the statement
     * @param code  the code block
     * @param LVal  the Assignable object representing the L-Value
     * @param errs  the error list to log any errors to
     */
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs) {
        if (hasSingleValueImpl()) {
            // this will be overridden by classes that can push down the work
            Argument arg = generateArgument(ctx, code, LVal.supportsLocalPropMode(), true, errs);
            LVal.assign(arg, code, errs);
            return;
        }

        if (hasMultiValueImpl()) {
            generateAssignments(ctx, code, new Assignable[] {LVal}, errs);
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
     * @param ctx    the compilation context for the statement
     * @param code   the code block
     * @param aLVal  an array of Assignable objects representing the L-Values
     * @param errs   the error list to log any errors to
     */
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs) {
        int     cLVals = aLVal.length;
        int     cRVals = getValueCount();
        boolean fCond  = isConditionalResult();

        if (isCompletable()) {
            int cRValsActual = fCond ? cRVals + 1 : cRVals;
            if (cLVals > cRValsActual) {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, cLVals, cRValsActual);
                return;
            }
        }

        if (cLVals < cRVals) {
            // blackhole the missing LVals
            Assignable[] aLValNew = new Assignable[cRVals];
            Arrays.fill(aLValNew, new Assignable());
            System.arraycopy(aLVal, 0, aLValNew, 0, cLVals);
            aLVal  = aLValNew;
            cLVals = cRVals;
        }

        if (!isInAssignment()) {
            switch (cLVals) {
            case 2:
                if (!fCond || cRVals != 1) {
                    break;
                }
                // must be a conditional "false" - fall through
            case 1:
                if (hasSingleValueImpl()) {
                    markInAssignment();
                    generateAssignment(ctx, code, aLVal[0], errs);
                    clearInAssignment();
                    return;
                }
                break;

            case 0:
                markInAssignment();
                generateVoid(ctx, code, errs);
                clearInAssignment();
                return;
            }
        }

        if (hasMultiValueImpl()) {
            boolean fLocalPropOk = true;
            for (int i = 0; i < cLVals; ++i) {
                fLocalPropOk &= aLVal[i].supportsLocalPropMode();
            }

            boolean    fCheckArg0 = fCond && cLVals > 1;
            Argument[] aArg       = generateArguments(ctx, code, fLocalPropOk, !fCheckArg0, errs);

            if (fCheckArg0) {
                Argument argCond = aArg[0];
                assert !argCond.isStack();

                aLVal[0].assign(argCond, code, errs);

                Label label = new Label("skip_assign");
                code.add(new JumpFalse(argCond, label));
                for (int i = 1; i < cLVals; ++i) {
                    aLVal[i].assign(aArg[i], code, errs);
                }
                code.add(label);
            } else {
                for (int i = 0; i < cLVals; ++i) {
                    aLVal[i].assign(aArg[i], code, errs);
                }
            }
            return;
        }

        throw notImplemented();
    }

    /**
     * Generate the necessary code that jumps to the specified label if this expression evaluates
     * to the boolean value indicated in <tt>fWhenTrue</tt>.
     *
     * @param ctx        the compilation context for the statement
     * @param code       the code block
     * @param label      the label to conditionally jump to
     * @param fWhenTrue  indicates whether to jump when this expression evaluates to true, or
     *                   whether to jump when this expression evaluates to false
     * @param errs       the error list to log any errors to
     */
    public void generateConditionalJump(
            Context ctx, Code code, Label label, boolean fWhenTrue, ErrorListener errs) {
        assert !isVoid() && getType().isA(pool().typeBoolean());

        if (isConstant()) {
            if (fWhenTrue == toConstant().equals(pool().valTrue())) {
                code.add(new Jump(label));
            }
            return;
        }

        // this is just a generic implementation; sub-classes should override this to simplify the
        // generated code (e.g. by not having to always generate a separate boolean value)
        Argument arg = generateArgument(ctx, code, true, true, errs);
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
     * @return the Assignable representing the temporary variable; the Assignable will contain a
     *         Register
     */
    protected Assignable createTempVar(Code code, TypeConstant type, boolean fUsedOnce) {
        Register reg;
        if (fUsedOnce) {
            reg = new Register(type, null, Op.A_STACK);
        } else {
            reg = code.createRegister(type);
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
     * @param ctx   the compilation context for the statement
     * @param code  the code block
     * @param errs  the error list to log any errors to
     *
     * @return an Assignable object
     */
    public Assignable generateAssignable(Context ctx, Code code, ErrorListener errs) {
        if (!isAssignable(ctx) || isVoid()) {
            throw new IllegalStateException();
        }

        if (hasMultiValueImpl()) {
            return generateAssignables(ctx, code, errs)[0];
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
     * @param ctx   the compilation context for the statement
     * @param code  the code block
     * @param errs  the error list to log any errors to
     *
     * @return an array of {@link #getValueCount()} Assignable objects
     */
    public Assignable[] generateAssignables(Context ctx, Code code, ErrorListener errs) {
        if (isVoid()) {
            generateVoid(ctx, code, errs);
            return NO_LVALUES;
        }

        if (hasSingleValueImpl() && isSingle()) {
            return new Assignable[] { generateAssignable(ctx, code, errs) };
        }

        // a sub-class should have overridden this method
        assert isAssignable(ctx);
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
    public boolean isAssignableTo(TypeConstant typeThat) {
        TypeConstant typeImplicit = getType();
        return typeImplicit.isA(typeThat)
                || typeImplicit.ensureTypeInfo().findConversion(typeThat) != null;
    }

    /**
     * @return true iff the Expression is of the type "Boolean"
     */
    public boolean isTypeBoolean() {
        return getType().isEcstasy("Boolean");
    }

    /**
     * @return true iff the Expression is the constant value "false"
     */
    public boolean isConstantFalse() {
        return isConstant() && toConstant().equals(pool().valFalse());
    }

    /**
     * @return true iff the Expression is the constant value "false"
     */
    public boolean isConstantTrue() {
        return isConstant() && toConstant().equals(pool().valTrue());
    }

    /**
     * @return true iff the Expression is the constant value "Null"
     */
    public boolean isConstantNull() {
        return isConstant() && toConstant().equals(pool().valNull());
    }

    /**
     * Given a constant, attempt to convert it to the specified type.
     *
     * @param constIn  the constant
     * @param typeOut  the type that the constant must be assignable to
     *
     * @return the constant to use, or null if conversion is not possible
     */
    protected Constant convertConstant(Constant constIn, TypeConstant typeOut) {
        TypeConstant typeIn = constIn.getType();
        if (typeIn.isA(typeOut)) {
            // common case; no conversion is necessary
            return constIn;
        }

        try {
            return constIn.convertTo(typeOut);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /**
     * Given a constant, verify that it can be assigned to (or somehow converted to) the specified
     * type, and do so.
     *
     * @param constIn  the constant that needs to be validated as assignable
     * @param typeOut  the type that the constant must be assignable to
     * @param errs     the error list to log any errors to
     *
     * @return the constant to use or null if the compile-time conversion is not possible
     */
    protected Constant convertConstant(Constant constIn, TypeConstant typeOut, ErrorListener errs) {
        TypeConstant typeIn = constIn.getType();
        if (typeIn.isA(typeOut)) {
            // common case; no conversion is necessary
            return constIn;
        }

        try {
            return constIn.convertTo(typeOut);
        } catch (ArithmeticException e) {
            // conversion failure due to range etc.
            log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, typeOut,
                    constIn.getValueString());
            return generateFakeConstant(typeOut);
        }
    }

    /**
     * Given a constant, verify that it can be assigned to (or somehow converted to) the specified
     * type, and do so.
     *
     * @param constIn  the constant that needs to be validated as assignable
     * @param typeOut  the type that the constant must be assignable to
     * @param errs     the error list to log any errors to, for example if the constant cannot be
     *                 coerced in a manner to make it assignable
     *
     * @return the constant to use (never null)
     */
    protected Constant validateAndConvertConstant(Constant constIn, TypeConstant typeOut, ErrorListener errs) {
        Constant constOut = convertConstant(constIn, typeOut, errs);
        if (constOut == null) {
            // conversion apparently was not possible
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeOut, constIn.getType());
            constOut = generateFakeConstant(typeOut);
        }

        return constOut;
    }

    /**
     * Obtain a TypeInfo for the specified type in the specified class context.
     *
     * Note: unless this method returns a "private" access TypeInfo, the caller **must** always
     *       check teh visibility of properties or methods retrieved using that info.
     *
     * @param type  the type to get the TypeInfo for; if null - use the context's type
     */
    protected TypeInfo getTypeInfo(Context ctx, TypeConstant type, ErrorListener errs) {
        if (type == null) {
            return pool().
                ensureAccessTypeConstant(ctx.getThisType(), Access.PRIVATE).ensureTypeInfo(errs);
        }

        TypeInfo info = type.ensureTypeInfo(ctx.getThisClassId(), errs);
        if (info.getType().isAccessSpecified() || !type.isAccessModifiable()) {
            return info;
        }

        // The "ensureTypeInfo" didn't see any reason to widen the TypeInfo beyond the "public"
        // access, however:
        // a) there are some scenarios when "protected" access to some properties or
        //    methods should still be warranted (e.g. "left" is a subclass of "this");
        // b) even if the access is not allowed, it's much better to report such an
        //    error, rather than a generic "name XYZ is not found" message
        // Therefore, we will widen the search to "protected" access, but will enforce
        // the visibility check when a property or method is found.
        return type.ensureAccess(Access.PROTECTED).ensureTypeInfo(errs);
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
    protected Register generateBlackHole(TypeConstant type) {
        return new Register(type == null ? pool().typeObject() : type, null, Op.A_IGNORE);
    }

    /**
     * When an error occurs during compilation, but a constant of a specific type is required, this
     * method comes to the rescue.
     *
     * @param type  the type of the constant
     *
     * @return a constant of the specified type
     */
    protected Constant generateFakeConstant(TypeConstant type) {
        return Constant.defaultValue(type);
    }

    /**
     * @return true iff the current recursion is coming from assignment processing
     */
    protected boolean isInAssignment() {
        return (m_nFlags & IN_ASSIGNMENT) != 0;
    }

    /**
     * Mark the expression as being "in assignment".
     */
    protected void markInAssignment() {
        assert !isInAssignment();
        m_nFlags |= IN_ASSIGNMENT;
    }

    /**
     * Mark the expression as no longer being "in assignment".
     */
    protected void clearInAssignment() {
        assert isInAssignment();
        m_nFlags &= ~IN_ASSIGNMENT;
    }

    /**
     * @return true iff the "illegal short-circuiting" error has already been logged for this
     *        expression
     */
    protected boolean isSuppressShortCircuit() {
        return (m_nFlags & ILLEGAL_SHORT) != 0;
    }

    /**
     * If the expression is short-circuiting in a context that does NOT allow short-circuiting
     * expressions, then log an error (and subsequently return {@code true} from
     * {@link #isSuppressShortCircuit()}.)
     *
     * @param errs  the error list to log any errors to
     */
    protected void checkShortCircuit(ErrorListener errs) {
        if (!isSuppressShortCircuit() && isShortCircuiting()
                && !getParent().allowsShortCircuit(this)) {
            log(errs, Severity.ERROR, Compiler.SHORT_CIRCUIT_ILLEGAL);
            m_nFlags |= ILLEGAL_SHORT;  // only log the error once
        }
    }

    /**
     * A helper method to create an array of common types for two arrays.
     *
     * @param atypeThen  the first type array
     * @param atypeElse  the second type array
     * @return           an array of common types (of the minimum of the two array sizes)
     */
    protected TypeConstant[] selectCommonTypes(TypeConstant[] atypeThen, TypeConstant[] atypeElse) {
        int            cTypes      = Math.min(atypeThen.length, atypeElse.length);
        TypeConstant[] atypeCommon = new TypeConstant[cTypes];
        for (int i = 0; i < cTypes; i++) {
            TypeConstant typeThen = atypeThen[i];
            TypeConstant typeElse = atypeElse[i];

            ConstantPool pool       = pool();
            TypeConstant typeCommon = Op.selectCommonType(typeThen, typeElse, ErrorListener.BLACKHOLE);
            atypeCommon[i] = typeCommon == null && typeThen != null && typeElse != null
                    ? typeThen.isOnlyNullable() ? pool.ensureNullableTypeConstant(typeElse)
                    : typeElse.isOnlyNullable() ? pool.ensureNullableTypeConstant(typeThen)
                        : pool.ensureUnionTypeConstant(typeThen, typeElse)
                        : typeCommon;
        }
        return atypeCommon;
    }

    /**
     * Given that this expression sits in a list of expressions at the specified index, determine if
     * any of the expressions "on the right" of it have a potential of changing this expression's
     * value as a side effect and if so, replace the specified argument with a "point-in-time" value.
     *
     * @param arg       the argument representing this expression's value
     * @param listExprs the lists of expressions
     * @param iExpr     the index of this expression in the list
     *
     * @return a "point-in-time" value, if necessary
     */
    protected Argument ensurePointInTime(Code code, Argument arg, List<Expression> listExprs, int iExpr) {
        // clearly, the last expression in a list cannot have any side effects, as well as an
        // effectively final argument
        int cExprs = listExprs.size();
        if (iExpr < cExprs-1 && !arg.isEffectivelyFinal()) {
            for (int iRight = iExpr + 1; iRight < cExprs; ++iRight) {
                if (listExprs.get(iRight).mightAffect(this, arg) == SideEffect.DefYes) {
                    return ensurePointInTime(code, arg);
                }
            }
        }
        return arg;
    }

    /**
     * Check it the specified expression has a potential of changing this expression's value (as a
     * side effect) and if so, replace the specified argument with a "point-in-time" value.
     *
     * @param arg        the argument representing this expression's value
     * @param exprRight  the expression to check for side effect on this expression
     *
     * @return a "point-in-time" value, if necessary
     */
    protected Argument ensurePointInTime(Code code, Argument arg, Expression exprRight) {
        return !arg.isEffectivelyFinal() && exprRight.mightAffect(this, arg) == SideEffect.DefYes
                ? ensurePointInTime(code, arg)
                : arg;
    }

     /**
     * Check if the specified argument's value can be changed externally and replace it with a
     * "point-in-time" value if necessary.
     */
    private Argument ensurePointInTime(Code code, Argument arg) {
        // if the register is a @Future, no need to take a snapshot
        if (arg instanceof PropertyConstant ||
            arg instanceof Register reg &&
                (!reg.isVar() || !reg.ensureRegType(true).isA(pool().clzFuture().getType()))) {
            Register regTemp = code.createRegister(arg.getType());
            regTemp.markEffectivelyFinal();
            code.add(new Var_I(regTemp, arg));
            arg = regTemp;
        }
        return arg;
    }

    /**
     * Report a type mismatch, attempting to provide a most useful error.
     */
    protected void reportTypeMismatch(TypeConstant typeRequired, TypeConstant typeActual,
                                      ErrorListener errs) {
        if (this instanceof LiteralExpression lit &&
                typeRequired.isA(pool().typeFileNode()) &&
                lit.getLiteral().getId() == Token.Id.LIT_PATH) {
            log(errs, Severity.ERROR, Compiler.MISSING_RESOURCE);
        } else {
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                typeRequired.getValueString(), typeActual.getValueString());
        }
    }


    // ----- inner class: Assignable ---------------------------------------------------------------

    /**
     * Assignable represents an L-Value.
     */
    public class Assignable {
        // ----- constructors ------------------------------------------------------------------

        /**
         * Construct a black hole L-Value.
         */
        public Assignable() {
            m_form = AssignForm.BlackHole;
        }

        /**
         * Construct an Assignable based on a local variable.
         *
         * @param regVar  the Register, representing the local variable
         */
        public Assignable(Register regVar) {
            m_form = AssignForm.LocalVar;
            m_arg  = regVar;
        }

        /**
         * Construct an Assignable based on a property (either local or "this").
         *
         * @param argTarget  the argument representing the property target
         * @param constProp  the PropertyConstant
         */
        public Assignable(Argument argTarget, PropertyConstant constProp) {
            if (argTarget instanceof Register reg) {
                if (reg.isPredefined()) {
                    switch (reg.getIndex()) {
                    case Op.A_THIS:
                    case Op.A_TARGET:
                    case Op.A_STRUCT:
                        m_form = AssignForm.LocalProp;
                        break;

                    default:
                        m_form = AssignForm.TargetProp;
                        break;
                    }
                } else {
                    m_form = AssignForm.TargetProp;
                }
            } else {
                m_form = AssignForm.TargetProp;
            }
            m_arg  = argTarget;
            m_prop = constProp;
        }

        /**
         * Construct an Assignable based on a single dimension array local variable.
         *
         * @param argArray  the Register, representing the local variable holding an array
         * @param index     the index into the array
         */
        public Assignable(Argument argArray, Argument index) {
            m_form   = AssignForm.Indexed;
            m_arg    = argArray;
            m_oIndex = index;
        }

        /**
         * Construct an Assignable based on a multi (any) dimension array local variable.
         *
         * @param regArray  the Register, representing the local variable holding an array
         * @param indexes   an array of indexes into the array
         */
        public Assignable(Argument regArray, Argument[] indexes) {
            assert indexes != null && indexes.length > 0;

            m_form   = indexes.length == 1 ? AssignForm.Indexed : AssignForm.IndexedN;
            m_arg    = regArray;
            m_oIndex = indexes.length == 1 ? indexes[0] : indexes;
        }

        /**
         * Construct an Assignable based on a local property that is a single dimension array.
         *
         * @param constProp  the PropertyConstant
         * @param index      the index into the array
         */
        public Assignable(PropertyConstant constProp, Argument index) {
            m_form   = AssignForm.IndexedProp;
            m_prop   = constProp;
            m_oIndex = index;
        }

        /**
         * Construct an Assignable based on a local property that is a multi (any) dimension array.
         *
         * @param constProp  the PropertyConstant
         * @param indexes    an array of indexes into the array
         */
        public Assignable(PropertyConstant constProp, Argument[] indexes) {
            assert indexes != null && indexes.length > 0;

            m_form   = indexes.length == 1 ? AssignForm.IndexedProp : AssignForm.IndexedNProp;
            m_prop   = constProp;
            m_oIndex = indexes.length == 1 ? indexes[0] : indexes;
        }

        // ----- accessors ---------------------------------------------------------------------

        /**
         * @return the type of the L-Value
         */
        public TypeConstant getType() {
            return switch (m_form) {
                case BlackHole ->
                    pool().typeObject();

                case LocalVar ->
                    getRegister().getType();

                case LocalProp, TargetProp ->
                    getProperty().getType();

                case Indexed, IndexedN ->
                    getArray().getType();

                case IndexedProp, IndexedNProp ->
                    // TODO
                    throw notImplemented();
            };
        }

        /**
         * @return the form of the Assignable
         */
        public AssignForm getForm() {
            return m_form;
        }

        /**
         * @return true iff this Assignable represents a "black hole"
         */
        public boolean isBlackhole() {
            return m_form == AssignForm.BlackHole;
        }

        /**
         * @return the register, iff this Assignable represents a local variable
         */
        public Register getRegister() {
            if (m_form != AssignForm.LocalVar) {
                throw new IllegalStateException();
            }
            return (Register) m_arg;
        }

        /**
         * @return true iff the lvalue is a local variable register that is "normal" (but not the
         *         stack)
         */
        public boolean isNormalVariable() {
            return m_form == AssignForm.LocalVar && ((Register) m_arg).isNormal();
        }

        /**
         * @return true iff the lvalue is a local variable register that is on the stack
         */
        public boolean isStack() {
            return m_form == AssignForm.LocalVar && m_arg.isStack();
        }

        /**
         * @return true iff the lvalue is a register for a LocalVar, the property constant for a
         *         LocalProp, or the black-hole register for a BlackHole
         */
        public boolean isLocalArgument() {
            return switch (m_form) {
                case BlackHole, LocalVar, LocalProp -> true;
                default                             -> false;
            };
        }

        /**
         * @return true iff the lvalue is a property
         */
        public boolean isProperty() {
            return switch (m_form) {
                case LocalProp, TargetProp -> true;
                default                    -> false;
            };
        }

        /**
         * @return the register for a LocalVar, the property constant for a LocalProp, or the
         *         black-hole register for a BlackHole
         */
        public Argument getLocalArgument() {
            return switch (m_form) {
                case BlackHole -> generateBlackHole(null);
                case LocalVar  -> getRegister();
                case LocalProp -> getProperty();
                default        -> throw new IllegalStateException();
            };
        }

        /**
         * @return the property target, iff this Assignable represents a property
         */
        public Argument getTarget() {
            if (m_form != AssignForm.LocalProp && m_form != AssignForm.TargetProp) {
                throw new IllegalStateException();
            }
            return m_arg;
        }

        /**
         * @return the property, iff this Assignable represents a property
         */
        public PropertyConstant getProperty() {
            if (m_form != AssignForm.LocalProp && m_form != AssignForm.TargetProp &&
                m_form != AssignForm.IndexedProp && m_form != AssignForm.IndexedNProp) {
                throw new IllegalStateException();
            }
            return m_prop;
        }

        /**
         * @return the argument for the array, iff this Assignable represents an array
         */
        public Argument getArray() {
            if (m_form != AssignForm.Indexed && m_form != AssignForm.IndexedN) {
                throw new IllegalStateException();
            }
            return m_arg;
        }

        /**
         * @return the array index, iff this Assignable represents a 1-dimensional array
         */
        public Argument getIndex() {
            if (m_form == AssignForm.Indexed || m_form == AssignForm.IndexedProp) {
                return (Argument) m_oIndex;
            }

            throw new IllegalStateException();
        }

        /**
         * @return the array indexes, iff this Assignable represents an any-dimensional array
         */
        public Argument[] getIndexes() {
            if (m_form == AssignForm.Indexed || m_form == AssignForm.IndexedProp) {
                return new Argument[] {(Argument) m_oIndex};
            }

            if (m_form == AssignForm.IndexedN || m_form == AssignForm.IndexedNProp) {
                return (Argument[]) m_oIndex;
            }

            throw new IllegalStateException();
        }

        // ----- compilation -------------------------------------------------------------------

        /**
         * @return false iff the assignment of this LVal can<b>not</b> pull directly from a local
         *         property using the optimized (property constant only) encoding
         */
        public boolean supportsLocalPropMode() {
            // TODO
            return true;
        }

        /**
         * Generate an RValue argument that represents the value that is in the LValue. The RValue
         * may be the same as the LValue, for example when the LValue is a local variable, or it may
         * be a copy.
         *
         * @param LValResult    the L-value to store the result in, or null
         * @param fLocalPropOk  if no L-value is provided, then this is used to indicate whether the
         *                      resulting argument can be a property constant indicating a local
         *                      property value
         * @param fUsedOnce     if no L-value is provided, then this is used to indicate whether the
         *                      result is used only once (e.g. could the temporary stack be used for
         *                      the result)
         * @param code          the code object to which the assembly is added
         * @param errs          the error listener to log to
         *
         * @return an argument, if an Assignable was not provided
         */
        public Argument getValue(Assignable LValResult, boolean fLocalPropOk, boolean fUsedOnce,
                Code code, ErrorListener errs) {
            switch (m_form) {
            case BlackHole:
                // blackhole has no value
                throw new IllegalStateException();

            case LocalVar:
                if (LValResult == null) {
                    return getRegister();
                } else {
                    LValResult.assign(getRegister(), code, errs);
                    return null;
                }

            case LocalProp:
                if (LValResult == null && fLocalPropOk) {
                    return getProperty();
                }
                // fall through
            case TargetProp: {
                Assignable LValTemp = LValResult == null || !LValResult.isLocalArgument()
                        ? createTempVar(code, getType(), fUsedOnce)
                        : LValResult;
                code.add(new P_Get(getProperty(), getTarget(), LValTemp.getLocalArgument()));
                if (LValResult == null) {
                    return LValTemp.getLocalArgument();
                } else {
                    if (LValResult != LValTemp) {
                        LValResult.assign(LValTemp.getLocalArgument(), code, errs);
                    }
                    return null;
                }
            }

            case Indexed:
            case IndexedProp: {
                Assignable LValTemp = LValResult == null || !LValResult.isLocalArgument()
                        ? createTempVar(code, getType(), fUsedOnce)
                        : LValResult;
                Argument argTarget = m_form == AssignForm.Indexed
                        ? getArray()
                        : getProperty();
                code.add(new I_Get(argTarget, getIndex(), LValTemp.getLocalArgument()));
                if (LValResult == null) {
                    return LValTemp.getLocalArgument();
                } else {
                    if (LValResult != LValTemp) {
                        LValResult.assign(LValTemp.getLocalArgument(), code, errs);
                    }
                    return null;
                }
            }

            case IndexedN:
            case IndexedNProp:
                // TODO
                throw notImplemented();

            default:
                throw new IllegalStateException();
            }
        }

        /**
         * Generate an argument that represents the result of this LValue. This method exists to
         * support language constructs that require an LValue to provide a value, such as the
         * bi-expressions for the "&&=", "||=", and "?:=" operators. The primary difference between
         * this method and the expression's own generateArgument() method is that using this method
         * prevents a duplicate side-effect of generating the L-Value; for example, the side-effect
         * of the post-increment in the following statement must only occur one time:
         * <code><pre>
         * a[i++] &&= foo();
         * </pre></code>
         *
         * @param ctx           the compilation context for the statement
         * @param code          the code block
         * @param fLocalPropOk  true if the resulting arguments can be expressed as property
         *                      constants if the argument values are local properties
         * @param fUsedOnce     enables use of the "frame-local stack"
         * @param errs          the error list to log any errors to
         *
         * @return a resulting argument of the validated type
         */
        public Argument generateArgument(
                Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs) {
            switch (m_form) {
            case LocalVar:
                return getRegister();

            case LocalProp: {
                if (fLocalPropOk) {
                    return getProperty();
                }

                if (fUsedOnce) {
                    Register reg = new Register(getType(), null, Op.A_STACK);
                    code.add(new L_Get(getProperty(), reg));
                    return reg;
                }

                Register reg = code.createRegister(getType());
                code.add(new Var_I(reg, getProperty()));
                return reg;
            }

            case TargetProp: {
                Register reg;
                if (fUsedOnce) {
                    reg = new Register(getType(), null, Op.A_STACK);
                } else {
                    reg = code.createRegister(getType());
                    code.add(new Var(reg));
                }
                code.add(new P_Get(getProperty(), getTarget(), reg));
                return reg;
            }

            case Indexed: {
                Register reg;
                if (fUsedOnce) {
                    reg = new Register(getType(), null, Op.A_STACK);
                } else {
                    reg = code.createRegister(getType());
                    code.add(new Var(reg));
                }
                code.add(new I_Get(getArray(), getIndex(), reg));
                return reg;
            }

            case IndexedN:
                // TODO
            case IndexedProp:       // REVIEW - is this even legal? (side effects)
            case IndexedNProp:      // REVIEW - is this even legal? (side effects)
                throw notImplemented();

            case BlackHole:
            default:
                throw new IllegalStateException("form=" + m_form);
            }
        }

        /**
         * Generate the assignment-specific assembly code.
         *
         * @param arg   the Argument, representing the R-value
         * @param code  the code object to which the assembly is added
         * @param errs  the error listener to log to
         */
        public void assign(Argument arg, Code code, ErrorListener errs) {
            switch (m_form) {
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

            case IndexedProp:
                code.add(new I_Set(getProperty(), getIndex(), arg));
                break;

            case IndexedN:
            case IndexedNProp:
                // TODO
                throw notImplemented();

            default:
                throw new IllegalStateException();
            }
        }

        /**
         * Generate the sequential operation assembly code. This method is basically a combination
         * of generationVoid(), generateArgument(), and generateAssignment(), for blind-, pre-, and
         * post-, and for both -increment, and -decrement.
         *
         * @param seq         the type of sequential operation
         * @param LValResult  the L-value to store the result in, or null
         * @param fUsedOnce   if no L-value is provided, and the expression has a resulting
         *                    argument, then this is used to indicate whether the result is used
         *                    only once (e.g. could the temporary stack be used for the result)
         * @param code        the code object to which the assembly is added
         * @param errs        the error listener to log to
         *
         * @return an argument, if the specified operation produces a value and an Assignable was
         *         not passed
         */
        public Argument assignSequential(Sequential seq, Assignable LValResult, boolean fUsedOnce,
                Code code, ErrorListener errs) {
            // a blind operation cannot have a result; all other operations will either assign to
            // the result or return a result
            assert !seq.isBlind() || LValResult == null;

            // black-hole optimization
            if (LValResult != null && LValResult.isBlackhole()) {
                seq = seq.toBlind();
                LValResult = null;
            }

            switch (m_form) {
            case LocalVar:
            case LocalProp: {
                Argument argTarget = getLocalArgument();
                switch (seq) {
                case Inc:
                    code.add(new IP_Inc(argTarget));
                    return null;

                case Dec:
                    code.add(new IP_Dec(argTarget));
                    return null;

                case PreInc:
                case PreDec: {
                    if (LValResult != null && LValResult.isLocalArgument()) {
                        code.add(seq.isInc()
                                ? new IP_PreInc(argTarget, LValResult.getLocalArgument())
                                : new IP_PreDec(argTarget, LValResult.getLocalArgument()));
                        return null;
                    }

                    Register regResult = code.createRegister(argTarget.getType(), fUsedOnce);
                    code.add(seq.isInc()
                            ? new IP_PreInc(argTarget, regResult)
                            : new IP_PreDec(argTarget, regResult));

                    if (LValResult == null) {
                        return regResult;
                    }
                    LValResult.assign(regResult, code, errs);
                    return null;
                }

                case PostInc:
                case PostDec: {
                    Assignable LValTemp = LValResult != null && LValResult.isLocalArgument()
                            ? LValResult
                            : createTempVar(code, getType(), fUsedOnce);
                    code.add(seq.isInc()
                            ? new IP_PostInc(argTarget, LValTemp.getLocalArgument())
                            : new IP_PostDec(argTarget, LValTemp.getLocalArgument()));
                    if (LValResult == null) {
                        return LValTemp.getRegister();
                    }

                    if (LValResult != LValTemp) {
                        LValResult.assign(LValTemp.getRegister(), code, errs);
                    }
                    return null;
                }
                }
                break;
            }

            case TargetProp: {
                PropertyConstant prop      = getProperty();
                Argument         argTarget = getTarget();
                if (seq.isBlind()) {
                    code.add(seq.isInc()
                            ? new PIP_Inc(prop, argTarget)
                            : new PIP_Dec(prop, argTarget));
                } else {
                    Assignable LValTemp = LValResult != null && LValResult.isLocalArgument()
                            ? LValResult
                            : createTempVar(code, getType(), fUsedOnce);

                    Argument argReturn = LValTemp.getLocalArgument();
                    code.add(seq.isPre()
                            ? seq.isInc()
                                ? new PIP_PreInc(prop, argTarget, argReturn)
                                : new PIP_PreDec(prop, argTarget, argReturn)
                            : seq.isInc()
                                ? new PIP_PostInc(prop, argTarget, argReturn)
                                : new PIP_PostDec(prop, argTarget, argReturn));
                    if (LValResult == null) {
                        return argReturn;
                    }

                    if (LValResult != LValTemp) {
                        LValResult.assign(argReturn, code, errs);
                    }
                }
                return null;
            }

            case Indexed:
            case IndexedProp: {
                Argument argArray = m_form == AssignForm.Indexed
                        ? getArray()
                        : getProperty();
                Argument argIndex = getIndex();
                if (seq.isBlind()) {
                    code.add(seq.isInc()
                            ? new IIP_Inc(argArray, argIndex)
                            : new IIP_Dec(argArray, argIndex));
                } else {
                    Assignable LValTemp = LValResult != null && LValResult.isLocalArgument()
                            ? LValResult
                            : createTempVar(code, getType().resolveGenericType("Element"), fUsedOnce);

                    Argument argReturn = LValTemp.getLocalArgument();
                    code.add(seq.isPre()
                            ? seq.isInc()
                                ? new IIP_PreInc(argArray, argIndex, argReturn)
                                : new IIP_PreDec(argArray, argIndex, argReturn)
                            : seq.isInc()
                                ? new IIP_PostInc(argArray, argIndex, argReturn)
                                : new IIP_PostDec(argArray, argIndex, argReturn));
                    if (LValResult == null) {
                        return argReturn;
                    }

                    if (LValResult != LValTemp) {
                        LValResult.assign(argReturn, code, errs);
                    }
                }
                return null;
            }

            case IndexedN:
            case IndexedNProp:
                // TODO
                throw notImplemented();

            default:
                throw new IllegalStateException();
            }

            // generic implementation
            switch (seq) {
            case Inc:
            case Dec: {
                Assignable LValTemp = createTempVar(code, getType(), false);

                // get the original value
                getValue(LValTemp, false, false, code, errs);

                // perform the sequential operation on the temp
                LValTemp.assignSequential(seq, null, false, code, errs);

                // store the operation's result
                assign(LValTemp.getRegister(), code, errs);

                return null;
            }

            case PreInc:
            case PreDec: {
                Assignable LValTemp = LValResult != null && LValResult.isNormalVariable()
                        ? LValResult
                        : createTempVar(code, getType(), false);

                // get the original value
                getValue(LValTemp, false, false, code, errs);

                // perform the sequential operation on the temp
                Sequential seqVoid = seq.isInc() ? Sequential.Inc : Sequential.Dec;
                LValTemp.assignSequential(seqVoid, null, false, code, errs);

                // store the operation's result
                assign(LValTemp.getRegister(), code, errs);

                // return the operation's result
                if (LValResult == null) {
                    return LValTemp.getRegister();
                }

                if (LValResult != LValTemp) {
                    LValResult.assign(LValTemp.getRegister(), code, errs);
                }
                return null;
            }

            case PostInc:
            case PostDec: {
                Assignable LValTemp  = createTempVar(code, getType(), false);
                Argument   argResult = null;
                if (LValResult == null) {
                    LValResult = createTempVar(code, getType(), fUsedOnce);
                    argResult  = LValResult.getRegister();
                }

                // get the original value
                getValue(LValTemp, false, false, code, errs);
                LValResult.assign(LValTemp.getRegister(), code, errs);

                // perform the sequential operation on the temp
                Sequential seqVoid = seq.isInc() ? Sequential.Inc : Sequential.Dec;
                LValTemp.assignSequential(seqVoid, null, false, code, errs);

                // store the operation's result
                assign(LValTemp.getRegister(), code, errs);

                // return the value that preceded the operation
                return argResult;
            }

            default:
                throw new IllegalStateException();
            }
        }

        /**
         * Generate the assignment-specific assembly code for the specified "in place" operator,
         * such as "+=" or "*=".
         *
         * @param tokOp  the "in place" operator
         * @param arg    the Argument, representing the R-value
         * @param code   the code object to which the assembly is added
         * @param errs   the error listener to log to
         */
        public void assignInPlaceResult(Token tokOp, Argument arg, Code code, ErrorListener errs) {
            Op op;
            switch (m_form) {
            case LocalVar:
            case LocalProp: {
                Argument argTarget = getLocalArgument();
                op = switch (tokOp.getId()) {
                    case ADD_ASN     -> new IP_Add(argTarget, arg);
                    case SUB_ASN     -> new IP_Sub(argTarget, arg);
                    case MUL_ASN     -> new IP_Mul(argTarget, arg);
                    case DIV_ASN     -> new IP_Div(argTarget, arg);
                    case MOD_ASN     -> new IP_Mod(argTarget, arg);
                    case SHL_ASN     -> new IP_Shl(argTarget, arg);
                    case SHR_ASN     -> new IP_Shr(argTarget, arg);
                    case USHR_ASN    -> new IP_ShrAll(argTarget, arg);
                    case BIT_AND_ASN -> new IP_And(argTarget, arg);
                    case BIT_OR_ASN  -> new IP_Or(argTarget, arg);
                    case BIT_XOR_ASN -> new IP_Xor(argTarget, arg);
                    default          -> throw new IllegalStateException("op=" + tokOp.getId().TEXT);
                };
                break;
            }

            case TargetProp: {
                PropertyConstant prop      = getProperty();
                Argument         argTarget = getTarget();
                op = switch (tokOp.getId()) {
                    case ADD_ASN     -> new PIP_Add(prop, argTarget, arg);
                    case SUB_ASN     -> new PIP_Sub(prop, argTarget, arg);
                    case MUL_ASN     -> new PIP_Mul(prop, argTarget, arg);
                    case DIV_ASN     -> new PIP_Div(prop, argTarget, arg);
                    case MOD_ASN     -> new PIP_Mod(prop, argTarget, arg);
                    case SHL_ASN     -> new PIP_Shl(prop, argTarget, arg);
                    case SHR_ASN     -> new PIP_Shr(prop, argTarget, arg);
                    case USHR_ASN    -> new PIP_ShrAll(prop, argTarget, arg);
                    case BIT_AND_ASN -> new PIP_And(prop, argTarget, arg);
                    case BIT_OR_ASN  -> new PIP_Or(prop, argTarget, arg);
                    case BIT_XOR_ASN -> new PIP_Xor(prop, argTarget, arg);
                    default          -> throw new IllegalStateException("op=" + tokOp.getId().TEXT);
                };
                break;
            }

            case Indexed:
            case IndexedProp: {
                Argument argArray = m_form == AssignForm.Indexed
                        ? getArray()
                        : getProperty();
                Argument argIndex = getIndex();
                op = switch (tokOp.getId()) {
                    case ADD_ASN     -> new IIP_Add(argArray, argIndex, arg);
                    case SUB_ASN     -> new IIP_Sub(argArray, argIndex, arg);
                    case MUL_ASN     -> new IIP_Mul(argArray, argIndex, arg);
                    case DIV_ASN     -> new IIP_Div(argArray, argIndex, arg);
                    case MOD_ASN     -> new IIP_Mod(argArray, argIndex, arg);
                    case SHL_ASN     -> new IIP_Shl(argArray, argIndex, arg);
                    case SHR_ASN     -> new IIP_Shr(argArray, argIndex, arg);
                    case USHR_ASN    -> new IIP_ShrAll(argArray, argIndex, arg);
                    case BIT_AND_ASN -> new IIP_And(argArray, argIndex, arg);
                    case BIT_OR_ASN  -> new IIP_Or(argArray, argIndex, arg);
                    case BIT_XOR_ASN -> new IIP_Xor(argArray, argIndex, arg);
                    default          -> throw new IllegalStateException("op=" + tokOp.getId().TEXT);
                };
                break;
            }

            case IndexedN:
            case IndexedNProp:
                // TODO
                throw notImplemented();

            default:
                throw new IllegalStateException();
            }

            code.add(op);
        }

        // ----- fields ------------------------------------------------------------------------

        private final AssignForm m_form;
        private Argument         m_arg;
        private PropertyConstant m_prop;
        private Object           m_oIndex;
    }

    /**
     * The form of Assignable.
     *
     * <ul>
     *   <li>{@link #BlackHole} - a write-only register that anyone can assign to, resulting in
     *       the value being discarded</li>
     *   <li>{@link #LocalVar} - a local variable of a method that can be assigned</li>
     *   <li>{@link #LocalProp} - a local (this:private) property that can be assigned</li>
     *   <li>{@link #TargetProp} - a property of a specified reference that can be assigned</li>
     *   <li>{@link #Indexed} - an index into a single-dimensioned array</li>
     *   <li>{@link #IndexedN} - an index into a multi-dimensioned array</li>
     *   <li>{@link #IndexedProp} - an index into a single-dimensioned array property</li>
     *   <li>{@link #IndexedNProp} - an index into a multi-dimensioned array property</li>
     * </ul>
     */
    public enum AssignForm {BlackHole, LocalVar, LocalProp, TargetProp,
                            Indexed, IndexedN, IndexedProp, IndexedNProp}

    // ----- Sequential enumeration ----------------------------------------------------------------

    /**
     * Describes a pre/post increment/decrement operation.
     */
    public enum Sequential {
        Inc, PreInc, PostInc, Dec, PreDec, PostDec;

        /**
         * @return true iff the operation is an increment
         */
        public boolean isInc() {
            return this.compareTo(PostInc) <= 0;
        }

        /**
         * @return true iff the operation is a "blind" increment or decrement
         */
        public boolean isBlind() {
            return this == Inc | this == Dec;
        }

        /**
         * @return the "blind" form of this operation
         */
        public Sequential toBlind() {
            return this.isInc() ? Inc : Dec;
        }

        /**
         * @return true iff the operation is a pre-increment or pre-decrement
         */
        public boolean isPre() {
            return this == PreInc | this == PreDec;
        }

        /**
         * @return true iff the operation is a post-increment or post-decrement
         */
        public boolean isPost() {
            return this == PostInc | this == PostDec;
        }
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
    public enum TypeFit {
        NoFit         (0b0000),
        ConvPackUnpack(0b1111),
        ConvPack      (0b1011),
        ConvUnpack    (0b0111),
        Conv          (0b0011),
        PackUnpack    (0b1101),
        Pack          (0b0101),
        Unpack        (0b1001),
        Fit           (0b0001);

        /**
         * Constructor.
         *
         * @param nFlags  bit flags defining how good a fit the TypeFit is
         */
        TypeFit(int nFlags) {
            FLAGS = nFlags;
        }

        /**
         * @return true iff the type fits, regardless of whether it needs conversion or packing or
         *         unpacking
         */
        public boolean isFit() {
            return (FLAGS & FITS) != 0;
        }

        /**
         * @return a TypeFit that does everything this TypeFit does, plus fits
         */
        public TypeFit ensureFit() {
            return isFit()
                    ? this
                    : Fit;
        }

        /**
         * @return true iff the type goes through at least one "@Auto" conversion in order to fit
         */
        public boolean isConverting() {
            return (FLAGS & CONVERTS) != 0;
        }

        /**
         * @return a TypeFit that does everything this TypeFit does, plus type conversion
         */
        public TypeFit addConversion() {
            return isFit()
                    ? forFlags(FLAGS | CONVERTS)
                    : NoFit;
        }

        /**
         * @return a TypeFit that does everything this TypeFit does, minus type conversion
         */
        public TypeFit removeConversion() {
            return isConverting()
                    ? forFlags(FLAGS & ~CONVERTS)
                    : this;
        }

        /**
         * @return true iff the type goes through a tuple creation
         */
        public boolean isPacking() {
            return (FLAGS & PACKS) != 0;
        }

        /**
         * @return a TypeFit that does everything this TypeFit does, plus Tuple packing
         */
        public TypeFit addPack() {
            return isFit()
                    ? forFlags(FLAGS | PACKS)
                    : NoFit;
        }

        /**
         * @return a TypeFit that does everything this TypeFit does, minus Tuple packing
         */
        public TypeFit removePack() {
            return isPacking()
                    ? forFlags(FLAGS & ~PACKS)
                    : this;
        }

        /**
         * @return true iff the type goes through a tuple extraction
         */
        public boolean isUnpacking() {
            return (FLAGS & UNPACKS) != 0;
        }

        /**
         * @return a TypeFit that does everything this TypeFit does, plus Tuple unpacking
         */
        public TypeFit addUnpack() {
            return isFit()
                    ? forFlags(FLAGS | UNPACKS)
                    : NoFit;
        }

        /**
         * @return a TypeFit that does everything this TypeFit does, minus Tuple unpacking
         */
        public TypeFit removeUnpack() {
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
        public TypeFit combineWith(TypeFit that) {
            return this.isFit() && that.isFit()
                    ? forFlags(this.FLAGS | that.FLAGS)
                    : NoFit;
        }

        /**
         * Determine which is the best fit, and return that best fit.
         *
         * @param that  the other fit
         *
         * @return whichever fit is considered better
         */
        public TypeFit betterOf(TypeFit that) {
            return this.ordinal() > that.ordinal() ? this : that;
        }

        /**
         * Determine if another fit is better than this fit.
         *
         * @param that  the other fit
         *
         * @return true iff the other fit is considered to be a better fit than this fit
         */
        public boolean betterThan(TypeFit that) {
            return this.ordinal() > that.ordinal();
        }

        /**
         * Determine if another fit is worse than this fit.
         *
         * @param that  the other fit
         *
         * @return true iff the other fit is considered to be a worse fit than this fit
         */
        public boolean worseThan(TypeFit that) {
            return this.ordinal() < that.ordinal();
        }

        /**
         * Look up a TypeFit enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the TypeFit enum for the specified ordinal
         */
        public static TypeFit valueOf(int i) {
            return BY_ORDINAL[i];
        }

        /**
         * Look up a TypeFit enum by its flags.
         *
         * @param nFlags  the flags
         *
         * @return the TypeFit enum for the specified ordinal
         */
        public static TypeFit forFlags(int nFlags) {
            if (nFlags >= 0 && nFlags <= BY_FLAGS.length) {
                TypeFit fit = BY_FLAGS[nFlags];
                if (fit != null) {
                    return fit;
                }
            }

            throw new IllegalStateException("no fit for flag value: " + nFlags);
        }

        /**
         * All the TypeFit enums, by ordinal.
         */
        private static final TypeFit[] BY_ORDINAL = TypeFit.values();

        /**
         * All the TypeFit enums, by flags.
         */
        private static final TypeFit[] BY_FLAGS = new TypeFit[0b10000];

        static {
            for (TypeFit fit : BY_ORDINAL) {
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


    /**
     * Return values for the {@link #mightAffect} method.
     */
    public enum SideEffect {
        DefNo,      // there is definitely no side effect
        Unknown,    // there is no information one way or another
        AnyCompute, // an expression "on the left" is a property, which could be affected by any
                    // computation "on the right" (anything but a register access)
        AnySeqOp,   // an expression "on the left" is a register, which could be affected by any
                    // sequential operation on the same register "on the right"
        DefYes      // side effects are likely
    }


    // ----- fields --------------------------------------------------------------------------------

    public static final Assignable[] NO_LVALUES = new Assignable[0];
    public static final Argument[]   NO_RVALUES = new Argument[0];

    private static final int IN_ASSIGNMENT = 1 << 30;
    private static final int ILLEGAL_SHORT = 1 << 29;

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
     * Various temporary flags.
     */
    private transient int m_nFlags;
}
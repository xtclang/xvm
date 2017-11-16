package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.I_Set;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.L_Set;
import org.xvm.asm.op.Label;

import org.xvm.asm.op.Move;
import org.xvm.asm.op.P_Set;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;

import org.xvm.util.Severity;


/**
 * Base class for all Ecstasy expressions.
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
 * </pre>
 * <p/>
 * <ul> <li>TODO need to pass in Scope but one that knows name->Register association?
 * </li><li>TODO how to do captures?
 * </li><li>TODO how to do definite assignment?
 * </li><li>TODO a version of this for conditional? or just a boolean parameter that says this is asking for a conditional?
 * </li></ul>
 */
public abstract class Expression
        extends AstNode
    {
    // ----- accessors -----------------------------------------------------------------------------

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


    // ----- compilation ---------------------------------------------------------------------------

    /**
     * Determine the number of values represented by the expression.
     * <ul>
     * <li>A {@code Void} expression represents no values</li>
     * <li>An {@link #isSingle() isSingle()==true} expression represents exactly one value (most common)</li>
     * <li>A multi-value expression represents more than one value</li>
     * </ul>
     * <p/>
     * This method must be overridden by any expression that represents any number of values other
     * than one, or that could be composed of other expressions in such a way that the result is
     * that this expression could represent a number of values other than one.
     *
     * @return the number of values represented by the expression
     */
    public int getValueCount()
        {
        return 1;
        }

    /**
     * @return true iff the Expression represents exactly one value
     */
    public boolean isSingle()
        {
        return getValueCount() == 1;
        }

    /**
     * Determine if the expression represents a {@code conditional} result.
     * <p/>
     * This method must be overridden by any expression that represents or could represent a
     * conditional result, including as the result of composition of other expressions that could
     * represent a conditional result.
     *
     * @return true iff the Expression represents a conditional value
     */
    public boolean isConditional()
        {
        return false;
        }

    /**
     * Determine the implicit (or "natural") type of the expression, which is the type that the
     * expression would naturally compile to if no type were specified. For a multi-value
     * expression, the TypeConstant is returned as a tuple of the types of the multiple values.
     * A Void type is indicated by a parameterized tuple type of zero parameters (fields).
     * <p/>
     * Either this method or {@link #getImplicitTypes()} must be overridden.
     *
     * @return the implicit type of the expression
     */
    public TypeConstant getImplicitType()
        {
        checkDepth();

        return isSingle()
                ? getImplicitTypes()[0]
                : pool().ensureParameterizedTypeConstant(pool().typeTuple(), getImplicitTypes());
        }

    /**
     * Obtain an array of types, one for each value that this expression represents.
     * <p/>
     * Either this method or {@link #getImplicitType()} must be overridden.
     *
     * @return the implicit types of the multiple values of the expression; a zero-length array
     *         indicates a Void type
     */
    public TypeConstant[] getImplicitTypes()
        {
        checkDepth();

        TypeConstant type = getImplicitType();
        if (isSingle())
            {
            return new TypeConstant[] {type};
            }
        else
            {
            // it's reasonable to expect that classes will override this method as appropriate to
            // avoid this type of inefficiency
            assert type.isTuple() && type.isParamsSpecified();
            List<TypeConstant> list = type.getParamTypes();
            return list.toArray(new TypeConstant[list.size()]);
            }
        }

    /**
     * Determine if the expression represents an L-Value, which means that this expression can be
     * assigned to.
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
     * Determine if the expression can complete.
     * <p/>
     * This method must be overridden by any expression does not complete, or that contains
     * another expression that may not be completable.
     *
     * @return true iff the expression is capable of completing normally
     */
    public boolean isCompletable()
        {
        return true;
        }

    /**
     * Determine if the expression can short-circuit.
     * <p/>
     * This method must be overridden by any expression can short circuit, or that contains
     * another expression that may be short-circuiting.
     *
     * @return true iff the expression is capable of short-circuiting
     */
    public boolean isShortCircuiting()
        {
        return false;
        }

    /**
     * Determine if the expression is constant.
     * <p/>
     * This method must be overridden by any expression is not guaranteed to be constant.
     *
     * @return true iff the Expression is a constant value
     */
    public boolean isConstant()
        {
        return true;
        }

    /**
     * For a constant expression, create a constant representations of the value. The type of the
     * constant will match the result of {@link #getImplicitType()}.
     * <p/>
     * An exception is generated if the expression is not constant.
     *
     * @return the default constant form of the expression, iff the expression is constant
     */
    public Constant toConstant()
        {
        if (!isConstant() || !isSingle())
            {
            throw new IllegalStateException();
            }

        throw notImplemented();
        }

    /**
     * Convert this expression to a constant value, which is possible iff {@link #isConstant}
     * returns true.
     * <p/>
     * This method may be overridden by any expression that can produce better code than the default
     * constant conversion code, or can do so more efficiently.
     *
     * @param code  the code block
     * @param type  the constant type required
     * @param errs  the error list to log any errors to if this expression cannot be made into
     *              a constant value of the specified type
     *
     * @return a constant of the specified type
     */
    public Constant generateConstant(Code code, TypeConstant type, ErrorListener errs)
        {
        checkDepth();

        if (isConstant())
            {
            return validateAndConvertConstant(toConstant(), type, errs);
            }

        log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
        return generateFakeConstant(type);
        }

    /**
     * Generate an argument that represents the result of this expression.
     * <p/>
     * This method must be overridden by any expression that is not always constant.
     *
     * @param code      the code block
     * @param type      the type that the expression must evaluate to
     * @param fTupleOk  true if the result can be a tuple of the the specified type
     * @param errs      the error list to log any errors to
     *
     * @return a resulting argument of the specified type, or of a tuple of the specified type if
     *         that is both allowed and "free" to produce
     */
    public Argument generateArgument(Code code, TypeConstant type, boolean fTupleOk, ErrorListener errs)
        {
        checkDepth();

        if (isConstant())
            {
            return generateConstant(code, type, errs);
            }

        throw notImplemented();
        }

    /**
     * Generate arguments of the specified types for this expression, or generate an error if that
     * is not possible.
     * <p/>
     * This method may be overridden by any expression that is multi-value-aware.
     *
     * @param code      the code block
     * @param atype     an array of types that the expression must evaluate to
     * @param fTupleOk  true if the result can be a tuple of the the specified types
     * @param errs      the error list to log any errors to
     *
     * @return a list of resulting arguments, which will either be the same size as the passed list,
     *         or size 1 for a tuple result if that is both allowed and "free" to produce
     */
    public Argument[] generateArguments(Code code, TypeConstant[] atype, boolean fTupleOk, ErrorListener errs)
        {
        checkDepth();

        switch (atype.length)
            {
            case 0:
                // Void means that the results of the expression are black-holed
                generateAssignments(code, NO_LVALUES, errs);
                return NO_RVALUES;

            case 1:
                return new Argument[] {generateArgument(code, atype[0], fTupleOk, errs)};

            default:
                if (fTupleOk)
                    {
                    ConstantPool pool = pool();
                    TypeConstant typeTuple =
                            pool.ensureParameterizedTypeConstant(pool.typeTuple(), atype);
                    return new Argument[] {generateArgument(code, typeTuple, false, errs)};
                    }
            }

        log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, 1, atype.length);
        return NO_RVALUES;
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
     * @return
     */
    public Assignable generateAssignable(Code code, ErrorListener errs)
        {
        checkDepth();

        if (!isAssignable() || !isSingle())
            {
            throw new IllegalStateException();
            }

        return generateAssignables(code, errs)[0];
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

        if (!isAssignable())
            {
            throw new IllegalStateException();
            }

        switch (getValueCount())
            {
            case 0:
                return new Assignable[0];

            case 1:
                return new Assignable[] {generateAssignable(code, errs)};

            default:
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, 1, getValueCount());
                return NO_LVALUES;
            }
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

        // this will be overridden by classes that can push down the work
        Argument arg = generateArgument(code, LVal.getType(), false, errs);
        LVal.assign(arg, code, errs);
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
        if (cLVals == 1)
            {
            generateAssignment(code, aLVal[0], errs);
            return;
            }

        TypeConstant[] aType = new TypeConstant[cLVals];
        for (int i = 0; i < cLVals; ++i)
            {
            aType[i] = aLVal[i].getType();
            }
        Argument[] aArg = generateArguments(code, aType, false, errs);
        for (int i = 0; i < cLVals; ++i)
            {
            aLVal[i].assign(aArg[i], code, errs);
            }
        }

    /**
     * TODO
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

        // this is just a generic implementation; sub-classes should override this simplify the
        // generated code (e.g. by not having to always generate a separate boolean value)
        Argument arg = generateArgument(code, pool().typeBoolean(), false, errs);
        code.add(fWhenTrue
                ? new JumpTrue(arg, label)
                : new JumpFalse(arg, label));
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
        TypeConstant typeImplicit = null;

        // first, layer-by-layer peel the "type onion" down to the terminal(s)
        switch (typeThat.getFormat())
            {
            case UnionType:
            {
            TypeConstant typeThat1 = typeThat.getUnderlyingType();
            TypeConstant typeThat2 = typeThat.getUnderlyingType2();
            if (!(isAssignableTo(typeThat1) && isAssignableTo(typeThat2)))
                {
                break;
                }

            // even though each of the two types was individually assignable to, there are rare
            // examples that are NOT allowable, such as the literal 0 being assignable to two
            // different Int classes
            if (typeThat1.isClassType() && typeThat2.isClassType())
                {
                HashSet<IdentityConstant> setClasses = new HashSet<>(5);
                setClasses.addAll(typeThat1.underlyingClasses());
                setClasses.addAll(typeThat2.underlyingClasses());
                if (setClasses.size() > 1)
                    {
                    // first check if the implicit type is a sub-class and/or impersonator of
                    // all of the classes implied by the union type
                    typeImplicit = getImplicitType();
                    if (typeImplicit.isA(typeThat))
                        {
                        return true;
                        }

                    // find a solution where there is one class that is a sub-class and/or
                    // impersonator of all other classes
                    int cClz = setClasses.size();
                    ClassStructure[] aclz = new ClassStructure[cClz];
                    int iClz = 0;
                    for (IdentityConstant constClz : setClasses)
                        {
                        aclz[iClz++] = (ClassStructure) constClz.getComponent();
                        }

                    // TODO currently this checks "extendsClass", but it needs a broader check that includes mixins, impersonation, etc.
                    ClassStructure clzSub = aclz[0];
                    NextClass:
                    for (iClz = 1; iClz < cClz; ++iClz)
                        {
                        ClassStructure clzCur = aclz[iClz];
                        if (clzSub == null)
                            {
                            // no current solution; see if the current class can be a sub to all
                            // the previous classes
                            for (int iSuper = 0; iSuper < iClz; ++iSuper)
                                {
                                if (!clzSub.extendsClass(aclz[iSuper].getIdentityConstant()))
                                    {
                                    // the current one is not a sub of all the previous ones
                                    continue NextClass;
                                    }
                                }

                            // the current one IS a sub of all the previous ones!
                            clzSub = clzCur;
                            }
                        else if (clzSub.extendsClass(clzCur.getIdentityConstant()))
                            {
                            // the current solution is still a good solution
                            continue NextClass;
                            }
                        else if (clzCur.extendsClass(clzSub.getIdentityConstant()))
                            {
                            // the current one appears to be a better solution than the previous
                            clzSub = clzCur;
                            }
                        else
                            {
                            // neither is a sub of the other
                            clzSub = null;
                            }
                        }

                    if (clzSub == null)
                        {
                        // no solution found
                        break;
                        }
                    }
                }

            return true;
            }

            case IntersectionType:
            {
            if (isAssignableTo(typeThat.getUnderlyingType()) ||
                    isAssignableTo(typeThat.getUnderlyingType2()))
                {
                return true;
                }

            // even though neither of the two types was individually assignable to, it is
            // possible that the intersection represents a duck-type-able interface, assuming
            // that none of the underlying types is a class
            // TODO - verify that none of the underlying types is a class type
            // TODO - resolve the intersection type into an interface type, and test assignability to that

            break;
            }

            case DifferenceType:
            {
            // TODO - resolve the difference type into an interface type, and test assignability to that

            break;
            }

            case ImmutableType:
            {
            // it is assumed that the expression is assignable to an immutable type if the
            // expression can be assigned to the specified type (without immutability specified),
            // and the expression is constant (which implies that the expression must be smart
            // enough to know how to compile itself as an immutable-type expression)
            if (isConstant() && isAssignableTo(typeThat.getUnderlyingType()))
                {
                return true;
                }

            break;
            }

            case AccessType:
            {
            // regardless of the accessibility override, assignability to the non-overridden
            // type is a pre-requisite
            if (!isAssignableTo(typeThat.getUnderlyingType()))
                {
                break;
                }

            if (typeThat.getAccess() != Access.PUBLIC)
                {
                // the non-public type needs to be flattened to an interface and evaluated
                // TODO - resolve the non-public type into an interface type, and test assignability to that

                break;
                }

            return true;
            }

            case ParameterizedType:
            {
            if (!isAssignableTo(typeThat.getUnderlyingType()))
                {
                break;
                }

            // either this expression evaluates implicitly to the same parameterized type (or a  itself, or we

            // the non-parameterized type was assignable to; flatten the parameterized type into an
            // interface and evaluate
            // TODO

            return true;
            }

            case AnnotatedType:
            {
            // TODO
            notImplemented();
            break;
            }

            case TerminalType:
            {
            if (typeThat.isEcstasy("Object"))
                {
                // everything is assignable to Object
                return true;
                }

            // an expression is assignable to a type, by default, if its implicit type is
            // assignable to that type; this is over-ridden; this will probably need to be
            // overwritten by various expressions
            typeImplicit = getImplicitType();
            if (typeImplicit.isA(typeThat))
                {
                return true;
                }

            break;
            }

            default:
                throw new IllegalStateException("format=" + typeThat.getFormat());
            }

        // obtain the implicit type if we have not already done so
        if (typeImplicit == null)
            {
            typeImplicit = getImplicitType();
            }

        // find all of the possible @Auto conversion functions for the type of the current
        // expression, and for each one, test whether the result of the conversion is assignable to
        // the specified type:
        // - if there are no solutions, then assignment is not possible
        // - if there is one solution, then assignment is possible
        // - if there is more than one solution, then assignment is only possible if all of the
        //   solutions result in the same type
        TypeConstant         typeSolution = null;
        Set<TypeConstant>    setTested    = new HashSet<>();
        List<MethodConstant> listPossible = new ArrayList<>(typeImplicit.autoConverts());
        for (int i = 0; i < listPossible.size(); ++i)
            {
            MethodConstant constMethod = listPossible.get(i);
            TypeConstant   typeReturn  = constMethod.getRawReturns()[0];

            // make sure we don't try any more conversion methods that return the same type
            if (!setTested.add(typeReturn))
                {
                // we already tested a conversion to this same type
                continue;
                }

            // check to see if this conversion gets us where we want to go
            // TODO this needs to be a full "is assignable to" test, not just an "isA" test
            if (typeReturn.isA(typeThat))
                {
                if (typeSolution == null)
                    {
                    // when we actually implement this, we will need a chain of conversions that got
                    // us here, but to answer this question, we just need to know that it is
                    // possible to get here at all
                    typeSolution = typeReturn;
                    }
                else if (typeSolution.equals(typeReturn))
                    {
                    // we already found this solution
                    continue;
                    }
                else
                    {
                    // two different solutions means no solution, i.e. the result is ambiguous
                    return false;
                    }
                }

            // it is possible that the type that we got to can be further converted to get us where
            // we want to ultimately get to
            listPossible.addAll(typeReturn.autoConverts());
            }

        return typeSolution != null;
        }

    /**
     * @return true iff the Expression is of the type "Boolean"
     */
    public boolean isTypeBoolean()
        {
        return getImplicitType().isEcstasy("Boolean") || isAssignableTo(pool().typeBoolean());
        }

    /**
     * @return true iff the Expression is the constant value "false"
     */
    public boolean isConstantFalse()
        {
        return isConstant() && toConstant().equals(pool().valFalse());
        }

    /**
     * @return true iff the Expression is the constant value "false"
     */
    public boolean isConstantTrue()
        {
        return isConstant() && toConstant().equals(pool().valTrue());
        }

    /**
     * @return true iff the Expression is the constant value "Null"
     */
    public boolean isConstantNull()
        {
        return isConstant() && toConstant().equals(pool().valNull());
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
        String sClassName = typeOut.getEcstasyClassName();
        if (sClassName.equals(constIn.getFormat().name()))
            {
            // common case; no conversion is necessary
            return constIn;
            }

        Constant constOut = null;

        if (!sClassName.equals("?")) // TODO barf
            {
            // hand the conversion request down to the constant and see if it knows how to do it
            constOut = constIn.convertTo(typeOut);
            }

        if (constOut == null)
            {
            // we have to return something, even in the case of an error
            constOut = constIn;

            // it has to be assignable from the constant that got passed in, because the constant
            // wasn't able to convert itself to the requested type
            if (!constIn.getType().isA(constOut.getType()))
                {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeOut, constIn.getType());
                }
            }

        return constOut;
        }

    /**
     * Given an argument, verify that it can be assigned to (or somehow converted to) the specified
     * type, and do so.
     *
     * @param argIn    the argument that needs to be validated as assignable
     * @param code     the code block
     * @param typeOut  the type that the argument must be assignable to
     * @param errs     the error list to log any errors to, for example if the object cannot be
     *                 coerced in a manner to make it assignable
     *
     * @return the argument to use
     */
    protected Argument validateAndConvertSingle(Argument argIn, Code code, TypeConstant typeOut, ErrorListener errs)
        {
        // assume that the result is the same as what was passed in
        Argument argOut = argIn;

        TypeConstant typeIn = argIn.getType();
        if (!typeIn.equals(typeOut))
            {
            // verify that a conversion is possible
            if (!typeIn.isA(typeOut))
                {
                // TODO isA() doesn't handle a lot of things that are actually assignable
                // TODO for things provably not assignable, check for an @Auto method
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeOut, typeIn);
                }
            }

        return argOut;
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
     *
     * @param listTypes
     * @return
     */
    protected List<Register> generateBlackHoles(List<TypeConstant> listTypes)
        {
        int       cTypes   = listTypes == null ? 0 : listTypes.size();
        ArrayList listRegs = new ArrayList(cTypes);
        for (int i = 0; i < cTypes; ++i)
            {
            listRegs.add(generateBlackHole(listTypes.get(i)));
            }
        return listRegs;
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
        // TODO
        return pool().valFalse();
        }

    /**
     * Temporary to prevent stack overflow from methods that haven't yet been overridden.
     *
     * @throws UnsupportedOperationException if it appears that there is an infinite loop
     */
    protected void checkDepth()
        {
        if (++m_cDepth > 20)
            {
            throw notImplemented();
            }
        }
    private int m_cDepth;


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
            m_reg   = regVar;
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
            m_reg   = regTarget;
            m_prop  = constProp;
            }

        /**
         * Construct an Assignable based on a single dimension array local variable.
         *
         * @param argArray  the Register, representing the local variable holding an array
         * @param index     the index into the array
         */
        public Assignable(Register argArray, Argument index)
            {
            m_nForm  = Indexed;
            m_reg    = argArray;
            m_oIndex = index;
            }

        /**
         * Construct an Assignable based on a multi (any) dimension array local variable.
         *
         * @param regArray  the Register, representing the local variable holding an array
         * @param indexes   an array of indexes into the array
         */
        public Assignable(Register regArray, Argument[] indexes)
            {
            assert indexes != null && indexes.length > 0;

            m_nForm  = indexes.length == 1 ? Indexed : IndexedN;
            m_reg    = regArray;
            m_oIndex = indexes.length == 1 ? indexes[0] : indexes;
            }

        /**
         * Construct an Assignable based on a local property that is a single dimension array.
         *
         * @param argArray  the Register, representing the local variable holding an array
         * @param index     the index into the array
         */
        public Assignable(Register argArray, PropertyConstant constProp, Argument index)
            {
            m_nForm  = IndexedProp;
            m_reg    = argArray;
            m_prop   = constProp;
            m_oIndex = index;
            }

        /**
         * Construct an Assignable based on a local property that is a a multi (any) dimension array.
         *
         * @param regArray  the Register, representing the local variable holding an array
         * @param indexes   an array of indexes into the array
         */
        public Assignable(Register regArray, PropertyConstant constProp, Argument[] indexes)
            {
            assert indexes != null && indexes.length > 0;

            m_nForm  = indexes.length == 1 ? IndexedProp : IndexedNProp;
            m_reg    = regArray;
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
            return m_reg;
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
            return m_reg;
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
            return m_reg;
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

        public static final int BlackHole    = 0;
        public static final int LocalVar     = 1;
        public static final int LocalProp    = 2;
        public static final int TargetProp   = 3;
        public static final int Indexed      = 4;
        public static final int IndexedN     = 5;
        public static final int IndexedProp  = 6;
        public static final int IndexedNProp = 7;

        private int              m_nForm;
        private Register         m_reg;
        private PropertyConstant m_prop;
        private Object           m_oIndex;
        }


    // ----- fields --------------------------------------------------------------------------------

    public static final Assignable[] NO_LVALUES = new Assignable[0];
    public static final Argument[]   NO_RVALUES = new Argument[0];
    }

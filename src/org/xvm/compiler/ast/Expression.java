package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.Collections;
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

import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;

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
     * A Void type is indicated by a paramterized tuple type of zero parameters (fields).
     * <p/>
     * Either this method or {@link #getImplicitTypes()} must be overridden.
     * @return the implicit type of the expression
     */
    public TypeConstant getImplicitType()
        {
        if (isSingle())
            {
            return getImplicitTypes()[0];
            }
        else
            {
            return pool().ensureParameterizedTypeConstant(pool().typeTuple(), getImplicitTypes());
            }
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
     * TODO
     * The type of assignability:
     * <ul>
     * <li>Constant - an item in the constant pool that cannot be assigned to</li>
     * <li>Reserved - an item provided by the runtime, like "this", that cannot be assigned to</li>
     * <li>Parameter - a register for a method parameter, which cannot be assigned to</li>
     * <li>BlackHole - a write-only register that anyone can assign to, discarding the value</li>
     * <li>LocalVar - a local variable of a method that can be assigned to</li>
     * <li>LocalProp - a local (this:private) property that can be assigned to</li>
     * <li>TargetProp - a property of a specified reference that can be assigned to</li>
     * <li>Indexed - an index into a single-dimensioned array</li>
     * <li>IndexedN - an index into a multi-dimensioned array</li>
     * </ul>
     * enum Assignable {ReadOnly, BlackHole, LocalVar, LocalProp, TargetProp, Indexed, IndexedN}
     * how to handle assignments to the black hole / void?
     * is it a variable?
     *  - what register?
     *  - is it a read-only register?
     * is it a property?
     *  - what is the property constant?
     *  - what is the ref (target)? (argument?)
     *  - is it a local property?
     * is it an array element?
     *  - what is the ref (array)?
     *  - how many dimensions?
     *  - what is the index for each dimension?
     */
    public interface Assignable
        {
        /**
         * @return the type of the L-Value
         */
        TypeConstant getType();

        void assign(Argument arg, Code code, ErrorListener errs);

        enum Form {BlackHole, LocalVar, LocalProp, TargetProp, Indexed, IndexedN}

        Form getForm();

        Register getRegister();

        Argument getTarget();

        PropertyConstant getProperty();

        Argument getIndex();

        Argument[] getIndexes();
        }

    /**
     * For an L-Value expression with exactly one value, create a representation of the L-Value.
     * <p/>
     * An exception is generated if the expression is not assignable.
     *
     * @param code  the code block
     * @param errs  the error list to log any errors to
     *
     * @return
     */
    public Assignable generateAssignable(Code code, ErrorListener errs)
        {
        if (!isAssignable())
            {
            throw new IllegalStateException();
            }

        assert isSingle();
        throw notImplemented();
        }

    /**
     * For an L-Value expression, create representations of the L-Values.
     * <p/>
     * An exception is generated if the expression is not assignable.
     *
     * @param code  the code block
     * @param errs  the error list to log any errors to
     *
     * @return an array of {@link #getValueCount()} Assignable objects
     */
    public Assignable[] generateAssignables(Code code, ErrorListener errs)
        {
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
                throw notImplemented();
            }
        }

    /**
     * @return true iff the Expression is a constant value
     */
    public abstract boolean isConstant();

    /**
     * For a constant expression, create a constant representations of the value.
     * <p/>
     * An exception is generated if the expression is not constant.
     *
     * @return the default constant form of the expression, iff the expression is constant
     */
    public Constant toConstant()
        {
        if (!isConstant())
            {
            throw new IllegalStateException();
            }

        throw notImplemented();
        }

    /**
     * Convert this expression to a constant value, which is possible iff {@link #isConstant}
     * returns true.
     *
     * @param type  the constant type required
     * @param errs  the error list to log any errors to if this expression cannot be made into
     *              a constant value of the specified type
     *
     * @return a constant of the specified type
     */
    public Argument generateConstant(TypeConstant type, ErrorListener errs)
        {
        if (isConstant())
            {
            return validateAndConvertConstant(toConstant(), type, errs);
            }

        log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
        return generateBlackHole(type);
        }

    /**
     * Generate an argument that represents the result of this expression.
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
        if (isConstant())
            {
            return generateConstant(type, errs);
            }

        throw notImplemented();
        }

    /**
     * Generate arguments of the specified types for this expression, or generate an error if that
     * is not possible.
     *
     * @param code      the code block
     * @param atype     an array of types that the expression must evaluate to
     * @param fTupleOk  true if the result can be a tuple of the the specified types
     * @param errs      the error list to log any errors to
     *
     * @return a list of resulting arguments, which will either be the same size as the passed list,
     *         or size 1 for a tuple result if that is both allowed and "free" to produce
     */
    public List<Argument> generateArguments(Code code, TypeConstant[] atype, boolean fTupleOk, ErrorListener errs)
        {
        switch (atype.length)
            {
            case 0:
                // Void means that the results of the expression are black-holed
                generateAssignments(code, atype, errs);
                return Collections.EMPTY_LIST;

            case 1:
                return Collections.singletonList(generateArgument(code, atype[0], fTupleOk, errs));

            default:
                if (fTupleOk)
                    {
                    ConstantPool pool = pool();
                    TypeConstant typeTuple = pool.ensureParameterizedTypeConstant(pool.typeTuple(), atype);
                    return Collections.singletonList(generateArgument(code, typeTuple, false, errs));
                    }
            }

        log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, 1, atype.length);
        return Collections.EMPTY_LIST;
        }

    /**
     *
     * @param code
     * @param LVal
     * @param errs
     */
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        // TODO should be abstract
        assert isAssignable();
        throw notImplemented();
        }

    public void generateAssignments(Code code, Assignable[] aLVal, ErrorListener errs)
        {
        // TODO should be abstract
        assert isAssignable();
        throw notImplemented();
        }

    /**
     *
     *
     * @param code       the code block
     * @param label      the label to conditionally jump to
     * @param fWhenTrue  indicates whether to jump when this expression evaluates to true, or
     *                   whether to jump when this expression evaluates to false
     * @param errs       the error list to log any errors to
     */
    public void generateConditionalJump(Code code, Label label, boolean fWhenTrue, ErrorListener errs)
        {
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
                        int              cClz = setClasses.size();
                        ClassStructure[] aclz = new ClassStructure[cClz];
                        int              iClz = 0;
                        for (IdentityConstant constClz : setClasses)
                            {
                            aclz[iClz++] = (ClassStructure) constClz.getComponent();
                            }

                        // TODO currently this checks "extendsClass", but it needs a broader check that includes mixins, impersonation, etc.
                        ClassStructure clzSub = aclz[0];
                        NextClass: for (iClz = 1; iClz < cClz; ++iClz)
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
                if (    isAssignableTo(typeThat.getUnderlyingType()) ||
                        isAssignableTo(typeThat.getUnderlyingType2()   ))
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
        // TODO this does not seem correct
        return getImplicitType().isEcstasy("Boolean");
        }

    /**
     * @return true iff the Expression is the constant value "false"
     */
    public boolean isConstantFalse()
        {
        // TODO this does not seem correct
        return getImplicitType().isEcstasy("Boolean.False");
        }

    /**
     * @return true iff the Expression is the constant value "false"
     */
    public boolean isConstantTrue()
        {
        // TODO this does not seem correct
        return getImplicitType().isEcstasy("Boolean.True");
        }

    /**
     * @return true iff the Expression is the constant value "Null"
     */
    public boolean isConstantNull()
        {
        // TODO this does not seem correct
        return getImplicitType().isEcstasy("Nullable.Null");
        // perhaps: return isAssignableTo(pool().typeNullable());
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
    protected Constant validateAndConvertConstant(Constant constIn, TypeConstant typeOut,
            ErrorListener errs)
        {
        // assume that the result is the same as what was passed in
        Constant constOut = constIn;

        TypeConstant typeIn =  constIn.getType();
        if (!typeIn.equals(typeOut))
            {
            // verify that a conversion is possible
            if (!typeIn.isA(typeOut))
                {
                // TODO isA() doesn't handle a lot of things that are actually assignable
                // TODO for things provably not assignable, check for an @Auto method
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeOut, typeIn);
                }

            // TODO hard-coded conversions
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
    }

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
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;

import org.xvm.util.Severity;


/**
 * Base class for all Ecstasy expressions.
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
     * @return true iff the Expression represents an "L-value" to which a value can be assigned
     */
    public boolean isAssignable()
        {
        return false;
        }

    /**
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
     */
    enum Assignable {ReadOnly, BlackHole, LocalVar, LocalProp, TargetProp, Indexed, IndexedN}

    public Assignable getAssignability()
        {
        assert !isAssignable();
        return Assignable.ReadOnly;
        }

    // TODO how to handle assignments to the black hole / void?
    // TODO is it a variable?
    //  - what register?
    //  - is it a read-only register?
    // TODO is it a property?
    //  - what is the property constant?
    //  - what is the ref (target)? (argument?)
    //  - is it a local property?
    // TODO is it an array element?
    //  - what is the ref (array)?
    //  - how many dimensions?
    //  - what is the index for each dimension?

    /**
     * @return true iff the Expression is a constant value
     */
    public boolean isConstant()
        {
        return false;
        }

    // TODO remove?
    public Constant toConstant()
        {
        assert isConstant();
        throw notImplemented();
        }

    /**
     * Determine the implicit (or "natural") type of the expression, which is the type that the
     * expression would naturally compile to if no type were specified.
     *
     * @return the implicit type of the expression
     */
    public TypeConstant getImplicitType()
        {
        // TODO this should be an abstract method
        throw notImplemented();
        }

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
        return getImplicitType().isEcstasy("Boolean");
        }

    /**
     * @return true iff the Expression is the constant value "false"
     */
    public boolean isConstantFalse()
        {
        return getImplicitType().isEcstasy("Boolean.False");
        }

    /**
     * @return true iff the Expression is the constant value "false"
     */
    public boolean isConstantTrue()
        {
        return getImplicitType().isEcstasy("Boolean.True");
        }

    /**
     * @return true iff the expression is capable of completing normally
     */
    public boolean canComplete()
        {
        return true;
        }

    /**
     * Convert this expression to a constant value, which is possible iff {@link #isConstant}
     * returns true.
     *
     * @param constType  the constant type required
     * @param errs       the error list to log any errors to if this expression cannot be made into
     *                   a constant value of the specified type
     *
     * @return a constant of the specified type
     */
    public Argument generateConstant(TypeConstant constType, ErrorListener errs)
        {
        if (isConstant())
            {
            throw notImplemented();
            }

        log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
        return generateBlackHole(constType);
        }

    /**
     * Generate an argument that represents the result of this expression.
     *
     * <p/>
     * <ul> <li>TODO need to pass in Scope but one that knows name->Register association?
     * </li><li>TODO how to do captures?
     * </li><li>TODO how to do definite assignment?
     * </li><li>TODO a version of this for conditional? or just a boolean parameter that says this is asking for a conditional?
     * </li></ul>
     *
     * @param code       the code block
     * @param constType  the type that the expression must evaluate to
     * @param fTupleOk   true if the result can be a tuple of the the specified type
     * @param errs       the error list to log any errors to
     *
     * @return a resulting argument of the specified type, or of a tuple of the specified type if
     *         that is both allowed and "free" to produce
     */
    public Argument generateArgument(Code code, TypeConstant constType, boolean fTupleOk, ErrorListener errs)
        {
        // TODO should be abstract
        throw notImplemented();
        }

    /**
     * Generate arguments of the specified types for this expression, or generate an error if that
     * is not possible.
     *
     * @param code       the code block
     * @param listTypes  a list of types that the expression must evaluate to
     * @param fTupleOk   true if the result can be a tuple of the the specified types
     * @param errs       the error list to log any errors to
     *
     * @return a list of resulting arguments, which will either be the same size as the passed list,
     *         or size 1 for a tuple result if that is both allowed and "free" to produce
     */
    public List<Argument> generateArguments(Code code, List<TypeConstant> listTypes, boolean fTupleOk, ErrorListener errs)
        {
        switch (listTypes.size())
            {
            case 0:
                // Void means that the results of the expression are discarded; method will override
                // this to black-hole the results instead of creating arguments for them
                generateArgument(code, getImplicitType(), true, errs);
                return Collections.EMPTY_LIST;

            case 1:
                return Collections.singletonList(generateArgument(code, listTypes.get(0), fTupleOk, errs));

            default:
                if (fTupleOk)
                    {
                    ConstantPool pool = getConstantPool();
                    TypeConstant typeTuple = pool.ensureParameterizedTypeConstant(
                            pool.ensureEcstasyTypeConstant("collections.Tuple"),
                            listTypes.toArray(new TypeConstant[listTypes.size()]));

                    return Collections.singletonList(generateArgument(code, typeTuple, false, errs));
                    }
            }

        log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, 1, listTypes.size());
        return Collections.EMPTY_LIST;
        }

    public void generateAssignment(Code code, Expression exprLValue, ErrorListener errs)
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
        Argument arg = generateArgument(code, getConstantPool().ensureEcstasyTypeConstant("Boolean"), false, errs);
        code.add(fWhenTrue
                ? new JumpTrue(arg, label)
                : new JumpFalse(arg, label));
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

package org.xvm.compiler.ast;


import com.sun.tools.internal.jxc.ap.Const;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import java.util.Set;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.AccessTypeConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.DifferenceTypeConstant;
import org.xvm.asm.constants.ImmutableTypeConstant;
import org.xvm.asm.constants.IntersectionTypeConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.constants.UnionTypeConstant;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;

import org.xvm.util.LinkedIterator;
import org.xvm.util.Severity;


/**
 * Base class for all Ecstasy expressions.
 */
public abstract class Expression
        extends AstNode
    {
    // ----- accessors -----------------------------------------------------------------------------

    // TODO remove
    public Constant toConstant()
        {
        assert isConstant();
        throw notImplemented();
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


    // ----- compilation ---------------------------------------------------------------------------

    /**
     * @return true iff the Expression represents an "L-value" to which a value can be assigned
     */
    public boolean isAssignable()
        {
        return false;
        }

    /**
     * @return true iff the Expression is a constant value
     */
    public boolean isConstant()
        {
        return false;
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
                    HashSet<Constant> setClasses = new HashSet<>(5);
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

                        // TODO - in progress
                        // if (typeThat1.isA)

                        // find a solution where there is one class that is a sub-class and/or
                        // impersonator of all other classes
                        boolean fSolution = false;
                        for (Constant constTheOne : setClasses)
                            {
                            for (Constant constOther : setClasses)
                                {
                                // TODO - in progress
                                // if (constTheOne != constOther)
                                }
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

                // this will probably need to be overwritten by various expressions
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

        // find all of the possible @Auto conversion functions for the type of the current
        // expression, and for each one, test whether the result of the conversion is assignable to
        // the specified type
        if (typeImplicit == null)
            {
            typeImplicit = getImplicitType();
            }

        Set<MethodConstant> setPossible   = new HashSet<>(typeImplicit.autoConverts());
        Set<TypeConstant>   setEliminated = new HashSet<>();
        while (!setPossible.isEmpty())
            {
            MethodConstant constMethod = setPossible.iterator().next();
            TypeConstant   typeReturn  = constMethod.getRawReturns()[0];

            // make sure we don't try any more conversion methods that return the same type
            setPossible.remove(constMethod);
            if (!setEliminated.add(typeReturn))
                {
                // we already tested a conversion to this same type
                continue;
                }

            // check to see if this conversion gets us where we want to go
            // TODO this needs to be a full "is assignable to" test, not just an "isA" test
            if (typeReturn.isA(typeThat))

                {
                // when we actually implement this, we will need a chain of conversions that got us
                // here, but to answer this question, we just need to know that it is possible to
                // get here at all
                return true;
                }

            // it is possible that the type that we got to can be further converted to get us where
            // we want to ultimately get to
            setPossible.addAll(typeReturn.autoConverts());
            }

        return false;
        }

    /**
     * @return true iff the Expression is the constant value "false"
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
        if (listTypes.size() == 1)
            {
            return Collections.singletonList(generateArgument(code, listTypes.get(0), fTupleOk, errs));
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

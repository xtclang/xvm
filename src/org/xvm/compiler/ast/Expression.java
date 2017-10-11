package org.xvm.compiler.ast;


import java.util.Collections;
import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.TypeConstant;

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
     * @return true iff the Expression is a constant value
     */
    public boolean isConstant()
        {
        return false;
        }

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
                // TODO for things provably not assignable, is an @Auto method available?
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeOut, typeIn);
                }
            }

        return argOut;
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

    /**
     * @return true iff the expression is capable of completing normally
     */
    public boolean canComplete()
        {
        return true;
        }

    /**
     * @return nothing, because the method always throws
     * @throws UnsupportedOperationException this exception is always thrown by this method
     */
    protected UnsupportedOperationException notImplemented()
        {
        throw new UnsupportedOperationException("not implemented by: " + this.getClass().getSimpleName());
        }
    }

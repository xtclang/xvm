package org.xvm.compiler.ast;


import java.util.Arrays;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.I_Set;
import org.xvm.asm.op.Invoke_01;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.L_Set;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Move;
import org.xvm.asm.op.P_Set;

import org.xvm.compiler.Compiler;

import org.xvm.compiler.ast.Statement.Context;

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
     * Before generating the code for the method body, resolve names and verify definite assignment,
     * etc.
     *
     * @param ctx           the compilation context for the statement
     * @param typeRequired  the type that the expression is expected to be able to provide, or null
     *                      if no particular type is expected
     * @param errs          the error listener to log to
     *
     * @return true iff the compilation can proceed
     */
    protected boolean validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        throw notImplemented();
        }

    /**
     * Before generating the code for the method body, resolve names and verify definite assignment,
     * etc.
     * <p/>
     * This method should be overridden by any Expression type that expects to result in multiple
     * values.
     *
     * @param ctx            the compilation context for the statement
     * @param atypeRequired  an array of required types
     * @param errs           the error listener to log to
     *
     * @return true iff the compilation can proceed
     */
    protected boolean validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        boolean      fValid       = true;
        TypeConstant typeRequired = null;
        if (atypeRequired != null)
            {
            switch (atypeRequired.length)
                {
                case 0:
                    // no type expected
                    break;

                case 1:
                    // single type expected
                    typeRequired = atypeRequired[0];
                    break;

                default:
                    if (getValueCount() >= atypeRequired.length)
                        {
                        // since this method was not overridden, see if the expression can deliver
                        // the tuple form of the multiple requested types; if it's not supported,
                        // the error may get detected by either the validate or the generate phase
                        typeRequired = pool().ensureParameterizedTypeConstant(
                                pool().typeTuple(), atypeRequired);
                        }
                    else
                        {
                        // log the error, but allow validation to continue (with no particular
                        // expected type) so that we get as many errors exposed as possible in the
                        // validate phase
                        log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY,
                                atypeRequired.length, getValueCount());
                        fValid = false;
                        }
                    break;
                }
            }

        return fValid & validate(ctx, typeRequired, errs);
        }

    public boolean hasImplicitType()
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
            return type.getParamTypesArray();
            }
        }

    public boolean canInferFrom()

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
        return true;    // TODO shouldn't this default to false?
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
            if (isAssignableTo(type))
                {
                return validateAndConvertConstant(toConstant(), type, errs);
                }

            log(errs, Severity.ERROR, Compiler.WRONG_TYPE, type, toConstant().getType());
            }
        else
            {
            log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
            }

        return generateFakeConstant(type);
        }

    // TODO do we need generateConstants? (for "/%" operator for example)

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

        if (isConstant())
            {
            Constant constant = generateConstant(code, getImplicitType(), errs);
            assert constant != null; // the constant is ignored
            return;
            }

        if (isSingle())
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
        if (cLVals == 0)
            {
            generateVoid(code, errs);
            return;
            }

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
        TypeConstant typeImplicit = getImplicitType();
        return typeImplicit.isA(typeThat)
                || typeImplicit.ensureTypeInfo().findConversion(typeThat) != null;
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
            log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, typeOut, constIn.getValueString());
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

        TypeConstant typeIn = argIn.getRefType();
        if (!typeIn.equals(typeOut) && !typeIn.isA(typeOut))
            {
            MethodConstant constConv = typeIn.ensureTypeInfo().findConversion(typeOut);
            if (constConv == null)
                {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeOut, typeIn);
                }
            else
                {
                argOut = new Register(typeOut);
                code.add(new Invoke_01(argIn, constConv, argOut));
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
                    return getRegister().getRefType();

                case LocalProp:
                case TargetProp:
                case IndexedProp:
                case IndexedNProp:
                    return getProperty().getRefType();

                case Indexed:
                case IndexedN:
                    return getArray().getRefType();

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

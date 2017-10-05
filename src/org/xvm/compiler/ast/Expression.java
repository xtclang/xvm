package org.xvm.compiler.ast;


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
 *
 * @author cp 2017.03.28
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

    public Argument generateArgument(Code code, TypeConstant constType, boolean fTupleOk, ErrorListener errs)
        {
        throw notImplemented();
        }

    /**
     * Generate arguments of the specified types for this expression, or generate an error if that
     * is not possible.
     *
     * TODO need to pass in Scope but one that knows name->Register association?
     * TODO how to do captures?
     * TODO how to do definite assignment?
     * TODO a version of this for conditional? or just a boolean parameter that says this is asking for a conditional?
     *
     * @param listTypes
     * @param errs
     *
     * @return
     */
    public List<Argument> generateArguments(Code code, List<TypeConstant> listTypes, boolean fTupleOk, ErrorListener errs)
        {
        // TODO if it's just one type, then call the generateArgument() method
        // TODO if it's multiple types, then log a generic error "can't do multiple types"
        // TODO then override this by anything that could be a tuple
        throw notImplemented();
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
    private UnsupportedOperationException notImplemented()
        {
        throw new UnsupportedOperationException("not implemented by: " + this.getClass().getSimpleName());
        }
    }

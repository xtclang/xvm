package org.xvm.compiler.ast;


import java.util.List;
import java.util.function.Function;

import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.RegisterAST;

import org.xvm.asm.constants.TypeConstant;


/**
 * This is used to specify an argument ("?") for a function that indicates that the corresponding
 * parameter of the function should remain unbound.
 */
public class NonBindingExpression
        extends Expression {
    // ----- constructors --------------------------------------------------------------------------

    public NonBindingExpression(long lStartPos, long lEndPos, TypeExpression type) {
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        this.type      = type;
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the type expression of the unbound argument, iff one was specified; otherwise null
     */
    public TypeExpression getArgType() {
        return type;
    }

    @Override
    public long getStartPosition() {
        return lStartPos;
    }

    @Override
    public long getEndPosition() {
        return lEndPos;
    }

    @Override
    public <T> T forEachChild(Function<AstNode, T> visitor) {
        if (type != null) {
            return visitor.apply(type);
        }
        return null;
    }



    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx) {
        return type == null ? null : type.ensureTypeConstant(ctx, null);
    }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive, ErrorListener errs) {
        return type == null || typeRequired == null
                ? TypeFit.Fit
                : type.testFit(ctx, typeRequired.getType(), fExhaustive, errs);
    }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs) {
        TypeFit fit = TypeFit.Fit;

        if (typeRequired == null) {
            typeRequired = pool().typeObject();
        }

        TypeExpression exprOldType = this.type;
        TypeConstant   typeArg;
        if (exprOldType == null) {
            // non-binding expression without a specified type should fit anything
            typeArg = typeRequired;
        } else {
            TypeConstant   typeReqType = typeRequired.getType();
            TypeExpression exprNewType = (TypeExpression) exprOldType.validate(ctx, typeReqType, errs);
            if (exprNewType == null) {
                fit     = TypeFit.NoFit;
                typeArg = typeRequired;
            } else {
                this.type = exprNewType;
                typeArg   = exprNewType.ensureTypeConstant(ctx, errs).resolveAutoNarrowingBase();
            }
        }

        return finishValidation(ctx, typeRequired, typeArg, fit, null, errs);
    }

    @Override
    public boolean isNonBinding() {
        return true;
    }

    @Override
    public Argument generateArgument(Context ctx, Code code, boolean fLocalPropOk, ErrorListener errs) {
        // we use synthetic NonBindingExpressions to mark non-specified default arguments in a
        // presence of other named arguments (see AstNode.rearrangeNamedArgs);
        // note that "generateArgument" is never called when a method binding is performed
        return Register.DEFAULT;
    }

    @Override
    public ExprAST getExprAST(Context ctx) {
        return RegisterAST.defaultReg(getType());
    }

    @Override
    protected SideEffect mightAffect(Expression exprLeft, Argument arg) {
        return SideEffect.DefNo;
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return type == null
                ? "?"
                : "<" + type + ">?";
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }


    // ----- AstNode methods -----------------------------------------------------------------------

    @Override
    protected AstNode withChildren(List<AstNode> children) {
        var c = new ChildList(children);
        TypeExpression newType = type != null ? c.next() : null;
        return new NonBindingExpression(lStartPos, lEndPos, newType);
    }


    // ----- fields --------------------------------------------------------------------------------

    protected final long     lStartPos;
    protected final long     lEndPos;
    protected TypeExpression type;
}
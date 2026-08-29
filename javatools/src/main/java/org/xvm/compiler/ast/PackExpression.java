package org.xvm.compiler.ast;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.ast.UnaryOpExprAST.Operator;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var_T;


/**
 * A tuple packing expression. This packs the multiple values from the sub-expression into a tuple.
 */
public final class PackExpression
        extends UnaryOpExpression {
    // ----- constructors --------------------------------------------------------------------------

    private PackExpression(Expression expr) {
        super(expr, Operator.Pack);
    }

    public static PackExpression create(Expression expr, ErrorListener errs) {
        PackExpression exprPack = new PackExpression(expr);
        exprPack.adoptSyntheticExpression();

        ConstantPool pool = expr.pool();
        TypeConstant type = pool.ensureTupleType(expr.getTypes());
        Constant     val  = null;
        if (expr.isConstant()) {
            type = pool.ensureImmutableTypeConstant(type);
            val  = pool.ensureTupleConstant(type, expr.toConstants());
        }
        exprPack.finishValidation(null, null, type, expr.getTypeFit().addPack(), val, errs);
        return exprPack;
    }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx) {
        return getType();
    }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs) {
        return this;
    }

    @Override
    public void generateVoid(Context ctx, Code code, ErrorListener errs) {
        expr.generateVoid(ctx, code, errs);
    }

    @Override
    public Argument generateArgument(Context ctx, Code code, boolean fLocalPropOk, ErrorListener errs) {
        if (isConstant()) {
            return toConstant();
        }

        // generate the tuple fields
        Argument[] args = expr.generateArguments(ctx, code, fLocalPropOk, errs);
        assert args != null && args.length == 1;

        // generate the tuple value
        Register reg = code.createRegister(getType());
        code.add(new Var_T(reg, args));
        return reg;
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return "Packed:" + getUnderlyingExpression().toString();
    }


    // ----- fields --------------------------------------------------------------------------------


    // ----- copy support --------------------------------------------------------------------------

    /**
     * Shallow copy constructor for {@link AstNode#deepCopy()}: every declared field of this
     * tier is carried over verbatim (children are re-copied and re-adopted by the walk); the
     * field parity assertion in deepCopy() fails loudly if a field is added but not copied.
     */
    protected PackExpression(PackExpression that) {
        super(that);
    }

    @Override
    protected AstNode shallowCopy() {
        return new PackExpression(this);
    }
}

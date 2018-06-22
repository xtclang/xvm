package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.compiler.ast.Statement.Context;


/**
 * A ternary expression is the "a ? b : c" expression.
 */
public class TernaryExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TernaryExpression(Expression cond, Expression exprThen, Expression exprElse)
        {
        this.cond     = cond;
        this.exprThen = exprThen;
        this.exprElse = exprElse;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return cond.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return exprElse.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------


    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return CmpExpression.selectType(exprThen.getImplicitType(ctx),
                exprElse.getImplicitType(ctx), ErrorListener.BLACKHOLE);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit      fit         = TypeFit.Fit;
        ConstantPool pool        = pool();
        Expression   exprNewCond = cond.validate(ctx, pool.typeBoolean(), errs);
        // TODO see cmp
        return super.validate(ctx, typeRequired, errs);
        }

    @Override
    public boolean isAssignable()
        {
        return exprThen.isAssignable() && exprElse.isAssignable();
        }

    @Override
    public boolean isAborting()
        {
        return cond.isAborting() || exprThen.isAborting() || exprElse.isAborting();
        }

    @Override
    public boolean isShortCircuiting()
        {
        // REVIEW cond.isShortCircuiting() || exprThen.isShortCircuiting() || exprElse.isShortCircuiting();
        return false;
        }

    @Override
    public Argument generateArgument(Code code, boolean fLocalPropOk, boolean fUsedOnce,
            ErrorListener errs)
        {
        return super.generateArgument(code, fLocalPropOk, fUsedOnce, errs);
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        super.generateAssignment(code, LVal, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(cond)
          .append(" ? ")
          .append(exprThen)
          .append(" : ")
          .append(exprElse);

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression cond;
    protected Expression exprThen;
    protected Expression exprElse;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TernaryExpression.class, "cond", "exprThen", "exprElse");
    }

package org.xvm.compiler.ast;


import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;

import org.xvm.asm.op.New_N;
import org.xvm.asm.op.Throw;

import org.xvm.compiler.Token;


/**
 * A to-do expression raises an exception indicating missing functionality, with an optional
 * message. It can be used as an expression, or as a statement.
 */
public class TodoExpression
        extends ThrowExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TodoExpression(Token keyword, Expression message)
        {
        super(keyword, message);
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean validateThrow(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;
        if (expr != null)
            {
            Expression exprNew = expr.validate(ctx, pool().typeString(), errs);
            if (exprNew != expr)
                {
                fValid &= exprNew != null;
                if (exprNew != null)
                    {
                    expr = exprNew;
                    }
                }
            }

        ctx.setReachable(false);

        return fValid;
        }

    @Override
    public boolean isAssignable()
        {
        // sure, you can use this where an assignable is required
        return true;
        }

    @Override
    protected boolean allowsShortCircuit(AstNode nodeChild)
        {
        // the message of the T0D0 should not be able to prevent the exception from being thrown by
        // short-circuiting
        return false;
        }

    @Override
    protected void generateThrow(Context ctx, Code code, ErrorListener errs)
        {
        // throw new UnsupportedOperationException(message, null)
        ConstantPool   pool     = pool();
        ClassConstant  constEx  = pool.ensureEcstasyClassConstant("UnsupportedOperation");
        MethodConstant constNew = constEx.findConstructor(pool.typeString१(), pool.typeException१());
        Argument       argEx    = new Register(constEx.getType());
        Argument       argMsg   = expr == null
                ? pool.valNull()
                : expr.generateArgument(ctx, code, false, false, errs);

        code.add(new New_N(constNew, new Argument[] {argMsg, pool.valNull()}, argEx));
        code.add(new Throw(argEx));
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(keyword.getValueText())
          .append('(')
          .append(expr == null ? "\"\"" : expr)
          .append(')');
        return sb.toString();
        }
    }

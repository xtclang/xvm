package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.Register;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Label;
import org.xvm.asm.op.New_N;
import org.xvm.asm.op.Throw;
import org.xvm.asm.op.Var;

import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;


/**
 * A to-do expression raises an exception indicating missing functionality, with an optional
 * message. It can be used as an expression, or as a statement.
 */
public class TodoExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TodoExpression(Token keyword, Expression message)
        {
        this.keyword = keyword;
        this.message = message;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return message == null ? keyword.getEndPosition() : message.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType()
        {
        // it would be really nice to know what type they actually want, because we'd be glad to
        // pretend that we can provide one of those
        return pool().typeObject();
        }

    @Override
    public boolean isAssignable()
        {
        // sure, you can use this where an assignable is required
        return true;
        }

    @Override
    public boolean isCompletable()
        {
        return false;
        }

    @Override
    public boolean isConstant()
        {
        // sure, you can use this where a constant is required, i.e. a "case" statement
        return true;
        }

    @Override
    public Argument generateConstant(Code code, TypeConstant type, ErrorListener errs)
        {
        generateTodo(code, errs);
        return generateBlackHole(type);
        }

    @Override
    public Argument generateArgument(Code code, TypeConstant type, boolean fTupleOk, ErrorListener errs)
        {
        generateTodo(code, errs);
        return generateBlackHole(type);
        }

    @Override
    public Assignable generateAssignable(Code code, ErrorListener errs)
        {
        generateTodo(code, errs);
        return new Assignable();
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        generateTodo(code, errs);
        }

    @Override
    public void generateConditionalJump(Code code, Label label, boolean fWhenTrue, ErrorListener errs)
        {
        generateTodo(code, errs);
        }

    /**
     * Generate the actual code that a T0D0 expression evaluates to.
     *
     * @param code  the code block
     * @param errs  the error list to log any errors to
     */
    public void generateTodo(Code code, ErrorListener errs)
        {
        // throw new UnsupportedOperationException(message, null)
        ConstantPool   pool     = pool();
        ClassConstant  constEx  = pool.ensureEcstasyClassConstant("UnsupportedOperationException");
        MethodConstant constNew = pool.ensureEcstasyConstructor(constEx, pool.typeString१(), pool.typeException१());
        Argument       argEx    = new Register(constEx.asTypeConstant());
        Argument       argMsg   = message == null
                ? pool.valNull()
                : message.generateArgument(code, pool.typeString(), false, errs);

        code.add(new New_N(constNew, new Argument[] {argMsg, pool.valNull()}, argEx));
        code.add(new Throw(argEx));
        }

    @Override
    public boolean isAssignableTo(TypeConstant typeThat)
        {
        // sure, this expression can be one of those, whatever that is
        return true;
        }

    @Override
    public boolean isTypeBoolean()
        {
        // sure, whatever you want
        return true;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("TODO");
        if (message != null)
            {
            sb.append('(')
              .append(message)
              .append(')');
            }
        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token      keyword;
    protected Expression message;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TodoExpression.class, "message");
    }

package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Label;
import org.xvm.asm.op.New_N;
import org.xvm.asm.op.Throw;

import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;


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
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, TuplePref pref)
        {
        // sure, whatever you want
        return TypeFit.Fit;
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, TuplePref pref, ErrorListener errs)
        {
        boolean fValid = true;

        if (message != null)
            {
            Expression exprNew = message.validate(ctx, pool().typeString(), TuplePref.Rejected, errs);
            if (exprNew != message)
                {
                fValid &= exprNew != null;
                if (exprNew != null)
                    {
                    message = exprNew;
                    }
                }
            }

        // note: the required type is ignored, since this is a T0D0 ... but we pretend to provide
        // whatever they want
        if (atypeRequired == null)
            {
            // some arbitrary default ...
            atypeRequired = new TypeConstant[] {pool().typeBoolean()};
            }

        finishValidations(fValid ? TypeFit.Fit : TypeFit.NoFit, atypeRequired, null);
        return fValid
                ? this
                : null;
        }

    @Override
    public boolean isAssignable()
        {
        // sure, you can use this where an assignable is required
        return true;
        }

    @Override
    public boolean isAborting()
        {
        // a T0D0 cannot complete; it always throws
        return true;
        }

    @Override
    public boolean isConstant()
        {
        // sure, you can use this where a constant is required, although it does NOT have a compile
        // time constant available (i.e. the expression still requires code generation)
        return true;
        }

    @Override
    public Argument[] generateArguments(Code code, boolean fPack, ErrorListener errs)
        {
        generateTodo(code, errs);

        TypeConstant[] aTypes = getTypes();
        int            cArgs  = aTypes.length;
        Register[]     aArgs  = new Register[cArgs];
        for (int i = 0; i < cArgs; ++i)
            {
            aArgs[i] = generateBlackHole(aTypes[i]);
            }
        return aArgs;
        }

    @Override
    public Assignable[] generateAssignables(Code code, ErrorListener errs)
        {
        generateTodo(code, errs);

        int          cAsns = getValueCount();
        Assignable[] aAsns = new Assignable[cAsns];
        for (int i = 0; i < cAsns; ++i)
            {
            aAsns[i] = new Assignable();
            }
        return aAsns;
        }

    @Override
    public void generateAssignments(Code code, Assignable[] aLVal, ErrorListener errs)
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
    protected void generateTodo(Code code, ErrorListener errs)
        {
        // throw new UnsupportedOperationException(message, null)
        ConstantPool   pool     = pool();
        ClassConstant  constEx  = pool.ensureEcstasyClassConstant("UnsupportedOperationException");
        MethodConstant constNew = pool.ensureEcstasyConstructor(constEx, pool.typeString१(), pool.typeException१());
        Argument       argEx    = new Register(constEx.asTypeConstant());
        Argument       argMsg   = message == null
                ? pool.valNull()
                : message.generateArgument(code, false, errs);

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
        if (message == null)
            {
            sb.append("TODO");
            }
        else
            {
            sb.append(message);
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

    private TypeConstant m_type;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TodoExpression.class, "message");
    }

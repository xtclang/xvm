package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.Register;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.I_Get;
import org.xvm.compiler.Compiler;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * An array access expression is an expression followed by an array index expression.
 */
public class ArrayAccessExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ArrayAccessExpression(Expression expr, List<Expression> indexes, long lEndPos)
        {
        this.expr    = expr;
        this.indexes = indexes;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public TypeExpression toTypeExpression()
        {
        return new ArrayTypeExpression(expr.toTypeExpression(), indexes, lEndPos);
        }

    @Override
    public long getStartPosition()
        {
        return expr.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
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
        TypeConstant typeArray = expr.getImplicitType(ctx);
        if (typeArray == null)
            {
            return null;
            }

        TypeInfo            infoArray  = typeArray.ensureTypeInfo();
        int                 cIndexes   = indexes.size(); // REVIEW GG - what about supporting a tuple of indexes? (low priority)
        Set<MethodConstant> setMethods = infoArray.findOpMethods("getElement", "[]", cIndexes);
        for (MethodConstant idMethod : setMethods)
            {
            TypeConstant[] atypeRet = idMethod.getRawReturns();
            if (atypeRet.length > 0)
                {
                return atypeRet[0];
                }
            }

        return null;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit          fit            = TypeFit.Fit;
        Expression       exprArray      = expr;
        List<Expression> listIndexExprs = indexes;

        // first, validate the array expression; there is no way to say "required type is something
        // that has an operator for indexed look-up", since that could be Tuple, or List, or Array,
        // or UniformIndexed, or Matrix, or ...
        // REVIEW we could eventually explore possibilities starting with the implicit type and evaluating each @Auto conversion
        TypeConstant typeArray;
        Expression   exprArrayNew = exprArray.validate(ctx, null, errs);
        if (exprArrayNew == null)
            {
            // TODO
            exprArray.log(errs, Severity.ERROR, Compiler.MISSING_OPERATOR, "[]");
            fit       = TypeFit.NoFit;
            typeArray = pool().typeArray();
            }
        else if (exprArrayNew != exprArray)
            {
            // TODO
            }

        // the array expression must yield a type that has a get (and optional set) element op
        // :
        // - if it looks like the array expression will automatically yield a type that has a get
        //   element op, then validate with no required type
        // - otherwise, if the array expression automatically yields a type that has an @Auto
        //   conversion to a type that y
        // - otherwise, use UniformIndexed as the required type (not because it is necessarily
        //   correct, but because it is the only obvious default)
        if (getImplicitType(ctx) == null ? pool().typeUniformIndexed : null)
        // TODO validate expr, figure out get/set (make sure at most one of each, and at least one between them, and verify that types match)
        m_idGet = null;
        m_idSet = null;
        // TODO validate indexes based on type implied by get/set methods
        // TODO constant iff the expression and index(es?) are constant

        return super.validate(ctx, typeRequired, errs);
        }

    @Override
    public boolean isAssignable()
        {
        assert isValidated();
        return m_idSet != null;
        }

    @Override
    public Argument generateArgument(Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (hasConstantValue())
            {
            return toConstant();
            }

        // I_GET  rvalue-target, rvalue-ix, lvalue        ; T = T[ix]
        // M_GET  rvalue-target, #:(rvalue-ix), lvalue    ; T = T[ix*]
        Argument argArray  = expr.generateArgument(code, true, true, errs);
        Register regResult = createTempVar(code, getType(), true, errs).getRegister();

        List<Expression> listIndexExprs = indexes;
        int              cIndexes       = listIndexExprs.size();
        if (cIndexes == 1)
            {
            Argument argIndex = listIndexExprs.get(0).generateArgument(code, true, true, errs);
            code.add(new I_Get(argArray, argIndex, regResult));
            }
        else
            {
            Argument[] aIndexArgs = new Argument[cIndexes];
            for (int i = 0; i < cIndexes; ++i)
                {
                aIndexArgs[i] = listIndexExprs.get(i).generateArgument(code, true, true, errs);
                }
            // TODO code.add(new M_Get(argArray, aIndexArgs, regResult));
            throw notImplemented();
            }
        return regResult;
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        super.generateAssignment(code, LVal, errs);
        }

    @Override
    public Assignable generateAssignable(Code code, ErrorListener errs)
        {
        Argument         argArray  = expr.generateArgument(code, false, true, errs); // REVIEW local prop mode?
        List<Expression> listIndex = indexes;
        int              cIndexes  = listIndex.size();
        if (cIndexes == 1)
            {
            Argument argIndex = listIndex.get(0).generateArgument(code, false, true, errs); // REVIEW local prop mode?
            return new Assignable(argArray, argIndex);
            }
        else
            {
            Argument[] aArgIndexes = new Argument[cIndexes];
            for (int i = 0; i < cIndexes; ++i)
                {
                aArgIndexes[i] = listIndex.get(i).generateArgument(code, false, true, errs); // REVIEW local prop mode?
                }
            }
        new Assignable()
        return super.generateAssignable(code, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr)
          .append('[');

        boolean first = true;
        for (Expression index : indexes)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(index);
            }

          sb.append(']');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression       expr;
    protected List<Expression> indexes;
    protected long             lEndPos;

    private transient MethodConstant m_idGet;
    private transient MethodConstant m_idSet;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ArrayAccessExpression.class, "expr", "indexes");
    }

package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.I_Get;

import org.xvm.compiler.ast.Statement.Context;


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
        TypeFit      fit          = TypeFit.Fit;
        TypeConstant typeElement  = null;
        Expression   exprArray    = expr;
        int          cIndexes     = indexes.size();
        Expression[] aexprIndexes = indexes.toArray(new Expression[cIndexes]);

        // first, validate the array expression; there is no way to say "required type is something
        // that has an operator for indexed look-up", since that could be Tuple, or List, or Array,
        // or UniformIndexed, or Matrix, or ...
        // REVIEW we could eventually explore possibilities starting with the implicit type and evaluating each @Auto conversion
        TypeConstant   typeArray    = null;
        TypeConstant[] aIndexTypes  = null;
        Expression     exprArrayNew = exprArray.validate(ctx, null, errs);
        if (exprArrayNew == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            if (exprArrayNew != exprArray)
                {
                expr = exprArray = exprArrayNew;
                }

            // find the element access operator
            typeArray = exprArray.getType();
            MethodConstant idGet = findOpMethod(ctx, typeArray, "getElement", "[]", aexprIndexes,
                                                typeArray.isTuple() ? null : typeRequired, true, errs);
            if (idGet == null)
                {
                fit = TypeFit.NoFit;
                }
            else
                {
                aIndexTypes = idGet.getRawParams();
                typeElement = idGet.getRawReturns()[0];
                m_idGet     = idGet;
                }
            }

        // validate the index expression(s)
        for (int i = 0; i < cIndexes; ++i)
            {
            Expression exprOld = aexprIndexes[i];
            Expression exprNew = exprOld.validate(ctx, aIndexTypes == null ? null : aIndexTypes[i], errs);
            if (exprNew == null)
                {
                fit = TypeFit.NoFit;
                }
            else if (exprNew != exprOld)
                {
                aexprIndexes[i] = exprNew;
                indexes.set(i, exprNew);
                }
            }

        // bail out if the sub-expressions failed to validate and fit together correctly
        if (!fit.isFit())
            {
            if (typeElement == null)
                {
                typeElement = typeRequired == null ? pool().typeObject() : typeRequired;
                }
            return finishValidation(typeRequired, typeElement, fit, null, errs);
            }

        // check for a matching setter
        m_idSet = findOpMethod(ctx, typeArray, "setElement", "[]=", aexprIndexes,
                               typeArray.isTuple() ? null : typeRequired, false, errs);

        // the type of a tuple access expression is determinable iff the type is a tuple, it has
        // a known number of field types, and the index is a constant that specifies a field within
        // that domain of known field types
        if (typeArray.isTuple() && typeArray.isParamsSpecified() && aexprIndexes[0].hasConstantValue())
            {
            try
                {
                // lots of things can fail here, causing an exception, so if anything goes wrong,
                // we can correctly assume that we don't know more about the element type than the
                // "Object" that the Tuple interface suggests
                int i = ((IntConstant) aexprIndexes[0].toConstant()).getValue().getInt();
                typeElement = typeArray.getParamTypesArray()[i];
                }
            catch (RuntimeException e) {}
            }

        // the expression yields a constant value iff the sub-expressions are all constants and the
        // evaluation of the element access is legal
        Constant constVal = null;
        if (exprArray.hasConstantValue() && cIndexes == 1 && aexprIndexes[0].hasConstantValue())
            {
            // similar to above, lots of things can fail here, causing an exception, so if anything
            // goes wrong, we can correctly assume that we can't determine the compile-time constant
            // value of the expression
            // REVIEW GG we could issue a compile-time error though, e.g. index out of bounds!!!
            int i = ((IntConstant) aexprIndexes[0].toConstant()).getValue().getInt();
            constVal = ((ArrayConstant) exprArray.toConstant()).getValue()[i];
            }

        return finishValidation(typeRequired, typeElement, fit, constVal, errs);
        }

    @Override
    public boolean isAssignable()
        {
        assert isValidated();
        return m_idSet != null;
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (hasConstantValue())
            {
            LVal.assign(toConstant(), code, errs);
            }
        else
            {
            // I_GET  rvalue-target, rvalue-ix, lvalue        ; T = T[ix]
            // M_GET  rvalue-target, #:(rvalue-ix), lvalue    ; T = T[ix*]
            Register         regResult      = LVal.isLocalArgument()
                                            ? LVal.getRegister()
                                            : createTempVar(code, getType(), true, errs).getRegister();
            Argument         argArray       = expr.generateArgument(code, true, true, errs);
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
                throw notImplemented();
                // TODO code.add(new M_Get(argArray, aIndexArgs, regResult));
                }

            // if we created a local variable as a temporary for the result, we need to transfer
            // the result from that temporary to the specified LVal
            if (!LVal.isLocalArgument())
                {
                LVal.assign(regResult, code, errs);
                }
            }
        }

    @Override
    public Assignable generateAssignable(Code code, ErrorListener errs)
        {
        Argument         argArray  = expr.generateArgument(code, true, true, errs);
        List<Expression> listIndex = indexes;
        int              cIndexes  = listIndex.size();
        if (cIndexes == 1)
            {
            Argument argIndex = listIndex.get(0).generateArgument(code, true, true, errs);
            return new Assignable(argArray, argIndex);
            }
        else
            {
            Argument[] aArgIndexes = new Argument[cIndexes];
            for (int i = 0; i < cIndexes; ++i)
                {
                aArgIndexes[i] = listIndex.get(i).generateArgument(code, true, true, errs);
                }
            return new Assignable(argArray, aArgIndexes);
            }
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

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


/**
 * An array access expression is an expression followed by an array index expression.
 * <p/> REVIEW for multi-dimensional arrays, are they alternatives to the one-expression-per-dimension indexing?
 * <p/> REVIEW for a given dimensional index, would it be possible to specify more than one index? consider the example of a range
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


    // ----- LValue methods ------------------------------------------------------------------------

    @Override
    public boolean isLValueSyntax()
        {
        return true;
        }

    @Override
    public Expression getLValueExpression()
        {
        return this;
        }

    @Override
    public void updateLValueFromRValueTypes(TypeConstant[] aTypes)
        {
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        if (isValidated())
            {
            return getType();
            }

        TypeConstant typeArray = expr.getImplicitType(ctx);
        if (typeArray == null)
            {
            return null;
            }

        // tuples support array index operations, and are type safe, so we can figure out the type
        // of the resulting field IFF the tuple type specifies its field types AND the index into
        // the tuple is a constant value. however, since we haven't yet validated the expression,
        // the value of the index might not yet be determinable
        if (typeArray.isParamsSpecified() && typeArray.isTuple())
            {
            if (indexes.size() == 1)
                {
                Expression exprIndex = indexes.get(0);
                if (exprIndex.isConstant())
                    {
                    return determineTupleFieldType(typeArray, indexes.get(0).toConstant());
                    }

                if (exprIndex instanceof LiteralExpression)
                    {
                    Constant    constIndex = ((LiteralExpression) exprIndex).getLiteralConstant();
                    IntConstant intIndex   = null;
                    try
                        {
                        intIndex = (IntConstant) constIndex.convertTo(pool().typeInt());
                        }
                    catch (ArithmeticException e) {}
                    if (intIndex != null)
                        {
                        return determineTupleFieldType(typeArray, intIndex);
                        }
                    }
                }

            // if the type of the tuple field cannot be determined exactly, then we do not report
            // back the return type of the Tuple.getElement() method, since that is Object (and
            // would thus be misleading)
            return null;
            }

        TypeInfo            infoArray  = typeArray.ensureTypeInfo();
        int                 cIndexes   = indexes.size();
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
                                                typeArray.isTuple() ? null : typeRequired, errs);
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
        Expression[] aExprSet = new Expression[cIndexes + 1];
        System.arraycopy(aexprIndexes, 0, aExprSet, 0, cIndexes);
        // note: cannot fill in the expression that represents the value being set because it hasn't
        //       yet been validated (for example, if our parent is an AssignmentStatement)
        m_idSet = findOpMethod(ctx, typeArray, "setElement", "[]=", aExprSet, null, ErrorListener.BLACKHOLE);

        // the type of a tuple access expression is determinable iff the type is a tuple, it has
        // a known number of field types, and the index is a constant that specifies a field within
        // that domain of known field types
        if (typeArray.isTuple() && typeArray.isParamsSpecified() && aexprIndexes[0].isConstant())
            {
            TypeConstant typeField = determineTupleFieldType(typeArray, aexprIndexes[0].toConstant());
            if (typeField != null)
                {
                typeElement = typeField;
                }
            }

        // the expression yields a constant value iff the sub-expressions are all constants and the
        // evaluation of the element access is legal
        Constant constVal = null;
        if (exprArray.isConstant() && cIndexes == 1 && aexprIndexes[0].isConstant())
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

    /**
     * Determine the field type for a tuple field, if possible.
     *
     * @param typeTuple  the type of the tuple
     * @param index      a constant that holds the field index
     *
     * @return the field type, if it can be determined from the passed information, otherwise null
     */
    private TypeConstant determineTupleFieldType(TypeConstant typeTuple, Constant index)
        {
        int n;
        try
            {
            n = ((IntConstant) index.convertTo(pool().typeInt())).getIntValue().getInt();
            }
        catch (RuntimeException e)
            {
            return null;
            }

        TypeConstant[] atypeFields = typeTuple.getParamTypesArray();
        return n >= 0 && n < atypeFields.length
                ? atypeFields[n]
                : null;
        }

    @Override
    public boolean isCompletable()
        {
        for (Expression index : indexes)
            {
            if (!index.isCompletable())
                {
                return false;
                }
            }

        return true;
        }

    @Override
    public boolean isShortCircuiting()
        {
        for (Expression index : indexes)
            {
            if (index.isShortCircuiting())
                {
                return true;
                }
            }
        return expr.isShortCircuiting();
        }

    @Override
    public boolean isAssignable()
        {
        assert isValidated();
        return m_idSet != null;
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant())
            {
            LVal.assign(toConstant(), code, errs);
            }
        else
            {
            // I_GET  rvalue-target, rvalue-ix, lvalue        ; T = T[ix]
            // M_GET  rvalue-target, #:(rvalue-ix), lvalue    ; T = T[ix*]
            Argument         argResult      = LVal.isLocalArgument()
                                            ? LVal.getLocalArgument()
                                            : createTempVar(code, getType(), true, errs).getRegister();
            Argument         argArray       = expr.generateArgument(ctx, code, true, true, errs);
            List<Expression> listIndexExprs = indexes;
            int              cIndexes       = listIndexExprs.size();
            if (cIndexes == 1)
                {
                Argument argIndex = listIndexExprs.get(0).generateArgument(ctx, code, true, true, errs);
                code.add(new I_Get(argArray, argIndex, argResult));
                }
            else
                {
                Argument[] aIndexArgs = new Argument[cIndexes];
                for (int i = 0; i < cIndexes; ++i)
                    {
                    aIndexArgs[i] = listIndexExprs.get(i).generateArgument(ctx, code, true, true, errs);
                    }
                throw notImplemented();
                // TODO code.add(new M_Get(argArray, aIndexArgs, regResult));
                }

            // if we created a local variable as a temporary for the result, we need to transfer
            // the result from that temporary to the specified LVal
            if (!LVal.isLocalArgument())
                {
                LVal.assign(argResult, code, errs);
                }
            }
        }

    @Override
    public Assignable generateAssignable(Context ctx, Code code, ErrorListener errs)
        {
        Argument         argArray  = expr.generateArgument(ctx, code, true, true, errs);
        List<Expression> listIndex = indexes;
        int              cIndexes  = listIndex.size();
        if (cIndexes == 1)
            {
            Argument argIndex = listIndex.get(0).generateArgument(ctx, code, true, true, errs);
            return new Assignable(argArray, argIndex);
            }
        else
            {
            Argument[] aArgIndexes = new Argument[cIndexes];
            for (int i = 0; i < cIndexes; ++i)
                {
                aArgIndexes[i] = listIndex.get(i).generateArgument(ctx, code, true, true, errs);
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

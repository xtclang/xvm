package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.IntervalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.I_Get;

import org.xvm.compiler.Token.Id;


/**
 * An array access expression is an expression followed by an array index expression.
 *
 * <p/> TODO implement testFit() - the element type (that we can't guess in getImplicitType()) gets passed in!
 * <p/> TODO support tuple of indexes, particularly for multi-dimensional arrays
 * <p/> TODO for multi-dimensional arrays, support partial binding? @Op("[?,_]") / @Op("[_,?]") etc.
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

        TypeConstant typeArray = expr.isValidated() ? expr.getType() : expr.getImplicitType(ctx);
        if (typeArray == null)
            {
            // while we could test the expression to find out if it could be UniformIndexed, or
            // Array, or whatever, the information that we truly need is not the form (Array, Tuple,
            // etc.) but rather the content, e.g. the ElementType, and it is simply not conceivable
            // to test for that
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

                Constant constIndex = null;
                if (exprIndex instanceof LiteralExpression)
                    {
                    constIndex = fromLiteral(exprIndex);
                    }
                else if (exprIndex instanceof RelOpExpression)
                    {
                    RelOpExpression exprInterval = (RelOpExpression) exprIndex;
                    if (exprInterval.getOperator().getId() == Id.DOTDOT)
                        {
                        // need both the left & right expressions, and they must both be constant or
                        // of the LiteralExpression form
                        Constant constLo = fromLiteral(exprInterval.getExpression1());
                        Constant constHi = fromLiteral(exprInterval.getExpression2());
                        if (constLo != null && constHi != null)
                            {
                            constIndex = pool().ensureIntervalConstant(constLo, constHi);
                            }
                        }
                    }
                if (constIndex != null)
                    {
                    return determineTupleFieldType(typeArray, constIndex);
                    }
                }

            // if the type of the tuple field cannot be determined exactly, then we do not report
            // back the return type of the Tuple.getElement() method, since that is Object (and
            // would thus be misleading)
            return null;
            }

        // look for an operator that supports [x] indexing
        TypeInfo            infoArray  = typeArray.ensureTypeInfo();
        int                 cIndexes   = indexes.size();
        Set<MethodConstant> setMethods = infoArray.findOpMethods("getElement", "[]", cIndexes);
        for (MethodConstant idMethod : setMethods)
            {
            if (indexesFit(ctx, idMethod))
                {
                return idMethod.getRawReturns()[0];
                }
            }

        // we couldn't find a simple [x] form, but there could be an [x..y] form
        setMethods = infoArray.findOpMethods("slice", "[..]", cIndexes);
        for (MethodConstant idMethod : setMethods)
            {
            if (indexesFit(ctx, idMethod))
                {
                return idMethod.getRawReturns()[0];
                }
            }

        return null;
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired)
        {
        // check if we can figure out the answer on our own
        TypeConstant typeImplicit = getImplicitType(ctx);
        if (typeImplicit != null && typeRequired.isA(typeImplicit))
            {
            return TypeFit.Fit;
            }

        // test to see if the "array" expression is a tuple; this is easy to test for, just by
        // asking for a "naked" Tuple, but ignore anything that needs packing/unpacking/conversion
        ConstantPool pool      = pool();
        int          cIndexes  = indexes.size();
        TypeConstant typeArray = expr.getImplicitType(ctx);
        if (typeArray.isTuple() || typeArray == null && expr.testFit(ctx, pool.typeTuple()) == TypeFit.Fit)
            {
            TypeConstant typeTest = null;
            if (cIndexes == 1)
                {
                Expression   exprIndex  = indexes.get(0);
                if (exprIndex.isConstant())
                    {
                    typeTest = determineTupleTestType(indexes.get(0).toConstant(), typeRequired);
                    }
                else
                    {
                    Constant constIndex = null;
                    if (exprIndex instanceof LiteralExpression)
                        {
                        constIndex = fromLiteral(exprIndex);
                        }
                    else if (exprIndex instanceof RelOpExpression)
                        {
                        RelOpExpression exprInterval = (RelOpExpression) exprIndex;
                        if (exprInterval.getOperator().getId() == Id.DOTDOT)
                            {
                            // need both the left & right expressions, and they must both be
                            // constant or of the LiteralExpression form
                            Constant constLo = fromLiteral(exprInterval.getExpression1());
                            Constant constHi = fromLiteral(exprInterval.getExpression2());
                            if (constLo != null && constHi != null)
                                {
                                constIndex = pool().ensureIntervalConstant(constLo, constHi);
                                }
                            }
                        }
                    if (constIndex != null)
                        {
                        typeTest = determineTupleTestType(constIndex, typeRequired);
                        }
                    }
                }

            if (typeTest == null)
                {
                // the only thing that we can say for sure at this point is that the return
                // value is an object
                return pool.typeObject().isA(typeRequired) ? TypeFit.Fit : TypeFit.NoFit;
                }
            else
                {
                return expr.testFit(ctx, typeTest);
                }
            }

        if (typeArray != null)
            {
            typeArray.ensureTypeInfo().findOpMethods()
            }

        if (cIndexes == 1)
            {
            // test for sequence[] and sequence[..]
            Expression exprIndex = indexes.get(0);
            if (typeArray != null && typeArray.isA(pool.typeSequence() || expr.testFit(ctx, pool.typeSequence()) exprIndex.testFit(ctx, ))

            // test for UniformIndexed
            // TODO
            }
        else if (cIndexes == 2)
            {
            // test for matrix
            // TODO
            }

        if (typeArray != null)
            {
            }

        // we're going to have to probe for UniformIndexed, and Matrix
        // TODO

        return super.testFit(ctx, typeRequired);
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
        // or UniformIndexed, or Matrix, or any custom class; however, the most common case is
        // some sub-class of Sequence (such as Array, List, etc.), and we can test for that
        // TODO if (exprArray.testFit(ctx, ))
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


    // ----- compilation helpers -------------------------------------------------------------------

    /**
     * Helper to obtain an IntConstant from an expression.
     *
     * @param expr  an expression
     *
     * @return an IntConstant, or null if the expression did not yield an IntConstant
     */
    private IntConstant fromLiteral(Expression expr)
        {
        if (expr instanceof LiteralExpression)
            {
            Constant constLit = ((LiteralExpression) expr).getLiteralConstant();
            try
                {
                return (IntConstant) constLit.convertTo(pool().typeInt());
                }
            catch (ArithmeticException e) {}
            }

        return null;
        }

    /**
     * Helper to determine if the indexes of this expression can be used with the specified method.
     *
     * @param method  the method constant
     *
     * @return true iff the method takes a matching number of indexes of types matching those from
     *         {@link #indexes}, and returns a value
     */
    private boolean indexesFit(Context ctx, MethodConstant method)
        {
        if (method.getRawReturns().length > 0)
            {
            TypeConstant[] atypes = method.getRawParams();
            int            ctypes = atypes.length;
            if (ctypes == indexes.size())
                {
                for (int i = 0; i < ctypes; ++i)
                    {
                    if (!indexes.get(i).testFit(ctx, atypes[i]).isFit())
                        {
                        return false;
                        }
                    }
                return true;
                }
            }

        return false;
        }

    /**
     * Determine the field type for a tuple field, if possible.
     *
     * @param typeTuple  the type of the tuple
     * @param index      a constant that holds the field index (or a range, for a slice)
     *
     * @return the field type, if it can be determined from the passed information, otherwise null
     */
    private TypeConstant determineTupleFieldType(TypeConstant typeTuple, Constant index)
        {
        TypeConstant[] atypeFields = typeTuple.getParamTypesArray();
        int            cFields     = atypeFields.length;
        ConstantPool   pool        = pool();

        // type of "tup[n]" is a field type
        int     n   = 0;
        boolean fOk;
        try
            {
            n   = index.convertTo(pool.typeInt()).getIntValue().getInt();
            fOk = true;
            }
        catch (RuntimeException e)
            {
            fOk = false;
            }

        if (fOk)
            {
            return n >= 0 && n < cFields ? atypeFields[n] : null;
            }

        // type of "tup[lo..hi]" is a tuple of field types
        int nLo = 0;
        int nHi = 0;
        try
            {
            IntervalConstant interval = (IntervalConstant) index.convertTo(pool.typeInterval());
            nLo = interval.getFirst().convertTo(pool.typeInt()).getIntValue().getInt();
            nHi = interval.getLast().convertTo(pool.typeInt()).getIntValue().getInt();
            }
        catch (RuntimeException e2)
            {
            return null;
            }

        if (nLo < 0 || nLo >= cFields ||
            nHi < 0 || nHi >= cFields)
            {
            return null;
            }

        int            cSlice     = Math.abs(nHi - nLo) + 1;
        TypeConstant[] atypeSlice = new TypeConstant[cSlice];
        int            cStep      = nHi >= nLo ? +1 : -1;
        for (int iSrc = nLo, iDest = 0; iDest < cSlice; ++iDest, iSrc += cStep)
            {
            atypeSlice[iDest] = atypeFields[iSrc];
            }
        return pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeSlice);
        }

    /**
     * Determine the field type for a tuple field or slice.
     *
     * @param index         a constant that holds the field index (or a range, for a slice)
     * @param typeRequired  the field type(s) being tested for
     *
     * @return a tuple type with the test field(s), or null if the test type cannot be formed
     */
    private TypeConstant determineTupleTestType(Constant index, TypeConstant typeRequired)
        {
        ConstantPool pool = pool();

        int nLo, nHi;
        boolean fSlice;
        try
            {
            nLo = nHi = index.convertTo(pool.typeInt()).getIntValue().getInt();
            fSlice    = false;
            }
        catch (RuntimeException e)
            {
            // type of "tup[lo..hi]" is a tuple
            try
                {
                IntervalConstant interval = (IntervalConstant) index.convertTo(pool.typeInterval());
                nLo    = interval.getFirst().convertTo(pool.typeInt()).getIntValue().getInt();
                nHi    = interval.getLast().convertTo(pool.typeInt()).getIntValue().getInt();
                fSlice = true;
                }
            catch (RuntimeException e2)
                {
                return null;
                }
            }

        // note: 0xFF used as an *arbitrary* limit on tuple size here, just to avoid stupidity
        if (nLo < 0 || nHi < 0 || nLo > 0xFF || nHi > 0xFF || (fSlice && !typeRequired.isTuple()))
            {
            return null;
            }

        // create a nominally-sized array of field types that are all "Object", since Tuple<T1, Tn>
        // is by definition a Tuple<Object, Object>, which is in turn a Tuple<Object>
        int            cTestFields = Math.max(nLo, nHi) + 1;
        TypeConstant[] atypeFields = new TypeConstant[cTestFields];
        TypeConstant   typeObject  = pool.typeObject();
        for (int i = 0; i < cTestFields; ++i)
            {
            atypeFields[i] = typeObject;
            }

        if (fSlice)
            {
            TypeConstant[] atypeReq = typeRequired.getParamTypesArray();
            int            cReq     = atypeReq.length;
            if (cReq > (Math.abs(nHi - nLo) + 1))
                {
                return null;
                }

            int cStep = nHi >= nLo ? +1 : -1;
            for (int iSrc = 0, iDest = nLo; iSrc < cReq; ++iSrc, iDest += cStep)
                {
                atypeFields[iDest] = atypeReq[iSrc];
                }
            }
        else
            {
            atypeFields[nLo] = typeRequired;
            }

        return pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeFields);
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

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
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.I_Get;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token.Id;
import org.xvm.util.PackedInteger;
import org.xvm.util.Severity;


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
        if (typeArray.isTuple())
            {
            return typeArray.isParamsSpecified()
                    ? determineTupleFieldType(typeArray)
                    : null;
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
        if (typeArray != null && typeArray.isTuple()
                || typeArray == null && expr.testFit(ctx, pool.typeTuple()) == TypeFit.Fit)
            {
            TypeConstant typeTest = determineTupleTestType(typeRequired);
            return typeTest == null
                    // the only thing that we can say for sure at this point is that the return
                    // value is an object
                    ? pool.typeObject().isA(typeRequired) ? TypeFit.Fit : TypeFit.NoFit
                    : expr.testFit(ctx, typeTest);
            }
        // test for Sequence<T> (aka T...) and Array<T> (aka T[]) etc.
        else if (testType(ctx, expr, typeArray, pool.typeSequence()) && cIndexes == 1)
            {
            // array[index]
            if (indexes.get(0).testFit(ctx, pool.typeInt()).isFit())
                {
                return expr.testFit(ctx, pool.ensureParameterizedTypeConstant(
                        pool.typeSequence(), typeRequired));
                }

            // array[index..index]
            if (typeRequired.isA(pool.typeInterval()) && indexes.get(0).testFit(ctx,
                    pool.ensureParameterizedTypeConstant(pool.typeInterval(), pool.typeInt())).isFit())
                {
                // REVIEW this might not be quite right .. assemble the type and then find the result of the [..] and see if that isA(typeRequired)
                TypeConstant typeElement = typeRequired.getGenericParamType("ElementType");
                return typeElement == null
                        ? TypeFit.Fit
                        : expr.testFit(ctx, pool.ensureParameterizedTypeConstant(
                                pool.typeSequence(), typeElement));
                }
            }
        // test for UniformIndexed
        else if (testType(ctx, expr, typeArray, pool.typeIndexed()) && cIndexes == 1)
            {
            // figure out the index type
            TypeConstant typeIndex = typeArray == null
                    ? null
                    : typeArray.getGenericParamType("IndexType");
            if (typeIndex == null || !indexes.get(0).testFit(ctx, typeIndex).isFit())
                {
                typeIndex = indexes.get(0).getImplicitType(ctx);
                }

            if (typeIndex != null)
                {
                TypeFit fit = expr.testFit(ctx, pool.ensureParameterizedTypeConstant(
                        pool.typeIndexed(), typeIndex, typeRequired));
                if (fit.isFit())
                    {
                    return fit;
                    }
                }
            }
        // test for Matrix<T>
        else if (testType(ctx, expr, typeArray, pool.typeMatrix()) && cIndexes == 2)
            {
            Expression exprCol = indexes.get(0);
            Expression exprRow = indexes.get(1);

            // matrix[index,index]
            if (exprCol.testFit(ctx, pool.typeInt()).isFit() &&
                exprRow.testFit(ctx, pool.typeInt()).isFit())
                {
                return expr.testFit(ctx, pool.ensureParameterizedTypeConstant(
                        pool.typeMatrix(), typeRequired));
                }

            // matrix[index..index,index..index]
            TypeConstant typeInterval = pool.ensureParameterizedTypeConstant(pool.typeInterval(), pool.typeInt());
            if (typeRequired.isA(pool.typeMatrix()) && exprCol.testFit(ctx, typeInterval).isFit()
                                                    && exprRow.testFit(ctx, typeInterval).isFit())
                {
                // REVIEW same issue as array above
                TypeConstant typeElement = typeRequired.getGenericParamType("ElementType");
                return typeElement == null
                        ? TypeFit.Fit
                        : expr.testFit(ctx, pool.ensureParameterizedTypeConstant(
                                pool.typeMatrix(), typeElement));
                }

            // test for Matrix[_,?] or Matrix[?,_]
            // TODO
            }

        return TypeFit.NoFit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool pool         = pool();
        Expression   exprArray    = expr;
        TypeConstant typeArray    = exprArray.getImplicitType(ctx);
        TypeConstant typeArrayReq = null;
        int          cIndexes     = indexes.size();
        Expression[] aexprIndexes = indexes.toArray(new Expression[cIndexes]);

        // test if the access is against a tuple expression
        if (typeArray != null && typeArray.isTuple()
                || typeArray == null && exprArray.testFit(ctx, pool.typeTuple()) == TypeFit.Fit)
            {
            typeArrayReq = typeArray == null
                    ? pool.typeTuple()
                    : determineTupleTestType(typeRequired);
            }
        // test for Sequence<T> (aka T...) and Array<T> (aka T[]) etc.
        else if (testType(ctx, exprArray, typeArray, pool.typeSequence()) && cIndexes == 1)
            {
            typeArrayReq = pool.typeSequence();
            if (typeRequired != null)
                {
                // array[index]
                TypeConstant typeElement  = null;
                if (aexprIndexes[0].testFit(ctx, pool.typeInt()).isFit())
                    {
                    typeElement = typeRequired;
                    }
                // array[index..index]
                else if (typeRequired.isA(pool.typeInterval()) && aexprIndexes[0].testFit(ctx,
                        pool.ensureParameterizedTypeConstant(pool.typeInterval(), pool.typeInt())).isFit())
                    {
                    // REVIEW keep this in sync with testFit()
                    typeElement = typeRequired.getGenericParamType("ElementType");
                    }

                if (typeElement != null)
                    {
                    typeArrayReq = pool.ensureParameterizedTypeConstant(typeArrayReq, typeRequired);
                    }
                }
            }
        // test for UniformIndexed
        else if (testType(ctx, exprArray, typeArray, pool.typeIndexed()) && cIndexes == 1)
            {
            typeArrayReq = pool.typeIndexed();

            // figure out the index type
            TypeConstant typeIndex = typeArray == null
                    ? null
                    : typeArray.getGenericParamType("IndexType");
            if (typeIndex == null || !aexprIndexes[0].testFit(ctx, typeIndex).isFit())
                {
                typeIndex = aexprIndexes[0].getImplicitType(ctx);
                typeIndex = determineIndexType(ctx, exprArray, typeArray, aexprIndexes, typeIndex);
                }

            if (typeIndex != null)
                {
                TypeConstant typeElement = null;
                if (typeRequired != null)
                    {
                    TypeConstant typeTest = pool.ensureParameterizedTypeConstant(
                            pool.typeIndexed(), typeIndex, typeRequired);
                    if (exprArray.testFit(ctx, typeTest).isFit())
                        {
                        // we figured out what to ask for, including the index and element types
                        typeElement  = typeRequired;
                        typeArrayReq = typeTest;
                        }
                    }

                if (typeElement == null)
                    {
                    // we can only figure out the index type
                    typeArrayReq = pool.ensureParameterizedTypeConstant(typeArrayReq, typeIndex);
                    }
                }
            }
        // test for Matrix<T>
        else if (testType(ctx, expr, typeArray, pool.typeMatrix()) && cIndexes == 2)
            {
            typeArrayReq = pool.typeMatrix();
            if (typeRequired != null)
                {
                Expression exprCol = aexprIndexes[0];
                Expression exprRow = aexprIndexes[1];

                // matrix[index,index]
                TypeConstant typeElement  = null;
                if (exprCol.testFit(ctx, pool.typeInt()).isFit() && exprRow.testFit(ctx, pool.typeInt()).isFit())
                    {
                    typeElement = typeRequired;
                    }
                // array[index..index]
                else if (typeRequired.isA(pool.typeInterval()))
                    {
                    TypeConstant typeIntInterval = pool.ensureParameterizedTypeConstant(pool.typeInterval(), pool.typeInt());
                    if (exprCol.testFit(ctx, typeIntInterval).isFit() &&
                        exprRow.testFit(ctx, typeIntInterval).isFit())
                        {
                        // REVIEW keep this in sync with testFit()
                        typeElement = typeRequired.getGenericParamType("ElementType");
                        }
                    }

                if (typeElement != null)
                    {
                    typeArrayReq = pool.ensureParameterizedTypeConstant(typeArrayReq, typeRequired);
                    }

                // test for Matrix[_,?] or Matrix[?,_]
                // TODO
                }
            }

        // first, validate the array expression; there is no way to say "required type is something
        // that has an operator for indexed look-up", since that could be Tuple, or List, or Array,
        // or UniformIndexed, or Matrix, or any custom class; however, the most common case is
        // some sub-class of Sequence (such as Array, List, etc.), and we can test for that
        boolean        fValid       = true;
        TypeConstant[] aIndexTypes  = null;
        TypeConstant   typeElement  = null;
        Expression     exprArrayNew = exprArray.validate(ctx, typeArrayReq, errs);
        if (exprArrayNew == null)
            {
            fValid = false;
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
            boolean fSlice = false;
            if (idGet == null)
                {
                idGet  = findOpMethod(ctx, typeArray, "slice", "[..]", aexprIndexes,
                        typeArray.isTuple() ? null : typeRequired, errs);
                fSlice = true;
                }

            if (idGet == null)
                {
                fValid = false;
                }
            else
                {
                aIndexTypes = idGet.getRawParams();
                typeElement = idGet.getRawReturns()[0];
                m_idGet     = idGet;
                m_fSlice    = fSlice;
                }
            }

        // validate the index expression(s)
        for (int i = 0; i < cIndexes; ++i)
            {
            Expression exprOld = aexprIndexes[i];
            Expression exprNew = exprOld.validate(ctx, aIndexTypes == null ? null : aIndexTypes[i], errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else if (exprNew != exprOld)
                {
                aexprIndexes[i] = exprNew;
                indexes.set(i, exprNew);
                }
            }

        // bail out if the sub-expressions failed to validate and fit together correctly
        if (!fValid)
            {
            if (typeElement == null)
                {
                typeElement = typeRequired == null ? pool().typeObject() : typeRequired;
                }
            return finishValidation(typeRequired, typeElement, TypeFit.NoFit, null, errs);
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
            TypeConstant typeField = determineTupleFieldTypeFromIndex(typeArray,
                    aexprIndexes[0].toConstant());
            if (typeField != null)
                {
                typeElement = typeField;
                }
            }

        // the expression yields a constant value iff the sub-expressions are all constants and the
        // evaluation of the element access is legal
        Constant constVal = null;
        if (exprArray.isConstant() && aexprIndexes[0].isConstant() &&
                (cIndexes == 1 || aexprIndexes[1].isConstant()))
            {
            if (typeArray.isTuple() && cIndexes == 1)
                {
                if (m_fSlice)
                    {
                    // TODO
                    }
                else
                    {
                    // similar to above, lots of things can fail here, causing an exception, so if anything
                    // goes wrong, we can correctly assume that we can't determine the compile-time constant
                    // value of the expression
                    // REVIEW GG we could issue a compile-time error though, e.g. index out of bounds!!!
                    int i = ((IntConstant) aexprIndexes[0].toConstant()).getValue().getInt();
                    constVal = ((ArrayConstant) exprArray.toConstant()).getValue()[i];
                    // TODO
                    }
                }
            else if (typeArray.isA(pool.typeSequence()))
                {
                if (m_fSlice)
                    {
                    // TODO
                    }
                else
                    {
                    // TODO
                    }
                }
            else if (typeArray.isA(pool.typeIndexed()))
                {
                if (!m_fSlice && typeArray.isA(pool.typeMap()))
                    {
                    // TODO map lookup by key
                    }
                }
            else if (typeArray.isA(pool.typeMatrix()))
                {
                if (m_fSlice)
                    {
                    // TODO
                    }
                else
                    {
                    // TODO
                    }
                }
            }

        return finishValidation(typeRequired, typeElement, TypeFit.Fit, constVal, errs);
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
     * TODO
     *
     * @param typeTuple
     *
     * @return
     */
    private TypeConstant determineTupleFieldType(TypeConstant typeTuple)
        {
        return determineTupleInfoImpl(true, typeTuple);
        }

    /**
     * TODO
     *
     * @param typeRequired
     *
     * @return
     */
    private TypeConstant determineTupleTestType(TypeConstant typeRequired)
        {
        return determineTupleInfoImpl(false, typeRequired);
        }

    /**
     * Determine type information about a tuple.
     *
     * @param fCalcField  true if we're trying to calculate the type of a field, or false if we're
     *                    trying to guess the test type for the tuple itself
     * @param type        if we're calculating the type of a field, then this is the tuple type (as
     *                    much as has been figured out); otherwise, if we're trying to guess the
     *                    test type for the tuple, then this is the required type of the
     *                    ArrayAccessExpression
     *
     * @return the requested type, if it can be determined from the passed information, otherwise
     *         null
     */
    private TypeConstant determineTupleInfoImpl(boolean fCalcField, TypeConstant type)
        {
        TypeConstant typeTuple    = fCalcField ? type : null;
        TypeConstant typeRequired = fCalcField ? null : type;

        Constant constIndex = null;
        if (indexes.size() == 1)
            {
            Expression exprIndex = indexes.get(0);
            if (exprIndex.isConstant())
                {
                constIndex = indexes.get(0).toConstant();
                }
            else
                {
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
                }
            }

        if (constIndex == null)
            {
            return null;
            }

        return fCalcField
                ? determineTupleFieldTypeFromIndex(typeTuple, constIndex)
                : determineTupleTestTypeFromAccess(typeRequired, constIndex);
        }

    /**
     * Determine the type of the tuple to test for, based on a required type for the result of the
     * ArrayAccessExpression and the constant index(es) of the tuple being accessed.
     *
     * @param index         a constant that holds the field index (or a range, for a slice)
     * @param typeRequired  the field type(s) being tested for
     *
     * @return a tuple type with the test field(s) filled in based on the required information,
     *         or null if the test type cannot be formed
     */
    private TypeConstant determineTupleTestTypeFromAccess(TypeConstant typeRequired, Constant index)
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

    /**
     * Determine the field type for a tuple field, if possible.
     *
     * @param typeTuple  the type of the tuple
     * @param index      a constant that holds the field index (or a range, for a slice)
     *
     * @return the field type, if it can be determined from the passed information, otherwise null
     */
    private TypeConstant determineTupleFieldTypeFromIndex(TypeConstant typeTuple, Constant index)
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
     * TODO
     *
     * @param ctx
     * @param exprArray
     * @param typeArray
     * @param typeIndex
     *
     * @return
     */
    private TypeConstant determineIndexType(Context ctx, Expression exprArray, TypeConstant typeArray, Expression[] aexprIndexes, TypeConstant typeIndex)
        {
        ConstantPool pool = pool();
        if (typeIndex == null)
            {
            if (typeArray != null)
                {
                MethodConstant id = findOpMethod(ctx, typeArray, "getElement", "[]", aexprIndexes,
                        null, ErrorListener.BLACKHOLE);
                if (id != null && id.getRawReturns().length >= 1)
                    {
                    typeIndex = id.getRawReturns()[0];
                    if (!pool.typeObject().isA(typeIndex))
                        {
                        return typeIndex;
                        }
                    }
                }

            return null;
            }

        if (exprArray.testFit(ctx, pool.ensureParameterizedTypeConstant(pool.typeIndexed(), typeIndex)).isFit())
            {
            return typeIndex;
            }

        Set<MethodInfo> setInfos = typeIndex.ensureTypeInfo().getAutoMethodInfos();
        for (MethodInfo info : setInfos)
            {
            typeIndex = info.getSignature().getRawReturns()[0];
            if (exprArray.testFit(ctx, pool.ensureParameterizedTypeConstant(pool.typeIndexed(), typeIndex)).isFit())
                {
                return typeIndex;
                }
            }

        return null;
        }

    /**
     * Determine if the passed expression is of the type being tested for.
     *
     * @param ctx       the compiler context
     * @param expr      the expression to test
     * @param typeExpr  the (optional) type information that we already have for the expression
     * @param typeTest  the type to test for
     *
     * @return true if the type fits without conversion
     */
    private boolean testType(Context ctx, Expression expr, TypeConstant typeExpr, TypeConstant typeTest)
        {
        if (typeExpr != null && typeExpr.isA(typeTest))
            {
            return true;
            }

        return expr.testFit(ctx, typeTest) == TypeFit.Fit;
        }

    /**
     * Extract a constant element from a constant array.
     *
     * @param constArray  the array to extract an element from
     * @param constIndex  the index to extract
     * @param errs        the error list to log to
     *
     * @return the extracted element
     */
    private Constant evalConst(ArrayConstant constArray, IntConstant constIndex, ErrorListener errs)
        {
        Constant[]    aconstVals = constArray.getValue();
        int           cVals      = aconstVals.length;
        PackedInteger piIndex    = constIndex.getValue();
        if (piIndex.checkRange(0, cVals-1))
            {
            return aconstVals[piIndex.getInt()];
            }
        else
            {
            log(errs, Severity.ERROR, Compiler.INVALID_INDEX, piIndex, 0, cVals-1);
            return null;
            }
        }

    /**
     * Extract a constant slice from a constant array.
     *
     * @param constArray  the array to extract a slice from
     * @param constIndex  the interval of indexes to extract in the slice
     * @param typeResult  the type of the resulting constant slice
     * @param errs        the error list to log to
     *
     * @return the extracted slice
     */
    private Constant evalConst(ArrayConstant constArray, IntervalConstant constIndex, TypeConstant typeResult, ErrorListener errs)
        {
        Constant[]    aOldVals = constArray.getValue();
        int           cOldVals = aOldVals.length;
        PackedInteger piIndex1 = constIndex.getFirst().getIntValue();
        PackedInteger piIndex2 = constIndex.getLast() .getIntValue();
        if (piIndex1.checkRange(0, cOldVals-1) && piIndex2.checkRange(0, cOldVals-1))
            {
            int        nFirst   = piIndex1.getInt();
            int        nLast    = piIndex2.getInt();
            int        nStep    = nFirst <= nLast ? 1 : -1;
            int        cNewVals = Math.abs(nLast - nFirst) + 1;
            Constant[] aNewVals = new Constant[cNewVals];
            for (int iNew = 0, iOld = nFirst; iNew < cNewVals; ++iNew, iOld += nStep)
                {
                aNewVals[iNew] = aOldVals[iOld];
                }
            return pool().ensureArrayConstant(typeResult, aNewVals);
            }
        else
            {
            PackedInteger piIndex = piIndex1.checkRange(0, cOldVals-1) ? piIndex2 : piIndex1;
            log(errs, Severity.ERROR, Compiler.INVALID_INDEX, piIndex, 0, cOldVals - 1);
            return null;
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
    private transient boolean        m_fSlice;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ArrayAccessExpression.class, "expr", "indexes");
    }

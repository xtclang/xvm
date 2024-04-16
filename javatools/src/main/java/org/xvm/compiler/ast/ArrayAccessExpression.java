package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.ast.ArrayAccessExprAST;
import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.InvokeExprAST;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.RangeConstant;
import org.xvm.asm.constants.MapConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.I_Get;
import org.xvm.asm.op.Invoke_11;
import org.xvm.asm.op.Invoke_N1;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.PackedInteger;
import org.xvm.util.Severity;


/**
 * An array access expression is an expression followed by an array index expression.
 *
 * <p/> TODO support tuple of indexes, particularly for multi-dimensional arrays
 * <p/> TODO for multi-dimensional arrays, support partial binding? @Op("[?,_]") / @Op("[_,?]") etc.
 */
public class ArrayAccessExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ArrayAccessExpression(Expression expr, List<Expression> indexes, Token tokClose)
        {
        this.expr     = expr;
        this.indexes  = indexes;
        this.tokClose = tokClose;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public TypeExpression toTypeExpression()
        {
        assert tokClose.getId() == Id.R_SQUARE;
        TypeExpression exprType = new ArrayTypeExpression(
                expr.toTypeExpression(), indexes, tokClose.getEndPosition());
        exprType.setParent(getParent());
        return exprType;
        }

    @Override
    public long getStartPosition()
        {
        return expr.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return tokClose.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }

    /**
     * @return true iff the array indexes indicate a slice operation
     */
    boolean isSliceOp()
        {
        for (Expression index : indexes)
            {
            if (index instanceof RelOpExpression exprRel)
                {
                switch (exprRel.operator.getId())
                    {
                    case I_RANGE_I:
                    case E_RANGE_I:
                    case I_RANGE_E:
                    case E_RANGE_E:
                        return true;
                    }
                }
            }
        return false;
        }

    // ----- LValue methods ------------------------------------------------------------------------

    @Override
    public boolean isLValueSyntax()
        {
        return !isSliceOp();
        }

    @Override
    public Expression getLValueExpression()
        {
        assert isLValueSyntax();
        return this;
        }

    @Override
    public void updateLValueFromRValueTypes(Context ctx, Context.Branch branch, boolean fCond,
                                            TypeConstant[] aTypes)
        {
        }

    @Override
    public void resetLValueTypes(Context ctx)
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

        TypeConstant typeTarget = expr.isValidated() ? expr.getType() : expr.getImplicitType(ctx);
        if (typeTarget == null)
            {
            // while we could test the expression to find out if it could be UniformIndexed, or
            // Array, or whatever, the information that we truly need is not the form (Array, Tuple,
            // etc.) but rather the content, e.g. the Element, and it is simply not conceivable
            // to test for that
            return null;
            }

        // tuples support array index operations, and are type safe, so we can figure out the type
        // of the resulting field IFF the tuple type specifies its field types AND the index into
        // the tuple is a constant value. however, since we haven't yet validated the expression,
        // the value of the index might not yet be determinable
        if (typeTarget.isTuple())
            {
            return typeTarget.isParamsSpecified() || typeTarget.isFormalTypeSequence()
                    ? determineTupleResultType(typeTarget)
                    : null;
            }

        // otherwise, the type comes from the return value from the op that is likely to be used to
        // access the "array" target
        TypeInfo            infoTarget  = typeTarget.ensureTypeInfo();
        int                 cIndexes    = indexes.size();
        Set<MethodConstant> setMethods  = findPotentialOps(infoTarget, cIndexes);
        for (MethodConstant idMethod : setMethods)
            {
            if (setMethods.size() == 1 || indexesFit(ctx, idMethod))
                {
                TypeConstant[] atypeReturns = idMethod.getRawReturns();
                if (atypeReturns.length >= 1)
                    {
                    return idMethod.getRawReturns()[0];
                    }
                }
            }

        return null;
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive, ErrorListener errs)
        {
        TypeFit fit = super.testFit(ctx, typeRequired, fExhaustive, errs);
        if (fit.isFit())
            {
            return fit;
            }

        // check the implicit type of the "array" expression to see if it has any operators that
        // could fulfill the desired type
        ConstantPool pool       = pool();
        int          cIndexes   = indexes.size();
        TypeConstant typeTarget = expr.getImplicitType(ctx);
        if (typeTarget != null)
            {
            TypeInfo            infoTarget = typeTarget.ensureTypeInfo();
            Set<MethodConstant> setMethods = findPotentialOps(infoTarget, cIndexes);
            for (MethodConstant idMethod : setMethods)
                {
                TypeConstant[] atypeReturns = idMethod.getRawReturns();
                if (atypeReturns.length >= 1 && indexesFit(ctx, idMethod))
                    {
                    fit = calcFit(ctx, atypeReturns[0], typeRequired);
                    if (fit.isFit())
                        {
                        return fit;
                        }
                    }
                }
            }

        // at this point, either the "array" expression has no idea what type it will result in, or
        // its best guess doesn't have an array operator that results in the required type; "probe"
        // for some likely types that the array access operators are known to exist on
        switch (cIndexes)
            {
            default:
            case 0:
                break;

            case 1:
                // tuple is a special case, because its elements each have their own type, so
                // determining the type of an "array style access" against a tuple is a function of
                // being able to determine both the types of the tuple fields, and the index values
                // (which must be determinable at compile time in order to know which tuple elements
                // are being accessed)
                if (typeTarget != null && typeTarget.isTuple()
                        || expr.testFit(ctx, pool.typeTuple(), fExhaustive, null) == TypeFit.Fit)
                    {
                    TypeConstant typeTest = determineTupleTestType(typeRequired);
                    return typeTest == null
                        // the only thing that we can say for sure at this point is that the return
                        // value is an object
                        ? pool.typeObject().isA(typeRequired) ? TypeFit.Fit : TypeFit.NoFit
                        : expr.testFit(ctx, typeTest, fExhaustive, errs);
                    }

                // test for List<T>, Array<T> (aka T[]), etc.
                Expression exprTarget = expr;
                Expression exprIndex  = indexes.get(0);
                if (testType(ctx, exprTarget, typeTarget, pool.typeList()))
                    {
                    // List (Array, ...)       - if target is a List (no conv)
                    //     Int -> Element      - if index could be an Int, then test if target
                    //                           could be a UniformIndexed<Int, typeRequired>
                    //
                    //     Range<Int> -> El... -  if target could be typeRequired (slice returns
                    //                            this:type) and index could be a Range<Int>

                    // array[index]
                    if (!isSliceOp() && exprIndex.testFit(ctx, pool.typeInt64(), fExhaustive, null).isFit())
                        {
                        return exprTarget.testFit(ctx, pool.ensureParameterizedTypeConstant(
                                pool.typeList(), typeRequired), fExhaustive, errs);
                        }

                    // array[index..index] or array[index..index)
                    // REVIEW what if it is a Range<IntLiteral> or Range<UInt> ???
                    if (exprTarget.testFit(ctx, typeRequired, fExhaustive, errs) == TypeFit.Fit
                            && exprIndex.testFit(ctx, pool.ensureRangeType(pool.typeInt64()), fExhaustive, null).isFit())
                        {
                        return TypeFit.Fit;
                        }
                    }
                else // not a List, but might still be UniformIndexed and/or Sliceable
                    {
                    // test for UniformIndexed:
                    //  UniformIndexed       - test if target could be
                    //                         UniformIndexed<indexes[0], typeRequired>
                    //      Index -> Element - indexes[0] must have an implicit type
                    if (!isSliceOp() && testType(ctx, exprTarget, typeTarget, pool.typeIndexed()))
                        {
                        // figure out the index type
                        TypeConstant typeIndex = estimateIndexType(ctx, typeTarget, "Index", exprIndex);
                        if (typeIndex != null)
                            {
                            fit = exprTarget.testFit(ctx, pool.ensureParameterizedTypeConstant(
                                    pool.typeIndexed(), typeIndex, typeRequired), fExhaustive, errs);
                            if (fit.isFit())
                                {
                                return fit;
                                }
                            }
                        }

                    // test for Sliceable:
                    //  Sliceable             - test if target could be Sliceable without conv, and
                    //                          index could be a Range
                    //      Index -> Element  - probably does not matter what indexes are, since
                    //                          Sliceable ops return this:type
                    if (testType(ctx, exprTarget, typeTarget, pool.typeSliceable())
                            && exprIndex.testFit(ctx, pool.typeRange(), fExhaustive, null).isFit())
                        {
                        fit = exprTarget.testFit(ctx, typeRequired, fExhaustive, errs);
                        if (fit.isFit())
                            {
                            return fit;
                            }
                        }
                    }

                // test for Map:
                //  Map               - test if target is Map without conv, and could be a
                //                      Map<indexes[0], typeRequired>
                //      Key -> Value  - indexes[0] must have an implicit type, or typeTarget has to
                //                      have a known Value type parameter
                if (typeTarget != null && !isSliceOp()
                        && testType(ctx, exprTarget, typeTarget, pool.typeMap()))
                    {
                    TypeConstant typeValue = typeTarget.resolveGenericType("Value");
                    fit = calcFit(ctx, typeValue, typeRequired);
                    if (fit.isFit() && typeRequired.isA(pool.typeNullable()))
                        {
                        return fit;
                        }
                    }
                break;

            case 2:
                // test for Matrix<T>
                if (testType(ctx, expr, typeTarget, pool.typeMatrix()))
                    {
                    Expression exprCol = indexes.get(0);
                    Expression exprRow = indexes.get(1);

                    // matrix[index,index]
                    if (exprCol.testFit(ctx, pool.typeInt64(), fExhaustive, null).isFit() &&
                        exprRow.testFit(ctx, pool.typeInt64(), fExhaustive, null).isFit())
                        {
                        return expr.testFit(ctx, pool.ensureParameterizedTypeConstant(
                            pool.typeMatrix(), typeRequired), fExhaustive, errs);
                        }

                    // matrix[index..index,index..index]
                    TypeConstant typeInterval = pool.ensureRangeType(pool.typeInt64());
                    if (typeRequired.isA(pool.typeMatrix()) && exprCol.testFit(ctx, typeInterval, fExhaustive, null).isFit()
                        && exprRow.testFit(ctx, typeInterval, fExhaustive, null).isFit())
                        {
                        // REVIEW same issue as array above
                        TypeConstant typeElement = typeRequired.resolveGenericType("Element");
                        return typeElement == null
                            ? TypeFit.Fit
                            : expr.testFit(ctx, pool.ensureParameterizedTypeConstant(
                                pool.typeMatrix(), typeElement), fExhaustive, errs);
                        }

                    // test for Matrix[_,?] or Matrix[?,_]
                    // TODO
                    }
                break;
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
                || typeArray == null && exprArray.testFit(ctx, pool.typeTuple(), false, null) == TypeFit.Fit)
            {
            if (typeArray == null)
                {
                typeArrayReq = pool.typeTuple();
                }
            else
                {
                TypeConstant typeTupleTest = determineTupleTestType(typeRequired);
                if (exprArray.testFit(ctx, typeTupleTest, false, null).isFit())
                    {
                    typeArrayReq = typeTupleTest;
                    }
                }
            }
        // test for List<T> and Array<T> (aka T[]) etc.
        else if (testType(ctx, exprArray, typeArray, pool.typeList()) && cIndexes == 1)
            {
            typeArrayReq = pool.typeList();
            if (typeRequired != null)
                {
                // array[index]
                TypeConstant typeElement = null;
                if (!isSliceOp() && aexprIndexes[0].testFit(ctx, pool.typeInt64(), false, null).isFit())
                    {
                    typeElement = typeRequired;
                    }
                // array[index..index] or array[index..index)
                else if (typeRequired.isA(pool.typeList()) && aexprIndexes[0].testFit(ctx,
                        pool.ensureRangeType(pool.typeInt64()), false, null).isFit())
                    {
                    // REVIEW keep this in sync with testFit()
                    typeElement = typeRequired.resolveGenericType("Element");
                    }

                if (typeElement != null)
                    {
                    TypeConstant typeArrayTest =
                            pool.ensureParameterizedTypeConstant(typeArrayReq, typeElement);
                    if (exprArray.testFit(ctx, typeArrayTest, false, null).isFit())
                        {
                        typeArrayReq = typeArrayTest;
                        }
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
                    : typeArray.resolveGenericType("Index");
            if (typeIndex == null || !aexprIndexes[0].testFit(ctx, typeIndex, false, null).isFit())
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
                    if (exprArray.testFit(ctx, typeTest, false, null).isFit())
                        {
                        // we figured out what to ask for, including the index and element types
                        typeElement  = typeRequired;
                        typeArrayReq = typeTest;
                        }
                    }

                if (typeElement == null)
                    {
                    // we can only figure out the index type
                    TypeConstant typeArrayTest =
                            pool.ensureParameterizedTypeConstant(typeArrayReq, typeIndex);
                    if (exprArray.testFit(ctx, typeArrayTest, false, null).isFit())
                        {
                        typeArrayReq = typeArrayTest;
                        }
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
                if (exprCol.testFit(ctx, pool.typeInt64(), false, null).isFit() &&
                    exprRow.testFit(ctx, pool.typeInt64(), false, null).isFit())
                    {
                    typeElement = typeRequired;
                    }
                // array[index..index]
                else if (typeRequired.isA(pool.typeInterval()))
                    {
                    TypeConstant typeIntInterval = pool.ensureRangeType(pool.typeInt64());
                    if (exprCol.testFit(ctx, typeIntInterval, false, null).isFit() &&
                        exprRow.testFit(ctx, typeIntInterval, false, null).isFit())
                        {
                        // REVIEW keep this in sync with testFit()
                        typeElement = typeRequired.resolveGenericType("Element");
                        }
                    }

                if (typeElement != null)
                    {
                    TypeConstant typeArrayTest =
                            pool.ensureParameterizedTypeConstant(typeArrayReq, typeRequired);
                    if (exprArray.testFit(ctx, typeArrayTest, false, null).isFit())
                        {
                        typeArrayReq = typeArrayTest;
                        }
                    }

                // test for Matrix[_,?] or Matrix[?,_]
                // TODO
                }
            }

        // first, validate the array expression; there is no way to say "required type is something
        // that has an operator for indexed look-up", since that could be Tuple, or List, or Array,
        // or UniformIndexed, or Matrix, or any custom class; however, the most common case is
        // some sub-class of UniformIndexed (such as Array, etc.), and we can test for that
        boolean        fValid       = true;
        TypeConstant[] aIndexTypes  = null;
        TypeConstant   typeResult   = null;
        boolean        fSlice       = false;
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
            typeArray = exprArray.getType();

            // find the element access operator
            MethodConstant idGet = findArrayAccessor(ctx, typeArray, aexprIndexes, typeRequired);
            if (idGet == null)
                {
                log(errs, Severity.ERROR, Compiler.MISSING_OPERATOR_SIGNATURE,
                        "[]", typeArray.getValueString(), cIndexes);
                fValid = false;
                }
            else
                {
                TypeInfo   infoArray = typeArray.ensureTypeInfo();
                MethodInfo infoGet   = infoArray.getMethodById(idGet);
                assert infoGet != null;
                if (!infoGet.isOp("getElement", "[]", -1))
                    {
                    m_fSlice = fSlice = true;
                    }

                aIndexTypes = idGet.getRawParams();
                typeResult  = idGet.getRawReturns()[0].resolveAutoNarrowing(pool, true, typeArray, null);
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
            if (typeResult == null)
                {
                typeResult = typeRequired == null ? pool().typeObject() : typeRequired;
                }
            return finishValidation(ctx, typeRequired, typeResult, TypeFit.NoFit, null, errs);
            }

        if (!fSlice)
            {
            // check for a matching setter
            Expression[] aExprSet = new Expression[cIndexes + 1];
            System.arraycopy(aexprIndexes, 0, aExprSet, 0, cIndexes);
            // note: cannot fill in the expression that represents the value being set because it
            // hasn't yet been validated (for example, if our parent is an AssignmentStatement)
            m_idSet = findOpMethod(ctx, typeArray, "setElement", "[]=", aExprSet, null,
                    ErrorListener.BLACKHOLE);
            }

        // tuple is different from other container types (like array) in that every field in the
        // tuple may have a different type, so if we have enough compile-time information, we can
        // determine the compile-time type of the field being accessed
        if (typeArray.isTuple() && aexprIndexes[0].isConstant())
            {
            TypeConstant typeField = determineTupleResultType(typeArray);
            if (typeField != null)
                {
                typeResult = typeField;
                }
            }

        // the expression yields a constant value iff the sub-expressions are all constants and the
        // evaluation of the element access is legal
        Constant constVal = null;
        if (exprArray.isConstant() && aexprIndexes[0].isConstant() &&
                (cIndexes == 1 || aexprIndexes[1].isConstant()))
            {
            if ((typeArray.isTuple() || typeArray.isA(pool.typeList()) && cIndexes == 1))
                {
                if (fSlice)
                    {
                    constVal = evalConst((ArrayConstant) exprArray.toConstant(),
                            (RangeConstant) aexprIndexes[0].toConstant(), typeResult, errs);
                    }
                else
                    {
                    IntConstant constIndex = (IntConstant)
                            validateAndConvertConstant(aexprIndexes[0].toConstant(), pool.typeInt64(), errs);
                    constVal = evalConst((ArrayConstant) exprArray.toConstant(), constIndex, errs);
                    }
                }
            else if (typeArray.isA(pool.typeIndexed()))
                {
                if (!fSlice && typeArray.isA(pool.typeMap()))
                    {
                    MapConstant constMap = (MapConstant) exprArray.toConstant();
                    Constant    constKey = aexprIndexes[0].toConstant();
                                constVal = constMap.getValue().get(constKey);
                    }
                }
            else if (typeArray.isA(pool.typeMatrix()))
                {
                if (fSlice)
                    {
                    // TODO sub-matrix result (note: we still need a matrix syntax & constant)
                    }
                else
                    {
                    // TODO array result (note: we still need a matrix syntax & constant)
                    }
                }
            }

        return finishValidation(ctx, typeRequired, typeResult, TypeFit.Fit, constVal, errs);
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

        return expr.isCompletable();
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
    public boolean isAssignable(Context ctx)
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
                                            : createTempVar(code, getType(), true).getRegister();
            // the target argument for Invoke must not be on stack
            Argument         argArray       = expr.generateArgument(ctx, code, true, !m_fSlice, errs);
            List<Expression> listIndexExprs = indexes;
            int              cIndexes       = listIndexExprs.size();
            if (cIndexes == 1)
                {
                Argument argIndex = listIndexExprs.get(0).generateArgument(ctx, code, true, true, errs);
                if (m_fSlice)
                    {
                    code.add(new Invoke_11(argArray, m_idGet, argIndex, argResult));
                    }
                else
                    {
                    code.add(new I_Get(argArray, argIndex, argResult));
                    }
                }
            else
                {
                Argument[] aIndexArgs = new Argument[cIndexes];
                for (int i = 0; i < cIndexes; ++i)
                    {
                    Argument arg = listIndexExprs.get(i).generateArgument(ctx, code, true, true, errs);
                    aIndexArgs[i] = i == cIndexes-1
                            ? arg
                            : ensurePointInTime(code, arg);
                    }

                if (m_idGet == null)
                    {
                    // TODO code.add(new M_Get(argArray, aIndexArgs, regResult));
                    throw notImplemented();
                    }
                else
                    {
                    code.add(new Invoke_N1(argArray, m_idGet, aIndexArgs, argResult));
                    }
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
                Argument arg = listIndex.get(i).generateArgument(ctx, code, true, true, errs);
                aArgIndexes[i] = i == cIndexes-1
                        ? arg
                        : ensurePointInTime(code, arg);
                }
            return new Assignable(argArray, aArgIndexes);
            }
        }

    @Override
    public ExprAST getExprAST(Context ctx)
        {
        int cIndexes = indexes.size();
        if (cIndexes == 1)
            {
            ExprAST astArray = expr.getExprAST(ctx);
            ExprAST astArg   = indexes.get(0).getExprAST(ctx);
            return m_fSlice
                ? new InvokeExprAST(m_idGet, getTypes(), astArray, new ExprAST[] {astArg}, false)
                : new ArrayAccessExprAST(astArray, astArg);
            }

        if (m_idGet == null)
            {
            throw notImplemented();
            }
        else
            {
            ExprAST[] astArgs = new ExprAST[cIndexes];
            for (int i = 0; i < cIndexes; i++)
                {
                astArgs[i] = indexes.get(i).getExprAST(ctx);
                }
            return new InvokeExprAST(m_idGet, getTypes(), expr.getExprAST(ctx), astArgs, false);
            }
        }


    // ----- compilation helpers -------------------------------------------------------------------

    /**
     * Search through the target type for an accessor that matches the form and type of an array
     * access operator with the arguments that are available.
     *
     * @param ctx         the compiler context
     * @param typeTarget  the array type
     * @param aexprArgs   the arguments
     * @param typeReturn  the (optional) desired result type
     *
     * @return the selected method to use as the array accessor
     */
    private MethodConstant findArrayAccessor(
            Context       ctx,
            TypeConstant  typeTarget,
            Expression[]  aexprArgs,
            TypeConstant  typeReturn)
        {
        int            cArgs     = aexprArgs.length;
        TypeConstant[] atypeArgs = new TypeConstant[cArgs];
        for (int i = 0; i < cArgs; ++i)
            {
            Expression exprArg = aexprArgs[i];
            atypeArgs[i] = exprArg.isValidated()
                    ? exprArg.getType()
                    : exprArg.getImplicitType(ctx);
            }

        TypeInfo            infoTarget = typeTarget.ensureTypeInfo();
        boolean             fTuple     = typeTarget.isTuple();
        Set<MethodConstant> setMatch   = new HashSet<>();

        Set<MethodConstant> setAll = findPotentialOps(infoTarget, cArgs);
        NextOp: for (MethodConstant idMethod : setAll)
            {
            SignatureConstant sig = idMethod.getSignature();
            if (sig.containsAutoNarrowing(false))
                {
                sig = sig.resolveAutoNarrowing(pool(), typeTarget, null);
                }

            if (!fTuple && typeReturn != null && (sig.getRawReturns().length < 1
                    || !isAssignable(ctx, sig.getRawReturns()[0], typeReturn)))
                {
                continue;
                }

            // verify that there are enough parameters to receive the arguments
            TypeConstant[] atypeParams = sig.getRawParams();
            int            cParams     = atypeParams.length;
            if (cParams < cArgs)
                {
                continue;
                }

            // verify that any additional parameters have a default value
            MethodInfo info = infoTarget.getMethodById(idMethod);
            if (cParams > cArgs && (cParams - cArgs) <
                    info.getTopmostMethodStructure(infoTarget).getDefaultParamCount())
                {
                continue;
                }

            // verify the args each have a matching parameter
            for (int i = 0; i < cArgs; ++i)
                {
                TypeConstant typeParam = atypeParams[i];
                TypeConstant typeArg   = atypeArgs[i];
                if (typeArg == null || !isAssignable(ctx, typeArg, typeParam))
                    {
                    Expression exprArg = aexprArgs[i];
                    if (!exprArg.testFit(ctx, typeParam, false, null).isFit())
                        {
                        continue NextOp;
                        }
                    }
                }

            if (info.isCapped())
                {
                MethodInfo infoNarrowing = infoTarget.getNarrowingMethod(info);
                if (infoNarrowing.isOp())
                    {
                    setMatch.add(infoTarget.resolveMethodConstant(infoNarrowing));
                    continue;
                    }
                }

            setMatch.add(idMethod);
            }

        return switch (setMatch.size())
            {
            case 0  -> null;
            case 1  -> setMatch.iterator().next();
            default -> chooseBest(setMatch, typeTarget, ErrorListener.BLACKHOLE);
            };
        }

    private Set<MethodConstant> findPotentialOps(TypeInfo infoTarget, int cArgs)
        {
        Set<MethodConstant> setGet   = infoTarget.findOpMethods("getElement", "[]", cArgs);
        Set<MethodConstant> setSlice = infoTarget.findOpMethods("slice", "[..]", cArgs);

        if (setGet.isEmpty())
            {
            return setSlice;
            }

        if (setSlice.isEmpty())
            {
            return setGet;
            }

        HashSet<MethodConstant> setUnion = new HashSet<>();
        setUnion.addAll(setGet);
        setUnion.addAll(setSlice);
        return setUnion;
        }

    /**
     * Helper to find an op method.
     *
     * @param ctx          the compilation context
     * @param typeTarget   the type on which to search for the op
     * @param sMethodName  default name of the op method
     * @param sOp          the operator string
     * @param aexprArgs    the (optional) argument expressions (which may not yet be validated)
     * @param typeReturn   the (optional) return type from the op
     * @param errs         listener to log any errors to
     *
     * @return the MethodConstant for the desired op, or null if an exact match was not found,
     *         in which case an error is reported
     */
    private MethodConstant findOpMethod(
            Context       ctx,
            TypeConstant  typeTarget,
            String        sMethodName,
            String        sOp,
            Expression[]  aexprArgs,
            TypeConstant  typeReturn,
            ErrorListener errs)
        {
        assert sMethodName != null && !sMethodName.isEmpty();
        assert sOp         != null && !sOp.isEmpty();

        TypeInfo infoTarget = typeTarget.ensureTypeInfo(errs);
        int      cParams    = aexprArgs == null ? -1 : aexprArgs.length;
        Set<MethodConstant> setMethods = infoTarget.findOpMethods(sMethodName, sOp, cParams);
        if (setMethods.isEmpty())
            {
            if (cParams < 0 || infoTarget.findOpMethods(sMethodName, sOp, -1).isEmpty())
                {
                log(errs, Severity.ERROR, Compiler.MISSING_OPERATOR,
                        sOp, typeTarget.getValueString());
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.MISSING_OPERATOR_SIGNATURE,
                        sOp, typeTarget.getValueString(), cParams);
                }
            return null;
            }

        TypeConstant[] atypeParams = null;
        if (cParams > 0)
            {
            atypeParams = new TypeConstant[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                Expression exprParam = aexprArgs[i];
                if (exprParam != null)
                    {
                    atypeParams[i] = exprParam.isValidated()
                            ? exprParam.getType()
                            : exprParam.getImplicitType(ctx);
                    }
                }
            }

        MethodConstant idBest = null;
        NextOp: for (MethodConstant idOp : setMethods)
            {
            if (cParams > 0)
                {
                TypeConstant[] atypeOpParams = idOp.getRawParams();
                int            cOpParams     = atypeOpParams.length;
                if (cParams != cOpParams)
                    {
                    continue;
                    }

                for (int i = 0; i < cParams; ++i)
                    {
                    if (atypeParams[i] != null && !isAssignable(ctx, atypeParams[i], atypeOpParams[i]))
                        {
                        continue NextOp;
                        }
                    }
                }

            if (typeReturn != null)
                {
                TypeConstant[] atypeOpReturns = idOp.getRawReturns();
                if (atypeOpReturns.length == 0 || isAssignable(ctx, atypeOpReturns[0], typeReturn))
                    {
                    continue;
                    }
                }

            if (idBest == null)
                {
                idBest = idOp;
                }
            else
                {
                boolean fOldBetter = idOp.getSignature().isSubstitutableFor(idBest.getSignature(), typeTarget);
                boolean fNewBetter = idBest.getSignature().isSubstitutableFor(idOp.getSignature(), typeTarget);
                if (fOldBetter ^ fNewBetter)
                    {
                    if (fNewBetter)
                        {
                        idBest = idOp;
                        }
                    }
                else
                    {
                    // note: theoretically could still be one better than either of these two, but
                    // for now, just assume it's an error at this point
                    log(errs, Severity.ERROR, Compiler.AMBIGUOUS_OPERATOR_SIGNATURE,
                            sOp, typeTarget.getValueString());
                    return null;
                    }
                }
            }

        if (idBest == null)
            {
            log(errs, Severity.ERROR, Compiler.MISSING_OPERATOR_SIGNATURE,
                    sOp, typeTarget.getValueString(), cParams);
            }

        return idBest;
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
            int            cTypes = atypes.length;
            if (cTypes == indexes.size())
                {
                for (int i = 0; i < cTypes; ++i)
                    {
                    if (!indexes.get(i).testFit(ctx, atypes[i], false, null).isFit())
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
     *
     * @return the field type, if it can be determined from the passed information, otherwise null
     */
    private TypeConstant determineTupleResultType(TypeConstant typeTuple)
        {
        Constant constIndex = extractArrayIndex(indexes.get(0));
        if (typeTuple == null || constIndex == null)
            {
            return null;
            }

        ConstantPool pool = pool();

        if (typeTuple.isFormalTypeSequence())
            {
            return pool.typeType();
            }

        int nIndex;
        try
            {
            // TODO is not clean; replace with a format check
            Constant constInt = constIndex.convertTo(pool.typeInt64());
            nIndex = constInt == null ? -1 : constInt.getIntValue().getInt();
            }
        catch (RuntimeException e)
            {
            nIndex = -1;
            }

        List<TypeConstant> listFields = typeTuple.getTupleParamTypes();
        if (nIndex >= 0)
            {
            // type of "tup[n]" is a field type
            return nIndex >= 0 && nIndex < listFields.size() ? listFields.get(nIndex) : null;
            }

        // type of "tup[lo..hi]" is a tuple of field types
        RangeConstant range;
        int nLo;
        int nHi;
        try
            {
            range = (RangeConstant) constIndex.convertTo(pool.typeInterval());
            nLo = range.getFirst().convertTo(pool.typeInt64()).getIntValue().getInt();
            nHi = range.getLast().convertTo(pool.typeInt64()).getIntValue().getInt();
            }
        catch (RuntimeException e)
            {
            return null;
            }

        boolean fReverse = nLo > nHi;
        if (range.isFirstExcluded())
            {
            nLo = fReverse ?  nLo - 1 : nLo + 1;
            }
        if (range.isLastExcluded())
            {
            nHi = fReverse ?  nHi + 1 : nHi - 1;
            }

        int cFields = listFields.size();
        if (    nLo < 0 || nLo >= cFields ||
                nHi < 0 || nHi >= cFields   )
            {
            return null;
            }

        int            cSlice     = Math.max(0, fReverse ? nLo - nHi + 1 : nHi - nLo + 1);
        TypeConstant[] atypeSlice = new TypeConstant[cSlice];
        int            cStep      = fReverse ? -1 : +1;
        for (int iSrc = nLo, iDest = 0; iDest < cSlice; ++iDest, iSrc += cStep)
            {
            atypeSlice[iDest] = listFields.get(iSrc);
            }
        return pool.ensureTupleType(atypeSlice);
        }

    /**
     * Determine the type of the tuple to test for, based on a required type for the result of the
     * ArrayAccessExpression and the constant index(es) of the tuple being accessed.
     *
     * @param typeRequired  the field type(s) being tested for
     *
     * @return a tuple type with the test field(s) filled in based on the required information,
     *         or null if the test type cannot be formed
     */
    private TypeConstant determineTupleTestType(TypeConstant typeRequired)
        {
        Constant index = extractArrayIndex(indexes.get(0));
        if (typeRequired == null || index == null)
            {
            return null;
            }

        ConstantPool pool = pool();

        int nLo, nHi;
        boolean fSlice, fReverse;
        try
            {
            if (index instanceof RangeConstant interval)
                {
                // type of "tup[lo..hi]" is a tuple
                nLo      = interval.getEffectiveFirst().convertTo(pool.typeInt64()).getIntValue().getInt();
                nHi      = interval.getEffectiveLast().convertTo(pool.typeInt64()).getIntValue().getInt();
                fSlice   = true;
                fReverse = interval.isReverse();

                if (fReverse)
                    {
                    int nTemp = nLo;
                    nLo = nHi;
                    nHi = nTemp;
                    }

                assert nLo <= nHi;
                }
            else
                {
                nLo = nHi = index.convertTo(pool.typeInt64()).getIntValue().getInt();
                fSlice    = false;
                fReverse  = false;
                }
            }
        catch (RuntimeException e)
            {
            return null;
            }

        // note: 0xFF used as an *arbitrary* limit on tuple size here, just to avoid stupidity
        if (nLo < 0 || nHi < 0 || nLo > 0xFF || nHi > 0xFF || (fSlice && !typeRequired.isTuple()))
            {
            return null;
            }

        // create a nominally-sized array of field types that are all "Object", since Tuple<T1, Tn>
        // is by definition a Tuple<Object, Object>, which is in turn a Tuple<Object>
        TypeConstant[] atypeFields = new TypeConstant[nHi + 1];
        Arrays.fill(atypeFields, pool.typeObject());

        if (fSlice)
            {
            int cElements = nHi - nLo + 1;
            if (cElements <= 0)
                {
                return null;
                }

            TypeConstant[] atypeReq = typeRequired.getParamTypesArray();
            int            cReq     = atypeReq.length;
            if (cReq > cElements)
                {
                return null;
                }

            for (int iSrc = 0, iDest = fReverse ? nHi : nLo, cStep = fReverse ? -1 : +1;
                    iSrc < cReq; ++iSrc, iDest += cStep)
                {
                atypeFields[iDest] = atypeReq[iSrc];
                }
            }
        else if (typeRequired != null)
            {
            atypeFields[nLo] = typeRequired;
            }

        return pool.ensureTupleType(atypeFields);
        }

    /**
     * Determine a constant index from an expression.
     *
     * @param exprIndex  an expression
     *
     * @return the index, if the expression yields a constant value, otherwise null
     */
    private Constant extractArrayIndex(Expression exprIndex)
        {
        if (exprIndex.isConstant())
            {
            ConstantPool pool       = pool();
            Constant     constRaw   = exprIndex.toConstant();
            Constant     constIndex = convertConstant(constRaw, pool.typeInt64());
            return constIndex == null
                    ? convertConstant(constRaw, pool.ensureRangeType(pool.typeInt64()))
                    : constIndex;
            }

        if (exprIndex instanceof LiteralExpression)
            {
            return fromLiteral(exprIndex);
            }

        if (exprIndex instanceof RelOpExpression exprInterval)
            {
            boolean slice = false;
            boolean fExLo = false;
            boolean fExHi = false;

            switch (exprInterval.operator.getId())
                {
                case I_RANGE_I:
                    slice = true;
                    break;

                case E_RANGE_I:
                    slice = true;
                    fExLo = true;
                    break;

                case I_RANGE_E:
                    slice = true;
                    fExHi = true;
                    break;

                case E_RANGE_E:
                    slice = true;
                    fExLo = true;
                    fExHi = true;
                    break;
                }

            if (slice)
                {
                // need both the left & right expressions, and they must both be
                // constant or of the LiteralExpression form
                Constant constLo = fromLiteral(exprInterval.getExpression1());
                Constant constHi = fromLiteral(exprInterval.getExpression2());
                if (constLo != null && constHi != null)
                    {
                    return pool().ensureRangeConstant(constLo, fExLo, constHi, fExHi);
                    }
                }
            }

        return null;
        }

    /**
     * Helper to obtain an IntConstant from an expression.
     *
     * @param expr  an expression
     *
     * @return an IntConstant, or null if the expression did not yield an IntConstant
     */
    private IntConstant fromLiteral(Expression expr)
        {
        if (expr instanceof LiteralExpression exprLit)
            {
            Constant constLit = exprLit.getLiteralConstant();
            try
                {
                return (IntConstant) constLit.convertTo(pool().typeInt64());
                }
            catch (ArithmeticException ignore) {}
            }

        return null;
        }

    /**
     * Estimate what the index type must be for a given indexed type.
     *
     * @param ctx          the compiler context
     * @param typeTarget   the array-like indexed expression
     * @param sIndexParam  the name of the target's type parameter that specifies the index type
     * @param exprIndex    the index expression
     *
     * @return the estimated index type, or null
     */
    TypeConstant estimateIndexType(Context ctx, TypeConstant typeTarget, String sIndexParam,
                                   Expression exprIndex)
        {
        TypeConstant typeIndex = typeTarget == null
                ? null
                : typeTarget.resolveGenericType(sIndexParam);
        if (typeIndex == null || !exprIndex.testFit(ctx, typeIndex, false, null).isFit())
            {
            typeIndex = exprIndex.getImplicitType(ctx);
            }
        return typeIndex;
        }

    /**
     * For a UniformIndexed type, determine the type of the index.
     *
     * @param ctx           the compiler context
     * @param exprArray     the array expression
     * @param typeArray     the implicit type of the array expression
     * @param aexprIndexes  the index expression(s)
     * @param typeIndex     the type of the array's "Index" type parameter, or null if the type
     *                      parameter's type could not be determined
     *
     * @return the type to use for the index of the array
     */
    private TypeConstant determineIndexType(Context ctx, Expression exprArray,
            TypeConstant typeArray, Expression[] aexprIndexes, TypeConstant typeIndex)
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

        if (exprArray.testFit(ctx,
                pool.ensureParameterizedTypeConstant(pool.typeIndexed(), typeIndex), false, null).isFit())
            {
            return typeIndex;
            }

        Set<MethodInfo> setInfos = typeIndex.ensureTypeInfo().getAutoMethodInfos();
        for (MethodInfo info : setInfos)
            {
            typeIndex = info.getSignature().getRawReturns()[0];
            if (exprArray.testFit(ctx,
                    pool.ensureParameterizedTypeConstant(pool.typeIndexed(), typeIndex), false, null).isFit())
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

        return expr.testFit(ctx, typeTest, false, null) == TypeFit.Fit;
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
     * @param constIndex  the range of indexes to extract in the slice
     * @param typeResult  the type of the resulting constant slice
     * @param errs        the error list to log to
     *
     * @return the extracted slice
     */
    private Constant evalConst(ArrayConstant constArray, RangeConstant constIndex,
                               TypeConstant typeResult, ErrorListener errs)
        {
     // TODO
        Constant[]    aOldVals = constArray.getValue();
        int           cOldVals = aOldVals.length;
        PackedInteger piIndex1 = constIndex.getEffectiveFirst().getIntValue();
        PackedInteger piIndex2 = constIndex.getEffectiveLast() .getIntValue();
        if (piIndex1.checkRange(0, cOldVals - 1) && piIndex2.checkRange(0, cOldVals-1))
            {
            int        cNewVals = (int) constIndex.size();
            Constant[] aNewVals = new Constant[cNewVals];
            if (cNewVals > 0)
                {
                int nFirst = piIndex1.getInt();
                int nLast  = piIndex2.getInt();
                int nStep  = nFirst <= nLast ? 1 : -1;
                for (int iNew = 0, iOld = nFirst; iNew < cNewVals; ++iNew, iOld += nStep)
                    {
                    aNewVals[iNew] = aOldVals[iOld];
                    }
                }
            ConstantPool pool = pool();
            return typeResult.isTuple()
                    ? pool.ensureTupleConstant(typeResult, aNewVals)
                    : pool.ensureArrayConstant(typeResult, aNewVals);
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

        sb.append(tokClose.getId().TEXT);

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
    protected Token            tokClose;

    private transient MethodConstant m_idGet;
    private transient MethodConstant m_idSet;
    private transient boolean        m_fSlice;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ArrayAccessExpression.class, "expr", "indexes");
    }
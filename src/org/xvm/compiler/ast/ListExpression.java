package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var_S;

import org.xvm.compiler.ast.Statement.Context;


/**
 * A list expression is an expression containing some number (0 or more) expressions of some common
 * type.
 * <p/>
 * <pre>
 * ListLiteral
 *     "[" ExpressionList-opt "]"
 *     "Sequence:{" ExpressionList-opt "}"
 *     "List:{" ExpressionList-opt "}"
 *     "Array:{" ExpressionList-opt "}"
 * </pre>
 */
public class ListExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ListExpression(TypeExpression type, List<Expression> exprs, long lStartPos, long lEndPos)
        {
        this.type      = type;
        this.exprs     = exprs;
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public List<Expression> getExpressions()
        {
        return exprs;
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
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
        TypeConstant typeExplicit = type == null ? null : type.ensureTypeConstant();
        if (typeExplicit != null && typeExplicit.getGenericParamType("ElementType", true) != null)
            {
            return typeExplicit;
            }

        // see if there is an implicit element type
        TypeConstant typeArray = typeExplicit == null ? pool().typeArray() : typeExplicit;
        int cElements = exprs.size();
        if (cElements > 0)
            {
            TypeConstant[] aElementTypes = new TypeConstant[cElements];
            for (int i = 0; i < cElements; ++i)
                {
                aElementTypes[i] = exprs.get(i).getImplicitType(ctx);
                }
            TypeConstant typeElement = inferCommonType(aElementTypes);
            if (typeElement != null)
                {
                typeArray = pool().ensureParameterizedTypeConstant(typeArray, typeElement);
                }
            }

        return typeArray;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool     pool        = pool();
        TypeFit          fit         = TypeFit.Fit;
        List<Expression> listExprs   = exprs;
        int              cExprs      = listExprs.size();
        boolean          fConstant   = true;
        TypeConstant     typeActual  = pool.typeArray();
        TypeConstant     typeElement = null;

        if (typeRequired != null && typeRequired.isA(pool.typeSequence()))
            {
            // if there is a required element type, then we'll use that to force the expressions to
            // convert to that type if necessary
            typeElement = typeRequired.getGenericParamType("ElementType", true);
            }

        TypeExpression exprOldType = type;
        if (exprOldType != null)
            {
            TypeConstant   typeArrayType = pool.ensureParameterizedTypeConstant(pool.typeType(), pool.typeSequence());
            TypeExpression exprNewType   = (TypeExpression) exprOldType.validate(ctx, typeArrayType, errs);
            if (exprNewType == null)
                {
                fit       = TypeFit.NoFit;
                fConstant = false;
                }
            else
                {
                if (exprNewType != exprOldType)
                    {
                    type = exprNewType;
                    }
                typeActual = exprNewType.ensureTypeConstant();

                TypeConstant typeElementTemp = typeActual.getGenericParamType("ElementType", true);
                if (typeElementTemp != null)
                    {
                    typeElement = typeElementTemp;
                    }
                }
            }

        if (typeElement == null && cExprs > 0)
            {
            // try to determine the element type
            TypeConstant[] aElementTypes = new TypeConstant[cExprs];
            for (int i = 0; i < cExprs; ++i)
                {
                aElementTypes[i] = listExprs.get(i).getImplicitType(ctx);
                }
            typeElement = inferCommonType(aElementTypes);
            typeActual  = typeActual.adoptParameters(new TypeConstant[] {typeElement});
            }

        for (int i = 0; i < cExprs; ++i)
            {
            Expression exprOld = listExprs.get(i);
            Expression exprNew = exprOld.validate(ctx, typeElement, errs);
            if (exprNew == null)
                {
                fit       = TypeFit.NoFit;
                fConstant = false;
                }
            else
                {
                if (exprNew != exprOld)
                    {
                    listExprs.set(i, exprNew);
                    }
                fConstant &= exprNew.hasConstantValue();
                }
            }

        // build a constant if it's a known container type and all of the elements are constants
        Constant constVal = null;
        if (fConstant)
            {
            TypeConstant typeImpl = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                    typeElement == null ? pool.typeObject() : typeElement);
            assert typeImpl.isA(typeRequired); // typeActual is either Array<ElType> or List<ElType>

            Constant[] aconstVal = new Constant[cExprs];
            for (int i = 0; i < cExprs; ++i)
                {
                aconstVal[i] = listExprs.get(i).toConstant();
                }

            constVal = pool.ensureArrayConstant(typeImpl, aconstVal);
            }

        return finishValidation(typeRequired, typeActual, fit, constVal, errs);
        }

    /**
     * Determine if the passed array of types indicates a particular common type.
     *
     * @param aTypes  an array of types, which can be null and which can contain nulls
     *
     * @return the inferred common type (including potentially requiring conversion), or null if no
     *         common type can be determined
     */
    protected TypeConstant inferCommonType(TypeConstant[] aTypes)
        {
        if (aTypes == null || aTypes.length == 0)
            {
            return null;
            }

        TypeConstant typeCommon = aTypes[0];
        if (typeCommon == null)
            {
            return null;
            }

        boolean fConvApplied = false;
        boolean fImmutable   = typeCommon.isImmutable();
        for (int i = 1, c = aTypes.length; i < c; ++i)
            {
            TypeConstant type = aTypes[i];
            if (type == null)
                {
                return null;
                }

            if (!type.isA(typeCommon))
                {
                if (typeCommon.isA(type))
                    {
                    typeCommon = type;
                    fImmutable = fImmutable && type.isImmutable();
                    continue;
                    }

                if (type.getConverterTo(typeCommon) != null)
                    {
                    fConvApplied = true;
                    continue;
                    }

                if (!fConvApplied)
                    {
                    MethodConstant idConv = typeCommon.getConverterTo(type);
                    if (idConv != null)
                        {
                        fConvApplied = true;
                        typeCommon   = type;
                        fImmutable   = fImmutable && type.isImmutable();
                        continue;
                        }
                    }

                // no obvious common type
                return null;
                }
            }

        return fImmutable ? typeCommon.ensureImmutable() : typeCommon;
        }

    @Override
    public Argument generateArgument(Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (hasConstantValue())
            {
            return toConstant();
            }

        List<Expression> listExprs = exprs;
        int              cArgs     = listExprs.size();
        Argument[]       aArgs     = new Argument[cArgs];
        for (int i = 0; i < cArgs; ++i)
            {
            aArgs[i] = listExprs.get(i).generateArgument(code, false, true, errs);
            }
        code.add(new Var_S(getType(), aArgs));
        return code.lastRegister();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('[');

        boolean first = true;
        for (Expression expr : exprs)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(expr);
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

    protected TypeExpression   type;
    protected List<Expression> exprs;
    protected long             lStartPos;
    protected long             lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ListExpression.class, "type", "exprs");
    }

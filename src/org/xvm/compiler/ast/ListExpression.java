package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeCollector;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var_S;


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
        if (typeExplicit != null)
            {
            if (typeExplicit.containsUnresolved())
                {
                return null;
                }
            if (typeExplicit.getGenericParamType("ElementType") != null)
                {
                return typeExplicit;
                }
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
            TypeConstant typeElement = TypeCollector.inferFrom(aElementTypes, pool());
            if (typeElement != null)
                {
                typeArray = pool().ensureParameterizedTypeConstant(typeArray, typeElement);
                }
            }

        return typeArray;
        }

    @Override
    protected TypeFit calcFit(Context ctx, TypeConstant typeIn, TypeConstant typeOut)
        {
        ConstantPool pool = pool();

        if (type == null && typeOut != null && typeOut.isA(pool.typeSequence()))
            {
            // matching the logic in "validate": if there is a required element type,
            // we'll force the expressions to convert to that type if necessary
            typeIn = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                        typeOut.getGenericParamType("ElementType"));
            }
        return super.calcFit(ctx, typeIn, typeOut);
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

        if (typeRequired != null && typeRequired.isA(pool.typeSequence())
                                 && typeRequired.isParamsSpecified())
            {
            // if there is a required element type, then we'll use that to force the expressions to
            // convert to that type if necessary
            typeElement = typeRequired.getGenericParamType("ElementType");
            if (typeElement != null)
                {
                typeActual = pool.ensureParameterizedTypeConstant(typeActual, typeElement);
                }
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
                typeActual = exprNewType.ensureTypeConstant().resolveAutoNarrowing(pool, null);

                TypeConstant typeElementTemp = typeActual.getGenericParamType("ElementType");
                if (typeElementTemp == null)
                    {
                    if (typeElement != null)
                        {
                        typeActual = pool.ensureParameterizedTypeConstant(typeActual, typeElement);
                        }
                    }
                else
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
            typeElement = TypeCollector.inferFrom(aElementTypes, pool);
            if (typeElement != null)
                {
                typeActual = typeActual.adoptParameters(pool, new TypeConstant[] {typeElement});
                }
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
                fConstant &= exprNew.isConstant();
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

    @Override
    public boolean isCompletable()
        {
        for (Expression expr : exprs)
            {
            if (!expr.isCompletable())
                {
                return false;
                }
            }

        return true;
        }

    @Override
    public boolean isShortCircuiting()
        {
        for (Expression expr : exprs)
            {
            if (expr.isShortCircuiting())
                {
                return true;
                }
            }

        return false;
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (isConstant())
            {
            return toConstant();
            }

        List<Expression> listExprs = exprs;
        int              cArgs     = listExprs.size();
        Argument[]       aArgs     = new Argument[cArgs];
        for (int i = 0; i < cArgs; ++i)
            {
            aArgs[i] = listExprs.get(i).generateArgument(ctx, code, false, true, errs);
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

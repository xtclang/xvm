package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.IntersectionTypeConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeCollector;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var_S;
import org.xvm.asm.op.Var_SN;

import org.xvm.compiler.Compiler;

import org.xvm.util.ListSet;
import org.xvm.util.Severity;


/**
 * A list expression is an expression containing some number (0 or more) expressions of some common
 * type.
 * <p/>
 * <pre>
 * ListLiteral
 *     "[" ExpressionList-opt "]"
 *     "Collection:[" ExpressionList-opt "]"
 *     "List:[" ExpressionList-opt "]"
 *     "Array:[" ExpressionList-opt "]"
 *     "Set:[" ExpressionList-opt "]"
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
        TypeConstant typeExplicit = type == null ? null : type.ensureTypeConstant(ctx);
        if (typeExplicit != null)
            {
            if (typeExplicit.containsUnresolved())
                {
                return null;
                }
            if (typeExplicit.resolveGenericType("Element") != null)
                {
                return typeExplicit;
                }
            }

        // see if there is an implicit element type
        TypeConstant typeArray   = typeExplicit == null ? pool().typeArray() : typeExplicit;
        TypeConstant typeElement = getImplicitElementType(ctx);
        if (typeElement != null)
            {
            typeArray = pool().ensureParameterizedTypeConstant(typeArray, typeElement);
            }
        return typeArray;
        }

    private TypeConstant getImplicitElementType(Context ctx)
        {
        int cElements = exprs.size();
        if (cElements > 0)
            {
            TypeConstant[] aElementTypes = new TypeConstant[cElements];
            for (int i = 0; i < cElements; ++i)
                {
                aElementTypes[i] = exprs.get(i).getImplicitType(ctx);
                }
            return TypeCollector.inferFrom(aElementTypes, pool());
            }
        return null;
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        int cElements = exprs.size();
        if (cElements > 0 && typeRequired != null && typeRequired.isA(pool().typeCollection()))
            {
            TypeConstant typeElement = typeRequired.resolveGenericType("Element");
            TypeFit      fit         = TypeFit.Fit;
            for (int i = 0; i < cElements; ++i)
                {
                fit = fit.combineWith(exprs.get(i).testFit(ctx, typeElement, errs));
                if (!fit.isFit())
                    {
                    break;
                    }
                }
            return fit;
            }
        return super.testFit(ctx, typeRequired, errs);
        }

    @Override
    protected TypeFit calcFit(Context ctx, TypeConstant typeIn, TypeConstant typeOut)
        {
        if (exprs.isEmpty())
            {
            // an empty list fits any type
            return TypeFit.Fit;
            }

        ConstantPool pool = pool();
        if (typeOut != null && typeOut.isA(pool.typeCollection()) &&
            typeIn  != null && typeIn .isA(pool.typeCollection()))
            {
            typeOut = typeOut.resolveGenericType("Element");
            typeIn  = typeIn .resolveGenericType("Element");
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
        TypeConstant     typeElement = null;

        if (typeRequired != null)
            {
            typeElement = calculateElementType(typeRequired);
            }

        if (typeElement == null || typeElement.equals(pool.typeObject()))
            {
            typeElement = getImplicitElementType(ctx);
            }

        if (typeElement == null)
            {
            typeElement = pool.typeObject();
            }

        TypeConstant typeActual;
        boolean      fSet;
        if (typeRequired != null && typeRequired.isA(pool.typeSet()))
            {
            typeActual = pool.typeSet();
            fSet       = true;
            }
        else
            {
            typeActual = pool.typeArray();
            fSet       = false;
            }

        TypeExpression exprTypeOld = type;
        if (exprTypeOld != null)
            {
            TypeConstant   typeSeqType = pool.typeCollection().getType();
            TypeExpression exprTypeNew = (TypeExpression) exprTypeOld.validate(ctx, typeSeqType, errs);
            if (exprTypeNew == null)
                {
                fit       = TypeFit.NoFit;
                fConstant = false;
                }
            else
                {
                if (exprTypeNew != exprTypeOld)
                    {
                    type = exprTypeNew;
                    }

                typeActual = exprTypeNew.ensureTypeConstant(ctx).resolveAutoNarrowingBase();

                TypeConstant typeElementNew = typeActual.resolveGenericType("Element");
                if (typeElementNew != null)
                    {
                    typeElement = typeElementNew;
                    }
                }
            }

        typeActual = pool.ensureParameterizedTypeConstant(typeActual, typeElement);
        if (cExprs > 0)
            {
            ctx = ctx.enterList();
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
            ctx = ctx.exit();
            }

        // build a constant if it's a known container type and all of the elements are constants
        Constant constVal = null;
        if (fConstant)
            {
            TypeConstant typeImpl = pool.ensureImmutableTypeConstant(
                    pool.ensureParameterizedTypeConstant(
                        fSet ? pool.typeSet() : pool.typeArray(),
                        typeElement == null ? pool.typeObject() : typeElement));
            if (typeRequired == null || typeImpl.isA(typeRequired)) // [Array | List | Set]<Element>
                {
                if (fSet)
                    {
                    ListSet<Constant> listVal = new ListSet<>(cExprs);
                    for (int i = 0; i < cExprs; ++i)
                        {
                        Constant constEl = listExprs.get(i).toConstant();
                        if (!listVal.add(constEl))
                            {
                            log(errs, Severity.ERROR, Compiler.SET_VALUES_DUPLICATE,
                                    constEl.getValueString());
                            }
                        }

                    constVal = pool.ensureSetConstant(typeImpl, listVal.toArray(Constant.NO_CONSTS));
                    }
                else
                    {
                    Constant[] aconstVal = new Constant[cExprs];
                    for (int i = 0; i < cExprs; ++i)
                        {
                        aconstVal[i] = listExprs.get(i).toConstant();
                        }
                    constVal = pool.ensureArrayConstant(typeImpl, aconstVal);
                    }
                }
            }

        return finishValidation(ctx, typeRequired, typeActual, fit, constVal, errs);
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
    public boolean supportsCompactInit(VariableDeclarationStatement lvalue)
        {
        // there may be not enough information in the lvalue type to use the VAR_SN op,
        // for example "Object list = [x, y];"
        return lvalue.getRegister().getType().resolveGenericType("Element") != null;
        }

    @Override
    public void generateCompactInit(
            Context ctx, Code code, VariableDeclarationStatement lvalue, ErrorListener errs)
        {
        if (isConstant())
            {
            super.generateCompactInit(ctx, code, lvalue, errs);
            }
        else
            {
            StringConstant idName = pool().ensureStringConstant(lvalue.getName());

            code.add(new Var_SN(lvalue.getRegister(), idName, collectArguments(ctx, code, errs)));
            }
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (isConstant())
            {
            return toConstant();
            }

        code.add(new Var_S(getType(), collectArguments(ctx, code, errs)));
        return code.lastRegister();
        }

    /**
     * Helper method to calculate the "Element" type for the specified required type.
     */
    private TypeConstant calculateElementType(TypeConstant typeRequired)
        {
        TypeConstant typeElement = typeRequired.resolveGenericType("Element");
        if (typeElement == null && typeRequired instanceof IntersectionTypeConstant)
            {
            // try to calculate an element type that would probably accommodate the required type
            Set<TypeConstant> setSeqType = ((IntersectionTypeConstant) typeRequired).
                collectMatching(pool().typeList(), null);
            if (!setSeqType.isEmpty())
                {
                for (TypeConstant typeSeq : setSeqType)
                    {
                    TypeConstant typeGuess = typeSeq.resolveGenericType("Element");
                    if (typeGuess != null)
                        {
                        if (typeElement == null)
                            {
                            typeElement = typeGuess;
                            }
                        else if (typeElement.isA(typeGuess))
                            {
                            typeElement = typeGuess;
                            }
                        else if (!typeGuess.isA(typeElement))
                            {
                            // typeGuess is incompatible with typeElement
                            return null;
                            }
                        }
                    }
                }
            }
        return typeElement;
        }

    /**
     * Helper method to generate an array of arguments.
     */
    private Argument[] collectArguments(Context ctx, Code code, ErrorListener errs)
        {
        List<Expression> listExprs = exprs;
        int              cArgs     = listExprs.size();
        Argument[]       aArgs     = new Argument[cArgs];

        for (int i = 0; i < cArgs; ++i)
            {
            aArgs[i] = listExprs.get(i).generateArgument(ctx, code, true, false, errs);
            }
        return aArgs;
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

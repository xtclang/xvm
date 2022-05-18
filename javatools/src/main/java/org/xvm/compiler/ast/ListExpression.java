package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
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
 *     "Collection:" "[" ExpressionList-opt "]"
 *     "List:" "[" ExpressionList-opt "]"
 *     "Array:" "[" ExpressionList-opt "]"
 *     "Set:" "[" ExpressionList-opt "]"
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
        TypeConstant typeBase    = getBaseType(ctx, null);
        TypeConstant typeElement = getImplicitElementType(ctx);
        if (typeElement != null)
            {
            typeBase = pool().ensureParameterizedTypeConstant(typeBase, typeElement);
            }
        return typeBase;
        }

    /**
     * @return the base type for this ListExpression, which is Array or Set
     */
    private TypeConstant getBaseType(Context ctx, TypeConstant typeRequired)
        {
        if (type == null)
            {
            ConstantPool pool = pool();
            if (typeRequired != null && typeRequired.isSingleUnderlyingClass(true))
                {
                TypeConstant typeBase = typeRequired.getSingleUnderlyingClass(true).getType();
                if (!pool.typeArray().isA(typeBase) && pool.typeSet().isA(typeBase))
                    {
                    return pool.typeSet();
                    }
                }

            return pool.typeArray();
            }

        return type.ensureTypeConstant(ctx, null);
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
        ConstantPool pool = pool();

        // an empty map looks like an empty list to the parser
        if (typeRequired.isA(pool.typeMap()) && exprs.isEmpty())
            {
            return TypeFit.Fit;
            }

        InferFromRequired:
        if (typeRequired != null)
            {
            TypeConstant typeElement = getImplicitElementType(ctx);
            if (typeElement != null &&
                    (pool.ensureArrayType(typeElement).isA(typeRequired) ||
                     pool.ensureSetType(typeElement).isA(typeRequired)))
                {
                return TypeFit.Fit;
                }

            typeElement = resolveElementType(typeRequired);
            if (typeElement == null)
                {
                break InferFromRequired;
                }

            TypeConstant typeBase   = getBaseType(ctx, typeRequired);
            TypeConstant typeTarget = typeBase.isParamsSpecified()
                    ? typeBase
                    : pool.ensureParameterizedTypeConstant(typeBase, typeElement);

            if (!isA(ctx, typeTarget, typeRequired))
                {
                return TypeFit.NoFit;
                }

            TypeFit fit = TypeFit.Fit;
            for (int i = 0, cElements = exprs.size(); i < cElements; ++i)
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
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool pool = pool();

        // an empty map looks like an empty list to the parser
        if (typeRequired != null && typeRequired.isA(pool.typeMap()) && exprs.isEmpty())
            {
            MapExpression exprNew =  new MapExpression(new NamedTypeExpression(this, typeRequired),
                    Collections.EMPTY_LIST, Collections.EMPTY_LIST, getEndPosition());
            return replaceThisWith(exprNew).validate(ctx, typeRequired, errs);
            }

        TypeFit          fit         = TypeFit.Fit;
        List<Expression> listExprs   = exprs;
        int              cExprs      = listExprs.size();
        boolean          fConstant   = true;
        TypeConstant     typeElement = getImplicitElementType(ctx);

        if (typeRequired != null &&
                (typeElement == null ||
                    !pool.ensureArrayType(typeElement).isA(typeRequired) &&
                    !pool.ensureSetType(typeElement).isA(typeRequired)))
            {
            // the implicit type is not good; try to resolve it based on the required type
            typeElement = resolveElementType(typeRequired);
            }

        if (typeElement == null)
            {
            typeElement = pool.typeObject();
            }

        TypeConstant typeActual = getBaseType(ctx, typeRequired);
        boolean      fSet       = typeActual.isA(pool.typeSet());

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

                typeActual = exprTypeNew.ensureTypeConstant(ctx, errs).resolveAutoNarrowingBase();

                TypeConstant typeElementNew = resolveElementType(typeActual);
                if (typeElementNew != null)
                    {
                    typeElement = typeElementNew;
                    }
                }
            }

        typeActual = pool.ensureParameterizedTypeConstant(typeActual, typeElement);
        if (typeElement.isImmutable())
            {
            typeActual = pool.ensureImmutableTypeConstant(typeActual);
            }

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
            if (typeElement == null)
                {
                typeElement = pool.typeObject();
                }
            TypeConstant typeImpl = pool.ensureImmutableTypeConstant(fSet
                    ? pool.ensureSetType(typeElement)
                    : pool.ensureArrayType(typeElement));
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
    private TypeConstant resolveElementType(TypeConstant typeRequired)
        {
        TypeConstant typeElement = typeRequired.resolveGenericType("Element");
        if (typeElement == null && typeRequired instanceof IntersectionTypeConstant typeIntersect)
            {
            // try to calculate an element type that would probably accommodate the required type
            Set<TypeConstant> setSeqType = typeIntersect.collectMatching(pool().typeList(), null);
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
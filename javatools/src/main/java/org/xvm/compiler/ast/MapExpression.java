package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeCollector;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var_M;
import org.xvm.asm.op.Var_MN;

import org.xvm.compiler.Compiler;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;


/**
 * A map expression is an expression containing some number (0 or more) entries, each of which has
 * a key and a value.
 */
public class MapExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public MapExpression(TypeExpression type, List<Expression> keys, List<Expression> values,
                         long lEndPos)
        {
        assert type != null;

        this.type    = type;
        this.keys    = keys;
        this.values  = values;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return type.getStartPosition();
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
        TypeConstant typeExplicit = type.ensureTypeConstant(ctx, null);
        TypeConstant typeKey      = null;
        TypeConstant typeVal      = null;
        if (typeExplicit != null)
            {
            if (typeExplicit.containsUnresolved())
                {
                return null;
                }
            typeKey = typeExplicit.resolveGenericType("Key");
            typeVal = typeExplicit.resolveGenericType("Value");
            if (typeKey != null && typeVal != null)
                {
                return typeExplicit;
                }
            }

        ConstantPool pool    = pool();
        TypeConstant typeMap = typeExplicit == null
                ? pool.typeMap()
                : typeExplicit;

        // see if there is an implicit key and value type
        int cEntries = keys.size();
        assert cEntries == values.size();
        if (cEntries > 0 && (typeKey == null || typeVal == null))
            {
            TypeConstant[] aTypes = new TypeConstant[cEntries];

            if (typeKey == null)
                {
                for (int i = 0; i < cEntries; ++i)
                    {
                    aTypes[i] = keys.get(i).getImplicitType(ctx);
                    }
                typeKey = TypeCollector.inferFrom(aTypes, pool);
                }

            if (typeVal == null)
                {
                for (int i = 0; i < cEntries; ++i)
                    {
                    aTypes[i] = values.get(i).getImplicitType(ctx);
                    }
                typeVal = TypeCollector.inferFrom(aTypes, pool);
                }

            if (typeKey != null)
                {
                typeMap = typeMap.adoptParameters(pool, typeVal == null
                        ? new TypeConstant[] {typeKey}
                        : new TypeConstant[] {typeKey, typeVal});
                }
            }

        return typeMap;
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        CheckEntries:
        if (typeRequired != null)
            {
            TypeConstant typeMap = pool().typeMap();

            if (!typeRequired.isA(typeMap) || !type.testFit(ctx, typeMap.getType(), null).isFit())
                {
                break CheckEntries;
                }

            TypeConstant typeKey = typeRequired.resolveGenericType("Key");
            TypeConstant typeVal = typeRequired.resolveGenericType("Value");
            if (typeKey == null || typeVal == null)
                {
                break CheckEntries;
                }

            TypeFit fit = TypeFit.Fit;
            for (Expression key : keys)
                {
                TypeFit fitKey = key.testFit(ctx, typeKey, null);
                if (!fitKey.isFit())
                    {
                    break CheckEntries;
                    }
                fit = fit.combineWith(fitKey);
                }

            for (Expression val : values)
                {
                TypeFit fitVal = val.testFit(ctx, typeVal, null);
                if (!fitVal.isFit())
                    {
                    break CheckEntries;
                    }
                fit = fit.combineWith(fitVal);
                }
            return fit;
            }

        return super.testFit(ctx, typeRequired, errs);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool     pool        = pool();
        TypeFit          fit         = TypeFit.Fit;
        List<Expression> listKeys    = keys;
        List<Expression> listVals    = values;
        boolean          fConstKeys  = true;
        boolean          fConstVals  = true;
        TypeConstant     typeActual  = pool.typeMap();
        TypeConstant     typeKey     = null;
        TypeConstant     typeVal     = null;
        int              cExprs      = listKeys.size();
        assert cExprs == listVals.size();

        // default key and value type from the required type
        if (typeRequired != null && typeRequired.isA(pool.typeMap()))
            {
            // if there are required key/value types, then we'll use them to force the expressions
            // to convert to those types if necessary
            typeKey = typeRequired.resolveGenericType("Key");
            typeVal = typeRequired.resolveGenericType("Value");
            }

        // determine type from the explicitly stated type
        TypeExpression exprOldType = type;
        TypeConstant   typeMapType = pool.typeMap().getType();
        TypeExpression exprNewType = (TypeExpression) exprOldType.validate(ctx, typeMapType, errs);
        if (exprNewType == null)
            {
            fit        = TypeFit.NoFit;
            fConstKeys = false;
            }
        else
            {
            if (exprNewType != exprOldType)
                {
                type = exprNewType;
                }
            typeActual = exprNewType.ensureTypeConstant(ctx, errs).resolveAutoNarrowingBase();

            TypeConstant typeKeyTemp = typeActual.resolveGenericType("Key");
            if (typeKeyTemp != null)
                {
                typeKey = typeKeyTemp;
                }

            TypeConstant typeValTemp = typeActual.resolveGenericType("Value");
            if (typeValTemp != null)
                {
                typeVal = typeValTemp;
                }
            }

        // infer key type
        TypeConstant[] aTypes = null;
        if (typeKey == null && cExprs > 0)
            {
            aTypes = new TypeConstant[cExprs];
            for (int i = 0; i < cExprs; ++i)
                {
                aTypes[i] = listKeys.get(i).getImplicitType(ctx);
                }
            typeKey = TypeCollector.inferFrom(aTypes, pool);
            }

        // infer value type
        if (typeVal == null && cExprs > 0)
            {
            if (aTypes == null)
                {
                aTypes = new TypeConstant[cExprs];
                }
            for (int i = 0; i < cExprs; ++i)
                {
                aTypes[i] = listVals.get(i).getImplicitType(ctx);
                }
            typeVal = TypeCollector.inferFrom(aTypes, pool);
            }

        // build actual type from map type, key type, value type
        if (typeKey != null && (!typeKey.equals(typeActual.resolveGenericType("Key")) ||
                typeVal != null && !typeVal.equals(typeActual.resolveGenericType("Value"))))
            {
            typeActual = typeActual.adoptParameters(pool, typeVal == null
                    ? new TypeConstant[] {typeKey}
                    : new TypeConstant[] {typeKey, typeVal});
            }

        for (int i = 0; i < cExprs; ++i)
            {
            // validate key
            Expression exprOld = listKeys.get(i);
            Expression exprNew = exprOld.validate(ctx, typeKey, errs);
            if (exprNew == null)
                {
                fit        = TypeFit.NoFit;
                fConstKeys = false;
                }
            else
                {
                if (exprNew != exprOld)
                    {
                    listKeys.set(i, exprNew);
                    }
                fConstKeys &= exprNew.isConstant();
                }

            // validate value
            exprOld = listVals.get(i);
            exprNew = exprOld.validate(ctx, typeVal, errs);
            if (exprNew == null)
                {
                fit        = TypeFit.NoFit;
                fConstVals = false;
                }
            else
                {
                if (exprNew != exprOld)
                    {
                    listVals.set(i, exprNew);
                    }
                fConstVals &= exprNew.isConstant();
                }
            }

        // the type must either be Map (in which case a system-selected type will be used), or a
        // type that is a Map and takes the contents in its constructor, or has a no-parameter
        // constructor (so the map can be created empty and the items added to it)
        if (!typeActual.isSingleUnderlyingClass(true) ||
                !typeActual.getSingleUnderlyingClass(true).equals(pool.clzMap()))
            {
            // REVIEW how to handle another type besides "Map"? same problem will exist for List, Array, etc.
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                    pool.typeMap().getValueString(), typeActual.getValueString());
            return null;
            }

        // build a constant if it's a known container type and all keys and values are constants
        Constant constVal = null;
        if (fConstKeys)
            {
            Map<Constant, Constant> map = new ListMap<>(cExprs);
            for (int i = 0; i < cExprs; ++i)
                {
                Constant constKey = listKeys.get(i).toConstant();
                if (map.containsKey(constKey))
                    {
                    log (errs, Severity.ERROR, Compiler.MAP_KEYS_DUPLICATE, constKey.getValueString());
                    return null;
                    }

                map.put(constKey, fConstVals ? listVals.get(i).toConstant() : pool.val0());
                }

            if (fConstVals)
                {
                typeActual = pool.ensureImmutableTypeConstant(typeActual);
                constVal   = pool.ensureMapConstant(typeActual, map);
                }
            }

        return finishValidation(ctx, typeRequired, typeActual, fit, constVal, errs);
        }

    @Override
    public boolean isCompletable()
        {
        for (Expression key : keys)
            {
            if (!key.isCompletable())
                {
                return false;
                }
            }

        for (Expression val : values)
            {
            if (!val.isCompletable())
                {
                return false;
                }
            }

        return true;
        }

    @Override
    public boolean isShortCircuiting()
        {
        for (Expression key : keys)
            {
            if (key.isShortCircuiting())
                {
                return true;
                }
            }

        for (Expression val : values)
            {
            if (val.isShortCircuiting())
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
        // for example "Object map = [k1=v1];"
        TypeConstant typeMap = lvalue.getRegister().getType();
        return typeMap.resolveGenericType("Key")   != null &&
               typeMap.resolveGenericType("Value") != null;
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

            code.add(new Var_MN(lvalue.getRegister(), idName,
                    collectArguments(ctx, code, true, errs),
                    collectArguments(ctx, code, false, errs)));
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

        code.add(new Var_M(getType(),
                collectArguments(ctx, code, true, errs),
                collectArguments(ctx, code, false, errs)));
        return code.lastRegister();
        }

    /**
     * Helper method to generate an array of keys or values.
     */
    private Argument[] collectArguments(Context ctx, Code code, boolean fKeys, ErrorListener errs)
        {
        List<Expression> list  = fKeys ? keys : values;
        int              cArgs = list.size();
        Argument[]       aArg  = new Argument[cArgs];

        for (int i = 0; i < cArgs; ++i)
            {
            aArg[i] = list.get(i).generateArgument(ctx, code, true, false, errs);
            }
        return aArg;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("\n    {");

        for (int i = 0, c = keys.size(); i < c; ++i)
            {
            if (i > 0)
                {
                sb.append(',');
                }

            sb.append("\n    ")
              .append(keys.get(i))
              .append(" = ")
              .append(values.get(i));
            }

        sb.append("\n    }");

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return "size=" + keys.size();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression   type;
    protected List<Expression> keys;
    protected List<Expression> values;
    protected long             lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(MapExpression.class, "type", "keys", "values");
    }

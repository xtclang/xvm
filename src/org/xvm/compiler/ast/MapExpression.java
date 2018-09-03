package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeCollector;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.ListMap;


/**
 * A map expression is an expression containing some number (0 or more) entries, each of which has
 * a key and a value.
 */
public class MapExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public MapExpression(TypeExpression type, List<Expression> keys, List<Expression> values, long lEndPos)
        {
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
        TypeConstant typeExplicit = type == null ? null : type.ensureTypeConstant();
        TypeConstant typeKey      = null;
        TypeConstant typeVal      = null;
        if (typeExplicit != null)
            {
            typeKey = typeExplicit.getGenericParamType("KeyType");
            typeVal = typeExplicit.getGenericParamType("ValueType");
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
                typeKey = TypeCollector.inferFrom(aTypes);

                }

            if (typeVal == null)
                {
                for (int i = 0; i < cEntries; ++i)
                    {
                    aTypes[i] = values.get(i).getImplicitType(ctx);
                    }
                typeVal = TypeCollector.inferFrom(aTypes);
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
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool     pool        = pool();
        TypeFit          fit         = TypeFit.Fit;
        List<Expression> listKeys    = keys;
        List<Expression> listVals    = values;
        boolean          fConstant   = true;
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
            typeKey = typeRequired.getGenericParamType("KeyType");
            typeVal = typeRequired.getGenericParamType("ValueType");
            }

        // determine type from the explicitly stated type
        TypeExpression exprOldType = type;
        if (exprOldType != null)
            {
            TypeConstant   typeMapType = pool.ensureParameterizedTypeConstant(pool.typeType(), pool.typeMap());
            TypeExpression exprNewType = (TypeExpression) exprOldType.validate(ctx, typeMapType, errs);
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

                TypeConstant typeKeyTemp = typeActual.getGenericParamType("KeyType");
                if (typeKeyTemp != null)
                    {
                    typeKey = typeKeyTemp;
                    }

                TypeConstant typeValTemp = typeActual.getGenericParamType("ValueType");
                if (typeValTemp != null)
                    {
                    typeVal = typeValTemp;
                    }
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
            typeKey = TypeCollector.inferFrom(aTypes);
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
            typeVal = TypeCollector.inferFrom(aTypes);
            }

        // build actual type from map type, key type, value type
        if (typeKey != null && (!typeKey.equals(typeActual.getGenericParamType("KeyType")) ||
                typeVal != null && !typeVal.equals(typeActual.getGenericParamType("ValueType"))))
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
                fit       = TypeFit.NoFit;
                fConstant = false;
                }
            else
                {
                if (exprNew != exprOld)
                    {
                    listKeys.set(i, exprNew);
                    }
                fConstant &= exprNew.isConstant();
                }

            // validate value
            exprOld = listVals.get(i);
            exprNew = exprOld.validate(ctx, typeVal, errs);
            if (exprNew == null)
                {
                fit       = TypeFit.NoFit;
                fConstant = false;
                }
            else
                {
                if (exprNew != exprOld)
                    {
                    listVals.set(i, exprNew);
                    }
                fConstant &= exprNew.isConstant();
                }
            }

        // the type must either be Map (in which case a system-selected type will be used), or a
        // type that is a Map and takes the contents in its constructor, or has a no-parameter
        // constructor (so the map can be created empty and the items added to it)
        if (!typeActual.isSingleUnderlyingClass(true) || !typeActual.getSingleUnderlyingClass(true).equals(pool.clzMap()))
            {
            // REVIEW how to handle another type besides "Map"? same problem will exist for List, Array, etc.
            notImplemented();

            fConstant = false;
            }

        // build a constant if it's a known container type and all of the elements are constants
        Constant constVal = null;
        if (fConstant)
            {
            Map map = new ListMap<>(cExprs);
            for (int i = 0; i < cExprs; ++i)
                {
                map.put(listKeys.get(i).toConstant(), listVals.get(i).toConstant());
                }

            if (map.size() == cExprs)
                {
                constVal = pool.ensureMapConstant(typeActual, map);
                }
            else
                {
                // TODO WARNING or ERROR that the map contains non-unique keys
                System.out.println("MapExpression: constant contains key collision(s): " + this);
                }
            }

        return finishValidation(typeRequired, typeActual, fit, constVal, errs);
        }

    @Override
    public boolean isAborting()
        {
        for (Expression key : keys)
            {
            if (key.isAborting())
                {
                return true;
                }
            }
        for (Expression val : values)
            {
            if (val.isAborting())
                {
                return true;
                }
            }
        return false;
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
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (isConstant())
            {
            return toConstant();
            }

        List<Expression> listKeys = keys;
        List<Expression> listVals = values;
        int              cEntries = listKeys.size();
        Argument[]       aKeys    = new Argument[cEntries];
        Argument[]       aVals    = new Argument[cEntries];
        for (int i = 0; i < cEntries; ++i)
            {
            aKeys[i] = listKeys.get(i).generateArgument(ctx, code, false, true, errs);
            aVals[i] = listVals.get(i).generateArgument(ctx, code, false, true, errs);
            }
        notImplemented();
        // TODO code.add(new Var_M(getType(), aKeys, aVals));
        return code.lastRegister();
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

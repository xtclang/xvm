package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * A type expression for a function. This corresponds to the "function" keyword.
 */
public class FunctionTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public FunctionTypeExpression(Token function, Token conditional, List<Parameter> returnValues,
            List<TypeExpression> params, long lEndPos)
        {
        this.function     = function;
        this.conditional  = conditional;
        this.returnValues = returnValues;
        this.paramTypes   = params;
        this.lEndPos      = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public boolean isConditional()
        {
        return conditional != null;
        }

    public List<Parameter> getReturnValues()
        {
        return returnValues;
        }

    public List<TypeExpression> getParamTypes()
        {
        return paramTypes;
        }

    @Override
    public long getStartPosition()
        {
        return function.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return  lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx)
        {
        ConstantPool pool = pool();
        return pool.ensureClassTypeConstant(pool.clzFunction(), null,
                toTupleType(toTypeConstantArray(paramTypes)),
                isConditional()
                        ? toConditionalTupleType(toParamTypeConstantArray(returnValues))
                        : toTupleType(toParamTypeConstantArray(returnValues)));
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        List<TypeExpression> listTypes = paramTypes;
        boolean              fValid    = true;
        for (int i = 0, c = listTypes.size(); i < c; i++)
            {
            TypeExpression exprOld = listTypes.get(i);
            TypeExpression exprNew = (TypeExpression) exprOld.validate(ctx, null, errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                if (exprNew.isDynamic())
                    {
                    log(errs, Severity.ERROR, Compiler.UNSUPPORTED_DYNAMIC_TYPE_PARAMS);
                    fValid = false;
                    }

                if (exprNew != exprOld)
                    {
                    listTypes.set(i, exprNew);
                    }
                }
            }

        return fValid
                ? super.validate(ctx, typeRequired, errs)
                : null;
        }

    private TypeConstant toTupleType(TypeConstant[] aconstTypes)
        {
        ConstantPool pool = pool();
        return pool.ensureClassTypeConstant(pool.clzTuple(), null, aconstTypes);
        }

    private TypeConstant toConditionalTupleType(TypeConstant[] aconstTypes)
        {
        ConstantPool pool = pool();

        int cTypes = aconstTypes.length;
        TypeConstant[] aconstCond = new TypeConstant[cTypes+1];
        aconstCond[0] = pool.typeBoolean();
        System.arraycopy(aconstTypes, 0, aconstCond, 1, cTypes);
        return pool.ensureClassTypeConstant(pool.clzCondTuple(), null, aconstTypes);
        }

    static TypeConstant[] toTypeConstantArray(List<TypeExpression> list)
        {
        int            c      = list.size();
        TypeConstant[] aconst = new TypeConstant[c];
        for (int i = 0; i < c; ++i)
            {
            aconst[i] = list.get(i).ensureTypeConstant();
            }
        return aconst;
        }

    private static TypeConstant[] toParamTypeConstantArray(List<Parameter> list)
        {
        int            c      = list.size();
        TypeConstant[] aconst = new TypeConstant[c];
        for (int i = 0; i < c; ++i)
            {
            aconst[i] = list.get(i).getType().ensureTypeConstant();
            }
        return aconst;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("function ");

        if (isConditional())
            {
            sb.append("conditional ");
            }

        if (returnValues.isEmpty())
            {
            sb.append("void");
            }
        else if (returnValues.size() == 1)
            {
            sb.append(returnValues.get(0));
            }
        else
            {
            boolean first = true;
            for (Parameter param : returnValues)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param);
                }
            }

        sb.append(" (");

        boolean first = true;
        for (TypeExpression type : paramTypes)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(type);
            }

        sb.append(')');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token                function;
    protected Token                conditional;
    protected List<Parameter>      returnValues;
    protected List<TypeExpression> paramTypes;
    protected long                 lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(FunctionTypeExpression.class, "returnValues", "paramTypes");
    }

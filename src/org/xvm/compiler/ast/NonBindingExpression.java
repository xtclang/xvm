package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.Statement.Context;


/**
 * This is used to specify an argument ("?") for a function that indicates that the corresponding
 * parameter of the function should remain unbound.
 */
public class NonBindingExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public NonBindingExpression(long lStartPos, long lEndPos, TypeExpression type)
        {
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        this.type      = type;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the type expression of the unbound argument, iff one was specified; otherwise null
     */
    public TypeExpression getArgType()
        {
        return type;
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
        return type == null
                ? null
                : type.getImplicitType(ctx);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit      fit      = TypeFit.Fit;
        TypeConstant typeArg  = null;
        Constant     constant = null;

        TypeExpression exprOldType = this.type;
        if (exprOldType != null)
            {
            TypeExpression exprNewType = (TypeExpression) exprOldType.validate(ctx, typeRequired, errs);
            if (exprNewType == null)
                {
                fit     = TypeFit.NoFit;
                typeArg = typeRequired;
                }
            else
                {
                this.type = exprNewType;
                typeArg   = exprNewType.ensureTypeConstant();
                }
            }

        // unfortunately, we have to make up a type here if none is specified or required; this will
        // necessarily complicate the logic of the invocation expression
        if (typeArg == null)
            {
            typeArg = pool().typeObject();
            }

        return finishValidation(typeRequired, typeArg, fit, constant, errs);
        }

    @Override
    public boolean isNonBinding()
        {
        return true;
        }

    @Override
    public Argument generateArgument(Code code, boolean fLocalPropOk,
            boolean fUsedOnce,
            ErrorListener errs)
        {
        throw new IllegalStateException("NonBindingExpression cannot generate an argument;"
                + ": that's why they're called non-binding! (" + this + ')');
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return type == null
                ? "?"
                : "<" + type + ">?";
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected long           lStartPos;
    protected long           lEndPos;
    protected TypeExpression type;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NonBindingExpression.class, "type");
    }

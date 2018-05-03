package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Version;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * Invocation expression represents calling a method or function.
 *
 * If you already have an expression "expr", this is for "expr(args)".
 */
public class InvocationExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public InvocationExpression(Expression expr, List<Expression> args, long lEndPos)
        {
        this.expr    = expr;
        this.args    = args;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        return expr instanceof NameExpression
                && ((NameExpression) expr).getName().equals("versionMatches")
                && args.size() == 1
                && args.get(0) instanceof VersionExpression
                && ((NameExpression) expr).getLeftExpression() != null
                && ((NameExpression) expr).isOnlyNames()
                || super.validateCondition(errs);
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        if (validateCondition(null))
            {
            // build the qualified module name
            StringBuilder sb    = new StringBuilder();
            List<Token>   names = ((NameExpression) expr).getNameTokens();
            for (int i = 0, c = names.size() - 1; i < c; ++i)
                {
                if (i > 0)
                    {
                    sb.append('.');
                    }
                sb.append(names.get(i).getValueText());
                }

            ConstantPool pool    = pool();
            String       sModule = sb.toString();
            Version      version = ((VersionExpression) args.get(0)).getVersion();
            return pool.ensureImportVersionCondition(
                    pool.ensureModuleConstant(sModule), pool.ensureVersionConstant(version));
            }

        return super.toConditionalConstant();
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


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        return TypeConstant.NO_TYPES;
        }


    // TODO getValueCount() - could be any #?

    @Override
    public boolean isConstant()
        {
        // assume all invocations can have side effects
        return false;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
        boolean fValid   = true;
        boolean fNoDeRef = false;

        // TODO validate parameters - might set fNoDeRef to true if any of the params is "?"

        if (expr instanceof NameExpression)
            {
            // when we have a name expression on the left, we do NOT (!!!) validate it, because the
            // name resolution is the responsibility of this InvocationExpression, and the
            // NameExpression will error on resolving a method/function name
            MultiMethodConstant  idMMethod = null;
            NameExpression       exprName  = (NameExpression) expr;
            Expression           left      =  exprName.left;
            Token                name      = exprName.name;
            String               sName     = name.getValueText();
            List<TypeExpression> params    = exprName.params;                // redundant returns

            // it's possible that the name indicates that the invocation is a partial binding and
            // not actually an invocation
            fNoDeRef |= exprName.isSuppressDeref();

            // a name expression may have type params from the construct:
            //      QualifiedNameName TypeParameterTypeList-opt
            // these names form the optional "redundant returns" portion of the method/function
            // invocation
            ConstantPool pool   = pool();
            boolean      fValid = true;
            if (params != null)
                {
                for (int i = 0, c = params.size(); i < c; ++i)
                    {
                    TypeExpression typeOld = params.get(i);
                    TypeExpression typeNew = (TypeExpression) typeOld.validate(
                            ctx, pool.typeType(), TuplePref.Rejected, errs);
                    fValid &= typeNew != null;
                    if (typeNew != typeOld && typeNew != null)
                        {
                        params.set(i, typeNew);
                        }
                    }
                }

            if (left == null)
                {
                Argument arg = ctx.resolveName(name, errs);
                if (arg instanceof MultiMethodConstant)
                    {
                    idMMethod = (MultiMethodConstant) arg;
                    }
                else if (arg == null)
                    {
                    log(errs, Severity.ERROR, Compiler.NAME_MISSING,
                            sName, ctx.getMethod().getIdentityConstant().getSignature());
                    fValid = false;
                    }
                else
                    {
                    // TODO log error - the name has to resolve to a method/function
                    fValid = false;
                    }
                }
            else
                {
                Expression leftNew = left.validate(ctx, null, TuplePref.Rejected, errs);
                if (leftNew == null)
                    {
                    // TODO null is an error, so the idea is to get out of here (but maybe do some work first, in case we can catch other errors)
                    return null;
                    }
                else if (leftNew != left)
                    {
                    exprName.left = left = leftNew;
                    }

                if (left.isVoid())
                    {
                    // TODO log error
                    return null;
                    }

                TypeInfo infoType = left.getType().ensureTypeInfo(errs);
                // TODO that info is where we will ultimately find the sName
                }

            if (!fValid)
                {
                return null;
                }

            // find the correct Method within the MultiMethod, using the (optional) redundant return
            // types, and the parameters
            // TODO
            }
        else // the expr is NOT a NameExpression
            {
            Expression exprNew = expr.validate(ctx, pool().typeFunction(), TuplePref.Rejected, errs);
            if (exprNew != expr)
                {
                if (exprNew != null)
                    {
                    expr = exprNew;
                    }
                }

            // verify that the arguments match the parameters
            // TODO

            }

        if (fValid)
            {

            }
        // TODO we have an "expr" that represents the thing being invoked, and we have "args" that represents the things being passed
        // TODO we may need one to validate the other, i.e. we may need to know the arg types to find the method, the the method to validate the args by required type

        // if (expr.validate(ctx,))
        // TODO

        // TODO goal is to figure out which one of these that we are responsible for producing code to do:
        // 1) call a method
        // 2) call a function
        // 3) bind a method (and possibly (partially) bind the resulting function)
        // 4) (partially) bind a function

        // TODO finishValidation() or finishValidations()
        return finishValidation(fit, typeRequired == null ? pool().typeObject() : typeRequired, null);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(expr)
          .append('(');

        boolean first = true;
        for (Expression arg : args)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(arg);
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

    protected Expression       expr;
    protected List<Expression> args;
    protected long             lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(InvocationExpression.class, "expr", "args");
    }

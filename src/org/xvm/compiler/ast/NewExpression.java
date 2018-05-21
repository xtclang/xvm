package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.constants.TypeInfo;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;

import static org.xvm.util.Handy.indentLines;


/**
 * "New object" expression.
 */
public class NewExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a "new" expression.
     *
     * @param operator  presumably, the "new" operator
     * @param type      the type being instantiated
     * @param args      a list of constructor arguments for the type being instantiated, or null
     * @param body      the body of the anonymous inner class being instantiated, or null
     * @param lEndPos   the expression's end position in the source code
     */
    public NewExpression(Token operator, TypeExpression type, List<Expression> args, StatementBlock body, long lEndPos)
        {
        this.left     = null;
        this.operator = operator;
        this.type     = type;
        this.args     = args;
        this.body     = body;
        this.lEndPos  = lEndPos;
        }

    /**
     * Construct a ".new" expression.
     *
     * @param left      the "left" expression
     * @param operator  presumably, the "new" operator
     * @param type      the type being instantiated
     * @param args      a list of constructor arguments for the type being instantiated, or null
     * @param lEndPos   the expression's end position in the source code
     */
    public NewExpression(Expression left, Token operator, TypeExpression type, List<Expression> args, long lEndPos)
        {
        this.left     = left;
        this.operator = operator;
        this.type     = type;
        this.args     = args;
        this.body     = null;
        this.lEndPos  = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the parent of the object being new'd, for example "parent.new Child()", or null if
     *         the object being new'd is of a top level class
     */
    public Expression getLeftExpression()
        {
        return left;
        }

    /**
     * @return the type of the object being new'd; never null
     */
    public TypeExpression getTypeExpression()
        {
        return type;
        }

    /**
     * @return return a list of expressions that are passed to the constructor of the object being
     *         instantiated, or null if there is no argument list specified
     */
    public List<Expression> getConstructorArguments()
        {
        return args;
        }

    /**
     * @return the body of the anonymous inner class, or null if this "new" is not instantiating an
     *         anonymous inner class
     */
    public StatementBlock getAnonymousInnerClassBody()
        {
        return body;
        }

    @Override
    public long getStartPosition()
        {
        return left == null ? operator.getStartPosition() : left.getStartPosition();
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
        if (body != null)
            {
            // TODO
            throw new UnsupportedOperationException("anonymous inner class type");
            }

        // REVIEW: if (left != null)
        return getTypeExpression().ensureTypeConstant();
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, TuplePref pref)
        {
        // TODO there should be a default implementation of this
        TypeConstant typeThis = getImplicitType(ctx);
        if (typeThis == null)
            {
            return TypeFit.NoFit;
            }

        if (typeRequired == null || typeThis.isA(typeRequired))
            {
            return pref == TuplePref.Required
                    ? TypeFit.Pack
                    : TypeFit.Fit;
            }

        if (typeThis.getConverterTo(typeRequired) != null)
            {
            return pref == TuplePref.Required
                    ? TypeFit.ConvPack
                    : TypeFit.Conv;
            }

        return TypeFit.NoFit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
        boolean fValid = true;

        TypeExpression exprTypeOld   = this.type;
        TypeExpression exprTypeNew   = (TypeExpression) exprTypeOld.validate(ctx, typeRequired, pref, errs);
        TypeConstant   typeConstruct = null;
        TypeInfo infoConstruct = null;
        if (exprTypeNew == null)
            {
            fValid = false;
            }
        else if (exprTypeNew != exprTypeOld)
            {
            this.type = exprTypeNew;

            typeConstruct = exprTypeNew.ensureTypeConstant();
            infoConstruct = typeConstruct.ensureTypeInfo(errs);

            // if the type is not new-able, then it must be an anonymous inner class with a body
            // that makes the type new-able
            if (body == null && !infoConstruct.isNewable())
                {
                // TODO log error
                fValid = false;
                }

            if (left != null && !typeConstruct.isAutoNarrowing())
                {
                // TODO log error - only auto-narrowing types can be constructed using ".new"
                fValid = false;
                }
            }

        return finishValidation(fValid ? TypeFit.Fit : TypeFit.NoFit, typeConstruct, null);
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        // TODO
        }


    // ----- debugging assistance ------------------------------------------------------------------

    /**
     * @return the signature of the contructor invocation
     */
    public String toSignatureString()
        {
        StringBuilder sb = new StringBuilder();

        if (left != null)
            {
            sb.append(left)
              .append('.');
            }

        sb.append(operator.getId().TEXT)
          .append(' ')
          .append(type);

        if (args != null)
            {
            sb.append('(');
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
            }

        return sb.toString();
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(toSignatureString());

        if (body != null)
            {
            sb.append('\n')
              .append(indentLines(body.toString(), "        "));
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        String s = toSignatureString();

        return body == null
                ? s
                : s + "{..}";
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression       left;
    protected Token            operator;
    protected TypeExpression   type;
    protected List<Expression> args;
    protected StatementBlock   body;
    protected long             lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NewExpression.class, "left", "type", "args", "body");
    }

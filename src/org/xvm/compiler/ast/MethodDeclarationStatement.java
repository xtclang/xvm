package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * A method declaration.
 *
 * @author cp 2017.04.03
 */
public class MethodDeclarationStatement
        extends ComponentStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public MethodDeclarationStatement(long                 lStartPos,
                                      long                 lEndPos,
                                      Expression           condition,
                                      List<Token>          modifiers,
                                      List<Annotation>     annotations,
                                      List<Parameter>      typeParams,
                                      Token                conditional,
                                      List<TypeExpression> returns,
                                      Token                name,
                                      List<TypeExpression> redundant,
                                      List<Parameter>      params,
                                      StatementBlock       body,
                                      StatementBlock       continuation,
                                      Token                doc)
        {
        super(lStartPos, lEndPos);

        this.condition    = condition;
        this.modifiers    = modifiers;
        this.annotations  = annotations;
        this.conditional  = conditional;
        this.typeParams   = typeParams;
        this.returns      = returns;
        this.name         = name;
        this.redundant    = redundant;
        this.params       = params;
        this.body         = body;
        this.continuation = continuation;
        this.doc          = doc;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public Access getDefaultAccess()
        {
        Access access = getAccess(modifiers);
        return access == null
                ? super.getDefaultAccess()
                : access;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    protected void registerStructures(ErrorListener errs)
        {
        // create the structure for this method
        if (getComponent() == null)
            {
            // TODO validate that the "redundant" types match the return types
            // TODO validate that the names (params, type params) are unique

            // create a structure for this type
            Component container = getParent().getComponent();
            String    sName     = (String) name.getValue();
            if (container.isMethodContainer())
                {
                boolean         fFunction   = isStatic(modifiers);
                Access          access      = getDefaultAccess();
                ConstantPool    pool        = container.getConstantPool();

                // build array of return types
                int ofReturn = 0;
                int cReturns = returns == null ? 0 : returns.size();
                if (conditional != null)
                    {
                    ++ofReturn;
                    ++cReturns;
                    }
                org.xvm.asm.Parameter[] aReturns = new org.xvm.asm.Parameter[cReturns];
                if (conditional != null)
                    {
                    aReturns[0] = new org.xvm.asm.Parameter(pool,
                            pool.ensureEcstasyTypeConstant("Boolean"), null, null, true, 0, true);
                    }
                for (int i = ofReturn; i < cReturns; ++i)
                    {
                    aReturns[i] = new org.xvm.asm.Parameter(pool,
                            returns.get(i-ofReturn).ensureTypeConstant(), null, null, true, i, false);
                    }

                // build array of parameters
                int cTypes  = typeParams == null ? 0 : typeParams.size();
                int cParams = cTypes + params.size();
                org.xvm.asm.Parameter[] aParams = new org.xvm.asm.Parameter[cParams];
                for (int i = 0; i < cTypes; ++i)
                    {
                    Parameter      param = typeParams.get(i);
                    TypeExpression exprType  = param.getType();
                    TypeConstant   constType = pool.ensureClassTypeConstant(
                            pool.ensureEcstasyClassConstant("Type"), Access.PUBLIC,
                            exprType == null
                                    ? pool.ensureEcstasyTypeConstant("Object")
                                    : exprType.ensureTypeConstant());
                    aParams[i] = new org.xvm.asm.Parameter(pool, constType, param.getName(), null, false, i, true);
                    }
                for (int i = cTypes; i < cParams; ++i)
                    {
                    Parameter param = params.get(i-cTypes);
                    aParams[i] = new org.xvm.asm.Parameter(pool, param.getType().ensureTypeConstant(),
                            param.getName(), /* TODO how to do value? */ null, false, i, false);
                    }

                MethodStructure method = container.createMethod(fFunction, access, aReturns, sName, aParams);
                setComponent(method);
                }
            else
                {
                log(errs, Severity.ERROR, org.xvm.compiler.Compiler.METHOD_UNEXPECTED, sName, container);
                throw new UnsupportedOperationException("not a method container: " + container);
                }
            }

        super.registerStructures(errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    public String toSignatureString()
        {
        StringBuilder sb = new StringBuilder();

        if (modifiers != null)
            {
            for (Token token : modifiers)
                {
                sb.append(token.getId().TEXT)
                        .append(' ');
                }
            }

        if (annotations != null)
            {
            for (Annotation annotation : annotations)
                {
                sb.append(annotation)
                        .append(' ');
                }
            }

        if (typeParams != null)
            {
            sb.append('<');
            boolean first = true;
            for (Parameter param : typeParams)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param.toTypeParamString());
                }
            sb.append("> ");
            }

        if (returns == null || returns.isEmpty())
            {
            sb.append("Void ");
            }
        else if (returns.size() == 1)
            {
            sb.append(returns.get(0))
                    .append(' ');
            }
        else
            {
            sb.append(" (");
            boolean first = true;
            for (TypeExpression type : returns)
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
            sb.append(") ");
            }

        sb.append(name.getValue());

        if (redundant != null)
            {
            sb.append('<');
            boolean first = true;
            for (TypeExpression type : redundant)
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
            sb.append('>');
            }

        if (params != null)
            {
            sb.append('(');
            boolean first = true;
            for (Parameter param : params)
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
            sb.append(')');
            }

        return sb.toString();
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (doc != null)
            {
            String sDoc = String.valueOf(doc.getValue());
            if (sDoc.length() > 100)
                {
                sDoc = sDoc.substring(0, 97) + "...";
                }
            appendString(sb.append("/*"), sDoc).append("*/\n");
            }

        sb.append(toSignatureString());

        if (body == null)
            {
            sb.append(';');
            }
        else
            {
            String sBody = body.toString();
            if (sBody.indexOf('\n') >= 0)
                {
                sb.append('\n')
                  .append(indentLines(sBody, "    "));
                }
            else
                {
                sb.append(' ')
                  .append(sBody);
                }
            
            if (continuation != null)
                {
                String sFinally = continuation.toString();
                sb.append("\nfinally");
                if (sFinally.indexOf('\n') >= 0)
                    {
                    sb.append('\n')
                      .append(indentLines(sFinally, "    "));
                    }
                else
                    {
                    sb.append(' ')
                      .append(sFinally);
                    }
                }
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toSignatureString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression           condition;
    protected List<Token>          modifiers;
    protected List<Annotation>     annotations;
    protected List<Parameter>      typeParams;
    protected Token                conditional;
    protected List<TypeExpression> returns;
    protected Token                name;
    protected List<TypeExpression> redundant;
    protected List<Parameter>      params;
    protected StatementBlock       body;
    protected StatementBlock       continuation;
    protected Token                doc;

    private static final Field[] CHILD_FIELDS = fieldsForNames(MethodDeclarationStatement.class,
            "annotations", "typeParams", "returns", "redundant", "params", "body", "continuation");
    }

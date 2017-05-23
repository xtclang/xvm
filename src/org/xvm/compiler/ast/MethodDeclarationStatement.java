package org.xvm.compiler.ast;


import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.*;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    public MethodDeclarationStatement(Expression           condition,
                                      List<Token>          modifiers,
                                      List<Annotation>     annotations,
                                      List<Token>          typeVars,
                                      List<TypeExpression> returns,
                                      Token                name,
                                      List<TypeExpression> redundant,
                                      List<Parameter>      params,
                                      StatementBlock       body,
                                      StatementBlock       continuation,
                                      Token                doc)
        {
        this.condition    = condition;
        this.modifiers    = modifiers;
        this.annotations  = annotations;
        this.typeVars     = typeVars;
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
    protected void registerStructures(AstNode parent, ErrorListener errs)
        {
        setParent(parent);

        // create the structure for this method
        if (getComponent() == null)
            {
            // create a structure for this type
            Component container = parent.getComponent();
            String    sName     = (String) name.getValue();
            if (container.isMethodContainer())
                {
                boolean         fFunction   = isStatic(modifiers);
                Access          access      = getDefaultAccess();
                TypeConstant[]  returnTypes = toTypeConstants(returns);
                TypeConstant[]  paramTypes  = toTypeConstants(toTypeExpressions(params));
                MethodStructure method      = container.createMethod(fFunction, access, returnTypes,
                                                                     sName, paramTypes);
                setStructure(method);
                }
            else
                {
                // TODO need a "method unexpected" error code
                errs.log(Severity.ERROR, org.xvm.compiler.Compiler.PROP_UNEXPECTED, new Object[] {sName, container},
                        getSource(), name.getStartPosition(), name.getEndPosition());

                throw new UnsupportedOperationException("not a method container: " + container);
                }
            }

        super.registerStructures(parent, errs);
        }

    protected List<TypeExpression> toTypeExpressions(List<Parameter> params)
        {
        if (params == null || params.isEmpty())
            {
            return Collections.EMPTY_LIST;
            }

        List<TypeExpression> list = new ArrayList<>(params.size());
        for (Parameter param : params)
            {
            list.add(param.getType());
            }
        return list;
        }

    protected TypeConstant[] toTypeConstants(List<TypeExpression> types)
        {
        if (types == null || types.isEmpty())
            {
            return ConstantPool.NO_TYPES;
            }

        int i = 0;
        TypeConstant[] array     = new TypeConstant[types.size()];
        Component      container = parent.getComponent();
        for (TypeExpression type : types)
            {
            array[i++] = container.getConstantPool().createUnresolvedTypeConstant(type.toString());
            }
        return array;
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

        if (typeVars != null)
            {
            sb.append('<');
            boolean first = true;
            for (Token var : typeVars)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(var.getValue());
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
    protected List<Token>          typeVars;
    protected List<TypeExpression> returns;
    protected Token                name;
    protected List<TypeExpression> redundant;
    protected List<Parameter>      params;
    protected StatementBlock       body;
    protected StatementBlock       continuation;
    protected Token                doc;

    private static final Field[] CHILD_FIELDS = fieldsForNames(MethodDeclarationStatement.class,
            "annotations", "returns", "redundant", "params", "body", "continuation");
    }

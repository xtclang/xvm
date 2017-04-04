package org.xvm.compiler.ast;


import org.xvm.asm.StructureContainer;
import org.xvm.compiler.Token;

import java.util.List;

import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * A method declaration.
 *
 * @author cp 2017.04.03
 */
public class MethodDeclarationStatement
        extends Statement
    {
    public MethodDeclarationStatement(List<Token> modifiers,
                                      List<Annotation> annotations,
                                      List<Token> typeVars,
                                      List<TypeExpression> returns,
                                      Token name,
                                      List<Parameter> params,
                                      BlockStatement body,
                                      Token doc)
        {
        this.modifiers   = modifiers;
        this.annotations = annotations;
        this.typeVars    = typeVars;
        this.returns     = returns;
        this.name        = name;
        this.params      = params;
        this.body        = body;
        this.doc         = doc;
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
            }

        return sb.toString();
        }

    public StructureContainer getStructure()
        {
        return struct;
        }

    public void setStructure(StructureContainer struct)
        {
        this.struct = struct;
        }

    public final List<Token>          modifiers;
    public final List<Annotation>     annotations;
    public final List<Token>          typeVars;
    public final List<TypeExpression> returns;
    public final Token                name;
    public final List<Parameter>      params;
    public final BlockStatement       body;
    public final Token                doc;

    StructureContainer struct;
    }

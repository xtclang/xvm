package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.List;
import java.util.Map;

import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * Represents an enum value.
 *
 * @author cp 2017.04.03
 */
public class EnumDeclaration
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public EnumDeclaration(List<Annotation> annotations, Token name, List<TypeExpression> typeParams,
                           List<Expression> args, StatementBlock body, Token doc)
        {
        this.annotations = annotations;
        this.name        = name;
        this.typeParams  = typeParams;
        this.args        = args;
        this.body        = body;
        this.doc         = doc;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

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

        if (annotations != null)
            {
            for (Annotation annotation : annotations)
                {
                sb.append(annotation)
                  .append(' ');
                }
            }

        sb.append(name.getValue());

        if (typeParams != null)
            {
            sb.append('<');
            boolean first = true;
            for (TypeExpression typeParam : typeParams)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(typeParam);
                }
            sb.append('>');
            }

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

        if (body != null)
            {
            sb.append('\n')
              .append(indentLines(body.toString(), "    "));
            }

        return sb.toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("annotations", annotations);
        map.put("name", name);
        map.put("typeParams", typeParams);
        map.put("args", args);
        map.put("body", body);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected List<Annotation> annotations;
    protected Token name;
    protected List<TypeExpression> typeParams;
    protected List<Expression> args;
    protected StatementBlock body;
    protected Token doc;
    }

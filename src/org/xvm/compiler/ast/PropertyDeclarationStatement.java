package org.xvm.compiler.ast;


import org.xvm.asm.StructureContainer;
import org.xvm.compiler.Token;

import java.util.List;

import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * A property declaration.
 *
 * @author cp 2017.04.04
 */
public class PropertyDeclarationStatement
        extends Statement
    {
    public PropertyDeclarationStatement(List<Token>      modifiers,
                                        List<Annotation> annotations,
                                        TypeExpression   type,
                                        Token            name,
                                        Expression       value,
                                        StatementBlock body,
                                        Token            doc)
        {
        this.modifiers   = modifiers;
        this.annotations = annotations;
        this.type        = type;
        this.name        = name;
        this.value       = value;
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

        sb.append(type)
          .append(' ')
          .append(name.getValue());

        if (value != null)
            {
            sb.append(" = ")
              .append(value)
              .append(";");
            }
        else if (body != null)
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
        else
            {
            sb.append(';');
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

    public final List<Token>      modifiers;
    public final List<Annotation> annotations;
    public final TypeExpression   type;
    public final Token            name;
    public final Expression       value;
    public final StatementBlock body;
    public final Token            doc;

    StructureContainer struct;
    }

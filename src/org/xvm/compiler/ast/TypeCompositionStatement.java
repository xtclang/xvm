package org.xvm.compiler.ast;


import org.xvm.asm.StructureContainer;
import org.xvm.compiler.Token;

import java.util.List;

import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * A type declaration.
 *
 * @author cp 2017.03.28
 */
public class TypeCompositionStatement
        extends Statement
    {
    public TypeCompositionStatement(List<Token> modifiers,
                                    List<Annotation> annotations,
                                    Token category,
                                    Token name,
                                    List<Token> qualified,
                                    List<Parameter> typeParams,
                                    List<Parameter> constructorParams,
                                    List<Composition> composition,
                                    StatementBlock body,
                                    Token doc)
        {
        this.modifiers         = modifiers;
        this.annotations       = annotations;
        this.category          = category;
        this.name              = name;
        this.qualified         = qualified;
        this.typeParams        = typeParams;               
        this.constructorParams = constructorParams;        
        this.composition       = composition;              
        this.body              = body;
        this.doc               = doc;
        }

    public String getName()
        {
        if (category.getId() == Token.Id.MODULE)
            {
            StringBuilder sb = new StringBuilder();
            for (Token suffix : qualified)
                {
                sb.append('.')
                  .append(suffix.getValue());
                }
            return sb.substring(1).toString();
            }
        else
            {
            return (String) name.getValue();
            }
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

        sb.append(category.getId().TEXT)
          .append(' ');

        if (qualified == null)
            {
            sb.append(name.getValue());
            }
        else
            {
            boolean first = true;
            for (Token token : qualified)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append('.');
                    }
                sb.append(token.getValue());
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
            sb.append('>');
            }

        if (constructorParams != null)
            {
            sb.append('(');
            boolean first = true;
            for (Parameter param : constructorParams)
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

        for (Composition composition : this.composition)
            {
            sb.append("\n        ")
              .append(composition);
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

    public final List<Token>       modifiers;
    public final List<Annotation>  annotations;
    public final Token             category;
    public final Token             name;
    public final List<Token>       qualified;
    public final List<Parameter>   typeParams;
    public final List<Parameter>   constructorParams;
    public final List<Composition> composition;
    public final StatementBlock body;
    public final Token             doc;

    StructureContainer struct;
    }

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
public class TypeDeclarationStatement
        extends Statement
    {
    public TypeDeclarationStatement(List<Token>       modifiers,
                                    List<Annotation>  annotations,
                                    Token             category,
                                    Token             name,
                                    List<Token>       qualifier,        
                                    List<Parameter>   typeParams,       
                                    List<Parameter>   constructorParams,
                                    List<Composition> composition,      
                                    BlockStatement    body,
                                    Token             doc)
        {
        this.modifiers         = modifiers;
        this.annotations       = annotations;
        this.category          = category;
        this.name              = name;
        this.qualifier         = qualifier;
        this.typeParams        = typeParams;               
        this.constructorParams = constructorParams;        
        this.composition       = composition;              
        this.body              = body;
        this.doc               = doc;
        }

    public String getName()
        {
        String sName = (String) name.getValue();
        if (category.getId() == Token.Id.MODULE)
            {
            StringBuilder sb = new StringBuilder(sName);
            for (Token suffix : qualifier)
                {
                sb.append('.')
                  .append(suffix.getValue());
                }
            return sb.toString();
            }
        return sName;
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
          .append(' ')
          .append(name.getValue());

        if (qualifier != null)
            {
            for (Token token : qualifier)
                {
                sb.append('.')
                  .append(token.getValue());
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
    public final List<Token>       qualifier;
    public final List<Parameter>   typeParams;
    public final List<Parameter>   constructorParams;
    public final List<Composition> composition;
    public final BlockStatement    body;
    public final Token             doc;

    StructureContainer struct;
    }

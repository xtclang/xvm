package org.xvm.compiler.ast;


import org.xvm.asm.StructureContainer;
import org.xvm.compiler.Token;
import org.xvm.util.ListMap;

import java.util.List;
import java.util.Map;

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
    // ----- constructors --------------------------------------------------------------------------

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


    // ----- accessors -----------------------------------------------------------------------------

    public Token getCategory()
        {
        return category;
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

    public StructureContainer getStructure()
        {
        return struct;
        }

    public void setStructure(StructureContainer struct)
        {
        this.struct = struct;
        }

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

    @Override
    public String getDumpDesc()
        {
        return (category == null ? "?" : category.getValue() == null ? category.getId().TEXT : String.valueOf(category.getValue()))
                + ' ' + (name == null ? "?" : name.getValue() == null ? name.getId().TEXT : String.valueOf(name.getValue()));
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("modifiers", modifiers);
        map.put("modifiers", modifiers);
        map.put("annotations", annotations);
        map.put("category", category);
        map.put("name", name);
        map.put("qualified", qualified);
        map.put("typeParams", typeParams);
        map.put("constructorParams", constructorParams);
        map.put("composition", composition);
        map.put("body", body);
        return map;
        }
        

    // ----- fields --------------------------------------------------------------------------------

    protected List<Token>        modifiers;
    protected List<Annotation>   annotations;
    protected Token              category;
    protected Token              name;
    protected List<Token>        qualified;
    protected List<Parameter>    typeParams;
    protected List<Parameter>    constructorParams;
    protected List<Composition>  composition;
    protected StatementBlock     body;
    protected Token              doc;
    protected StructureContainer struct;
    }
